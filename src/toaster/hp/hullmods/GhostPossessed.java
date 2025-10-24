package toaster.hp.hullmods;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.combat.EmpArcEntityAPI.EmpArcParams;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import com.fs.starfarer.api.impl.campaign.ids.Stats;
import com.fs.starfarer.api.impl.combat.MoteControlScript;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.combat.ai.system.V;
import org.hyperlib.HyperLibColours;
import org.hyperlib.combat.graphics.HyperspaceTiledSpriteSamplers;
import org.hyperlib.util.HyperLibCollision;
import org.lazywizard.lazylib.combat.CombatUtils;
import org.magiclib.util.MagicRender;
import org.magiclib.util.MagicUI;
import toaster.hp.GhostUtil;
import org.hyperlib.util.HyperLibVector;
import org.hyperlib.util.ScalingFlickerUtil;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.util.vector.Vector2f;
import toaster.hp.campaign.ids.HullMods;
import toaster.hp.campaign.ids.MutableStats;
import toaster.hp.campaign.ids.Tags;
import toaster.hp.campaign.ids.Weapons;
import toaster.hp.combat.ghost.GhostMoteAIScript;

import java.awt.*;
import java.util.*;
import java.util.List;


/**
 * Hullmod that triggers FX on possessed ships.
 */
public class GhostPossessed extends BaseHullMod {
    // --------------------------------
    // ID strings
    // --------------------------------
    public static final String ID = HullMods.GHOST_POSSESSED;  /// Used for storing this script
    public static final String KEY_SHIP_MAP = ID+"_shipMap";
    public static final String WEAPON = Weapons.MOTE_LAUNCHER;

    // --------------------------------
    // Ghost Ship stat multipliers
    // --------------------------------
    public static final float MAX_SPEED_PENALTY = 50f;
    public static final float MANEUVER_PENALTY = 50f;
    public static final float HULL_DAMAGE_TAKEN_PENALTY = 50f;
    public static final float SENSOR_PROFILE_REDUCTION = 50f;

    // --------------------------------
    // Arc visual FX
    // --------------------------------
    public static final float ARC_WIDTH_BASE = 8f;
    public static final float ARC_WIDTH_HULL_SIZE_MULT = 4f;
    public static final float ARC_CORE_WIDTH_MULT = 0.5f;

    // --------------------------------
    // Cloud trail VFX
    // --------------------------------
//    public static final Color GLOW_COLOUR = new Color(
//            FXColours.DEEP_HYPERSPACE_STRIKE.getRed(),
//            FXColours.DEEP_HYPERSPACE_STRIKE.getGreen(),
//            FXColours.DEEP_HYPERSPACE_STRIKE.getBlue(), 16
//    );
    public static final Color CLOUD_OVER_COLOUR = new Color(255, 255, 255, 64);
    public static final float CLOUD_FADE_IN = 0.5f;
    public static final float CLOUD_FULL = 0f;
    public static final float CLOUD_FADE_OUT = 3.5f;

    // --------------------------------
    // Glow trail VFX
    // --------------------------------
    public static final float JITTER_FLICKER_WAIT_MAX = 3.0f;  /// Maximum time between fluctuating jitter peaks
    public static final float CHARGE_JITTER_RADIUS_MULT = 0.1f;  /// The multiplier to the collision radius for the jitter range
    public static final float JITTER_INTENSITY_MIN = 0;
    public static final float JITTER_INTENSITY_MAX = 1.0f;
    public static final Color JITTER_COLOUR = new Color(138, 250, 244, 175);

    // --------------------------------
    // Target arc
    // --------------------------------
    public static final float LOCK_TARGET_LEEWAY = 50f;  /// The radius around the target point to check for ships.
    public static final Color EMP_COLOUR = HyperLibColours.DEEP_HYPERSPACE_STRIKE;
    public static final Map<HullSize, Integer> MOTES_BY_SIZE = Map.of(
            HullSize.FRIGATE, 4,
            HullSize.DESTROYER, 8,
            HullSize.CRUISER, 12,
            HullSize.CAPITAL_SHIP, 16
    );

    public static final float CHARGE_PER_SECOND = 0.05f;
    public static final float CHARGE_MAX = 3f;

    // --------------------------------
    // Variables that are set during initialisation
    // --------------------------------
    protected Vector2f cloudSize;  /// The size of the clouds.
    protected List<WeaponAPI> chargedWeapons;  /// The weapons that use charges.
    protected ScalingFlickerUtil jitterFlicker;  /// Lightning flicker effect for the jitter.
    protected float jitterRange;  /// Pre-calculated jitter max range.
    protected ShipAPI ship;  /// This ship.
    protected boolean spawnMotes = true;  /// Whether the ship should auto-spawn motes.
    protected float arcWidth;  /// The width of the lightning arcs.
    protected float arcExclusionRadius;  /// How far apart arc points should be spaced.
    protected int shipSize;  /// Cache the modified size.

    // --------------------------------
    // Variables that change during run
    // --------------------------------
    protected IntervalUtil cloudInterval = new IntervalUtil(0.05f, 0.1f);  /// FX interval for trailing clouds.

    protected float arcElapsed = 0f;  /// Used to ensure arcs only trigger once per lightning pulse.
    protected float charge = 0f;  /// Charges up when motes are killed, used by systems and defences.
    protected boolean handledDeath = false;  /// Have we caught and handled the death?

    /**
     * Modifies the charge level on the ship.
     *
     * @param amount    How much to modify the value by.
     */
    public void modifyCharge(float amount) {
        this.charge = MathUtils.clamp(
                this.charge + amount, 1f, CHARGE_MAX
        );
    }
    public float getCharge() { return this.charge; }
    public float getNormalisedCharge() { return (this.charge - 1f) / (CHARGE_MAX - 1f); }

    /**
     * Gets the copy of this script for a specific gship.
     *
     * @param ship  The possessed ship to look up.
     * @return  That ship's copy of this script, or null if that ship isn't registered.
     */
    @SuppressWarnings("unused")
    public static GhostPossessed getGhostPossessedScriptFor(ShipAPI ship) {
        if (ship == null) return null;
        return getShipMap().getOrDefault(ship, null);
    }

    /**
     * Gets, or creates, the map storing possessed ship scripts.
     *
     * @return  The hashmap to look up the ship in. Creates if not present.
     */
    @SuppressWarnings("unchecked")
    public static LinkedHashMap<CombatEntityAPI, GhostPossessed> getShipMap() {
        LinkedHashMap<CombatEntityAPI, GhostPossessed> map =
                (LinkedHashMap<CombatEntityAPI, GhostPossessed>) Global.getCombatEngine().getCustomData().get(KEY_SHIP_MAP);
        if (map == null) {
            map = new LinkedHashMap<>();
            Global.getCombatEngine().getCustomData().put(KEY_SHIP_MAP, map);
        }
        return map;
    }

    /**
     * Applies the stat effects to the ghost ship.
     * <p>
     * Eliminates CR and EMP damage, zeroes crew requirements, and makes the hull slower but tougher.
     *
     * @param hullSize  The size of the hull.
     * @param stats     The ship's stat entity.
     * @param id        The ship's ID.
     */
    @Override
    public void applyEffectsBeforeShipCreation(HullSize hullSize, MutableShipStatsAPI stats, String id) {
        super.applyEffectsBeforeShipCreation(hullSize, stats, id);

        // Even if the ship 'keeps' its stats, it still has no crew, no CR loss, and low sensor profile.
        stats.getSensorProfile().modifyMult(id, 1f - SENSOR_PROFILE_REDUCTION * 0.01f);
        stats.getMinCrewMod().modifyMult(id, 0f);
        stats.getCRLossPerSecondPercent().modifyMult(id, 0f);
        stats.getDynamic().getStat(MutableStats.GHOST_MOTE_CAPACITY).setBaseValue(MOTES_BY_SIZE.get(hullSize));

        // Debuffs to speed, more durable e.t.c.
        stats.getMaxSpeed().modifyMult(id, 1f - MAX_SPEED_PENALTY * 0.01f);
        stats.getAcceleration().modifyMult(id, 1f - MANEUVER_PENALTY * 0.01f);
        stats.getDeceleration().modifyMult(id, 1f - MANEUVER_PENALTY * 0.01f);
        stats.getTurnAcceleration().modifyMult(id, 1f - MANEUVER_PENALTY * 0.01f);
        stats.getMaxTurnRate().modifyMult(id, 1f - MANEUVER_PENALTY * 0.01f);

        stats.getHullDamageTakenMult().modifyPercent(id, HULL_DAMAGE_TAKEN_PENALTY * 0.01f);

        // Immune to CR loss, crew loss, EMP, engine & weapon damage, can't vent
        stats.getVentRateMult().modifyMult(id, 0f);
        stats.getEmpDamageTakenMult().modifyMult(id, 0f);
        stats.getEngineDamageTakenMult().modifyMult(id, 0f);
        stats.getWeaponDamageTakenMult().modifyMult(id, 0f);

        // Always breaks, can't be recovered
        stats.getDynamic().getMod(Stats.INDIVIDUAL_SHIP_RECOVERY_MOD).modifyFlat(id, -1000f);
    }

    /**
     * Returns the highlighted entries for hullmod descriptions.
     *
     * @param index     The index of the parameter.
     * @param hullSize  The hull size of the ship.
     * @param ship      The ship that's being described.
     * @return  The description param to be highlighted.
     */
    @Override
    public String getDescriptionParam(int index, HullSize hullSize, ShipAPI ship) {
        if (index == 0) return "" + (int) MAX_SPEED_PENALTY + "%";
        if (index == 1) return "" + (int) MANEUVER_PENALTY + "%";
        if (index == 2) return "cannot vent";
        if (index == 3) return "" + (int) SENSOR_PROFILE_REDUCTION + "%";
        if (index == 4) return "no crew";
        if (index == 5) return "no CR degradation";
        if (index == 6) return "" + (int) HULL_DAMAGE_TAKEN_PENALTY + "%";
        if (index == 7) return "cannot be disabled";
        if (index == 8) return "" + (int) (CHARGE_MAX - 1) * 100 + "%";
        return null;
    }

    /**
     * Applies the ghost ship effects once a ship spawns.
     *
     * @param ship  The ship.
     * @param id    The ship's ID.
     */
    @Override
    public void applyEffectsAfterShipCreation(ShipAPI ship, String id) {
        this.ship = ship;
        this.handledDeath = false;
        ship.setHeavyDHullOverlay();
        ship.setExtraOverlay(Global.getSettings().getSpriteName(ID, "damage"));
        ship.setExtraOverlayMatchHullColor(false);
        ship.setExtraOverlayShadowOpacity(1f);

        Set<String> tags = new HashSet<>();
        tags.addAll(ship.getHullSpec().getTags());
        tags.addAll(ship.getVariant().getTags());

        this.spawnMotes = !tags.contains(Tags.POSSESSED_NO_PASSIVE_MOTES);

        if (!tags.contains(Tags.POSSESSED_KEEP_STATS)) {
            ship.setRenderEngines(false);
            ship.setDoNotRenderVentingAnimation(true);
        } else {
            Global.getLogger(GhostPossessed.class).info(" Ship "+ship+" is keeping its glow and venting.");
        }

        this.cloudSize = new Vector2f(
                32f + 16f * ship.getHullSize().ordinal(),
                32f + 16f * ship.getHullSize().ordinal()
        );
        this.jitterRange = ship.getCollisionRadius() * CHARGE_JITTER_RADIUS_MULT;
        this.jitterFlicker = new ScalingFlickerUtil(JITTER_FLICKER_WAIT_MAX, 1f);
        this.jitterFlicker.newWait();
        this.arcWidth = ARC_WIDTH_BASE + ARC_WIDTH_HULL_SIZE_MULT * ship.getHullSize().ordinal();
        this.arcExclusionRadius = HyperLibCollision.getMinDimension(ship) * 0.9f;
        this.arcElapsed = 0;
        this.shipSize = ship.getHullSize().ordinal() - 1;

        Global.getLogger(GhostPossessed.class).info("Ship: "+ship+", arc exclusion radius: "+arcExclusionRadius);

        this.charge = 1f;
        this.chargedWeapons = GhostUtil.getChargedWeapons(ship);
        for (WeaponAPI weapon : chargedWeapons) {
            AmmoTrackerAPI ammoTracker = weapon.getAmmoTracker();
            ammoTracker.setMaxAmmo(getMaxMotes(ship));
            ammoTracker.setAmmo(0);
        }

        // Smaller explosion as it's already blown up
        ship.setExplosionScale(ship.getExplosionScale() / 2f);
        ship.setExplosionFlashColorOverride(HyperLibColours.DEEP_HYPERSPACE_STRIKE);

        // Get the shared mote data, to ensure it's initialised
        getSharedData(ship);

        // Store this script in the combat engine.
        getShipMap().put(ship, this);
    }

    /**
     * During combat, applies arc effects to the ship.
     *
     * @param ship      The ship.
     * @param amount    The ship's ID.
     */
    @Override
    public void advanceInCombat(ShipAPI ship, float amount) {
        super.advanceInCombat(ship, amount);
        if (ship == null) return;

        MutableShipStatsAPI stats = ship.getMutableStats();
        if (stats == null) return;

        // --------------------------------
        // Check if dead, and clear motes
        // --------------------------------
        if (ship.isHulk()) {
            if (!this.handledDeath){
                SharedGhostMoteAIData data = getSharedData(ship);
                Global.getLogger(GhostPossessed.class).info("Ship "+ship+" has died, killing "+data.motes.size()+" motes");
                this.handledDeath = true;

                for (int i = 0; i < data.motes.size(); i++) {
                    data.motes.get(i).flameOut();
                }
                data.motes.clear();

                // Remove self from the combat engine, though it should clear post-combat anyway.
                getShipMap().remove(ship);
            }
            return;
        }

        // --------------------------------
        // Track increasing charge and bonuses
        // --------------------------------
        this.modifyCharge(amount * CHARGE_PER_SECOND);
        stats.getSystemRegenBonus().modifyMult(ship.getId(), this.charge);
        stats.getEnergyAmmoRegenMult().modifyMult(ship.getId(), this.charge);
        stats.getEffectiveArmorBonus().modifyMult(ship.getId(), this.charge);

        if (Global.getCombatEngine().getPlayerShip() == ship) {
            MagicUI.drawInterfaceStatusBar(
                ship,
                getNormalisedCharge(),
                null,
                null,
                getNormalisedCharge(),
                "Charge",
                3
            );
        }

        // --------------------------------
        // Play jitter FX
        // --------------------------------
        jitterFlicker.advance(amount);
        float jitterMag = MathUtils.clamp(
                Math.max(
                        getNormalisedCharge(),
                        this.jitterFlicker.getBrightness()
                ),
                JITTER_INTENSITY_MIN, JITTER_INTENSITY_MAX
        );
        ship.setJitterUnder(
                ship, JITTER_COLOUR,
                jitterMag,
                10,
                jitterRange * jitterMag
        );
        ship.setCircularJitter(true);

        // --------------------------------
        // Spawn cloud pseudo-contrail
        // --------------------------------
        this.cloudInterval.advance(amount);
        if (this.cloudInterval.intervalElapsed()) {
            int cloudsBelow = MathUtils.getRandomNumberInRange(1, this.shipSize);
            int cloudsAbove = MathUtils.getRandomNumberInRange(1, this.shipSize);

            for (int i = 0; i < cloudsBelow; i++) {
                MagicRender.battlespace(
                        HyperspaceTiledSpriteSamplers.getHyperspaceDarkSprite(),
                        HyperLibCollision.getInternalPoint(ship), new Vector2f(),
                        this.cloudSize, new Vector2f(),
                        MathUtils.getRandomNumberInRange(0f, 360f), MathUtils.getRandomNumberInRange(-15f, 15f),
                        Color.WHITE, false,
                        0f, 0f, 0f, 0f, 0f,
                        CLOUD_FADE_IN, CLOUD_FULL, CLOUD_FADE_OUT,
                        CombatEngineLayers.ABOVE_PLANETS
                );
            }
            for (int i = 0; i < cloudsAbove; i++) {
                MagicRender.battlespace(
                        HyperspaceTiledSpriteSamplers.getHyperspaceSprite(),
                        HyperLibCollision.getInternalPoint(ship), new Vector2f(),
                        this.cloudSize, new Vector2f(),
                        MathUtils.getRandomNumberInRange(0f, 360f), MathUtils.getRandomNumberInRange(-15f, 15f),
                        CLOUD_OVER_COLOUR, false,
                        0f, 0f, 0f, 0f, 0f,
                        CLOUD_FADE_IN, CLOUD_FULL, CLOUD_FADE_OUT,
                        CombatEngineLayers.ABOVE_SHIPS_LAYER
                );
            }
        }

        // --------------------------------
        // Play arc FX across ship
        // --------------------------------
        this.arcElapsed -= amount;  // Cooldown so we only trigger on one peak per burst
        if (this.jitterFlicker.isPeakFrame() && this.arcElapsed < 0f) {
            this.arcElapsed = 1.5f;
            CombatEngineAPI engine = Global.getCombatEngine();

            EmpArcParams arcParams = new EmpArcParams();
            arcParams.glowSizeMult = 0.5f;

            Vector2f pointStart = ship.getLocation();
            Vector2f pointEnd = ship.getLocation();
            int numArcs = Math.max(
                    1,
                    MathUtils.getRandomNumberInRange(
                        1, (int) (this.shipSize * (1+jitterMag))
                    )
            );
            for (int i = 0; i < numArcs; i++) {
                pointStart = HyperLibCollision.getInternalPoint(ship);
                pointEnd = HyperLibCollision.getInternalPointDistantFrom(ship, pointStart, this.arcExclusionRadius);

                EmpArcEntityAPI arc = engine.spawnEmpArcVisual(
                        pointStart, ship, pointEnd, ship,
                        this.arcWidth,
                        HyperLibColours.DEEP_HYPERSPACE_STRIKE, Color.WHITE,
                        arcParams
                );
                arc.setCoreWidthOverride(this.arcWidth * ARC_CORE_WIDTH_MULT);
            }

            // --------------------------------
            // Charge up any charge-based weapons, and also the system battery.
            // --------------------------------
            int motesKilled = getKilledMoteCount(ship);
            if (motesKilled > 0) {
                this.modifyCharge(motesKilled * 0.5f / (float) this.shipSize);

                this.chargedWeapons.forEach(
                        weaponAPI -> weaponAPI.getAmmoTracker().setAmmo(
                                Math.min(
                                        weaponAPI.getAmmoTracker().getMaxAmmo(),
                                        weaponAPI.getAmmoTracker().getAmmo() + motesKilled
                                )
                        )
                );
            }

            // --------------------------------
            // Spawn any extra motes if this ship spawns motes
            // --------------------------------
            if (this.spawnMotes) {
                int spawnBatch = getMoteSpawnBatch(ship);
                EmpArcParams params;
                params = new EmpArcParams();
                params.glowSizeMult = 0.5f;
                params.fadeOutDist = 16f;
                params.movementDurOverride = 0.1f;

                for (int i = 0; i < spawnBatch; i++) {
                    String weaponId = getWeaponId(ship);
                    MissileAPI mote = (MissileAPI) engine.spawnProjectile(
                            ship, null,
                            weaponId,
                            MathUtils.getRandomPointOnLine(pointStart, pointEnd),
                            MathUtils.getRandomNumberInRange(0, 360f),
                            ship.getVelocity()
                    );
                    mote.setWeaponSpec(weaponId);
                    GhostPossessed.associateMote(ship, mote, 0f);
                }
            }
        }

        // --------------------------------
        // Update the shared mote info
        // --------------------------------
        // Other systems may be setting the attractor point
        SharedGhostMoteAIData data = getSharedData(ship);
        data.elapsed += amount;

        if (data.attractorRemaining > 0) {
            data.attractorRemaining -= amount;
            if (data.attractorRemaining <= 0 ||
                    (data.attractorLock != null && !data.attractorLock.isAlive()) ||
                    data.motes.isEmpty()) {
                data.attractorTarget = null;
                data.attractorLock = null;
                data.attractorRemaining = 0;
            }
        }

        // --------------------------------
        // Play the audio loop if enough motes
        // --------------------------------
        float fraction = data.motes.size() / (Math.max(1f, getMaxMotes(ship)));
        float volume = fraction * 3f;
        if (volume > 1f) volume = 1f;
        if (data.motes.size() > 3) {
            Vector2f com = new Vector2f();
            for (MissileAPI mote : data.motes) {
                Vector2f.add(com, mote.getLocation(), com);
            }
            com.scale(1f / data.motes.size());
            Global.getSoundPlayer().playLoop(getLoopSound(ship), ship, 1f, volume, com, new Vector2f());
        }
    }

    // --------------------------------
    // Copied from MoteController
    // --------------------------------
    public static float ANTI_FIGHTER_DAMAGE = 1000f;

    public static class MoteData {
        public Color jitterColor;
        public Color empColor;
        public float antiFighterDamage;
        public String impactSound;
        public String loopSound;
    }

    public static Map<String, MoteData> MOTE_DATA = new HashMap<String, MoteData>();

    static {
        // Trimmed down to just the one entry
        MoteData mote_data = new MoteData();
        mote_data.jitterColor = JITTER_COLOUR;
        mote_data.empColor = HyperLibColours.DEEP_HYPERSPACE_STRIKE;
        mote_data.antiFighterDamage = ANTI_FIGHTER_DAMAGE;
        mote_data.impactSound = "mote_attractor_impact_damage";
        mote_data.loopSound = "mote_attractor_loop_dark";
        MOTE_DATA.put(WEAPON, mote_data);
    }

    public static String getWeaponId(ShipAPI ship) { return WEAPON; }
    public static float getAntiFighterDamage(ShipAPI ship) { return MOTE_DATA.get(getWeaponId(ship)).antiFighterDamage; }
    public static String getImpactSoundId(ShipAPI ship) {
        return MOTE_DATA.get(getWeaponId(ship)).impactSound;
    }
    public static Color getJitterColor(ShipAPI ship) {
        return MOTE_DATA.get(getWeaponId(ship)).jitterColor;
    }
    public static Color getEMPColor(ShipAPI ship) {
        return MOTE_DATA.get(getWeaponId(ship)).empColor;
    }
    public static int getMaxMotes(ShipAPI ship) { return MOTES_BY_SIZE.get(ship.getHullSize()); }
    public static String getLoopSound(ShipAPI ship) { return MOTE_DATA.get(getWeaponId(ship)).loopSound; }

    /**
     *
     */
    public static class SharedGhostMoteAIData extends MoteControlScript.SharedMoteAIData{
        public int maxMotes = 0;
//        public float elapsed = 0f;
//        public List<MissileAPI> motes = new ArrayList<MissileAPI>();

//        public float attractorRemaining = 0f;
//        public Vector2f attractorTarget = null;
//        public ShipAPI attractorLock = null;
//
        public SharedGhostMoteAIData(int maxMotes) {
            this.maxMotes = maxMotes;
        }
    }

    /**
     * Gets the shared AI data object used by all of this ship's motes.
     *
     * @param ship  The ship.
     * @return  The shared AI data object.
     */
    public static SharedGhostMoteAIData getSharedData(ShipAPI ship) {
        String key = ship + "_mote_AI_shared";
        SharedGhostMoteAIData data = (SharedGhostMoteAIData) Global.getCombatEngine().getCustomData().get(key);
        if (data == null) {
            data = new SharedGhostMoteAIData(getMaxMotes(ship));
            Global.getCombatEngine().getCustomData().put(key, data);
        }
        return data;
    }

    /**
     * Gets the number of motes killed since the last check.
     * <p>
     * Clears out inactive motes.
     *
     * @param ship  The ship to query.
     * @return  The number of motes killed.
     */
    public static int getKilledMoteCount(ShipAPI ship) {
        SharedGhostMoteAIData data = getSharedData(ship);
        CombatEngineAPI engine = Global.getCombatEngine();
        int motes_killed = 0;

        if (ship.isHulk()) {
            data.motes.forEach(MissileAPI::flameOut);
            data.motes.clear();
            return 0;
        }
        MissileAPI mote;
        Iterator<MissileAPI> iter = data.motes.iterator();
        while (iter.hasNext()) {
            mote = iter.next();
            if (!engine.isMissileAlive(mote)) {
                iter.remove();
                if (mote.getHitpoints() <= 0 ) motes_killed++;
            }
        }
        return motes_killed;
    }

    /**
     * Gets the unused mote capacity for the ship.
     * <p>
     * Clears out inactive motes.
     *
     * @param ship  The ship to query.
     * @return  The total mote capacity - the active motes.
     */
    public static int getFreeMoteCapacity(ShipAPI ship) {
        if (ship.isHulk()) return 0;

        SharedGhostMoteAIData data = getSharedData(ship);
        return Math.max(getMaxMotes(ship) - data.motes.size(), 0);
    }

    /**
     * Gets a reasonable batch of motes to spawn.
     *
     * @param ship  The ship to calculate for.
     * @return  A reasonable fraction of the total motes.
     */
    protected int getMoteSpawnBatch(ShipAPI ship) {
        int free_capacity = getFreeMoteCapacity(ship);
        if (free_capacity <= 0 ) return 0;

        return Math.min(
                free_capacity,
                MathUtils.getRandomNumberInRange(
                        Math.max(1, getMaxMotes(ship) / 10),
                        Math.max(2, getMaxMotes(ship) / 5)
                )
        );
    }

    /**
     * Adds a mote spawned elsewhere (e.g. by a ship system) to a ship's roster.
     *
     * @param ship          The ship to add the mote to.
     * @param missile       The mote's missile entity.
     * @param timeOverride  How long the mote should ignore flocking for and just seek the ship.
     */
    public static void associateMote(ShipAPI ship, MissileAPI missile, float timeOverride) {
        missile.getActiveLayers().remove(CombatEngineLayers.FF_INDICATORS_LAYER);
        missile.setWeaponSpec(getWeaponId(ship));
        missile.setEmpResistance(10000);
        missile.setMissileAI(
                new GhostMoteAIScript(missile, timeOverride)
        );
        getSharedData(ship).motes.add(missile);
    }
}
