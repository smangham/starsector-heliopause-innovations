package toaster.hp.shipsystems;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.impl.campaign.ids.Stats;
import org.hyperlib.util.HyperLibVector;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.util.vector.Vector2f;
import org.magiclib.util.MagicRender;
import toaster.hp.campaign.ids.HullMods;
import toaster.hp.campaign.ids.ShipSystems;
import toaster.hp.hullmods.GhostPossessed;

import java.awt.*;


/**
 * Modification of the attractor field that also phases the ship out for the duration.
 */
@SuppressWarnings("unused")
public class AttractorFieldMk1 extends AttractorField {
    // Static values for visuals
    public static final Color GLOW_WHIRL_CORE_COLOUR = new Color(138, 250, 244, 128);
    public static final float GLOW_WHIRL_CORE_RADIUS = 1024f;
    public static final float GLOW_WHIRL_CORE_TURN_RATE = -30f;

    // Save local copies of the variables that persist over loops
    protected boolean isBoss = false;
    /// Whether this is the NPC Boss Ship
    protected boolean awardedAmmoBoss = false;  /// Whether we've done the bonus ammo top-up

    /**
     * Init that tags if the ship is a 'boss ship'.
     *
     * @param ship The ship.
     */
    @Override
    protected void init(ShipAPI ship) {
        super.init(ship);
        this.isBoss = ship.getVariant().getHullMods().contains(HullMods.GHOST_POSSESSED);
    }

    /**
     * Adjust VFX to add an extra glow if this is the boss ship.
     */
    @Override
    protected void playStateInVFX() {
        super.playStateInVFX();
        if (!this.isBoss) return;

        MagicRender.objectspace(
                Global.getSettings().getSprite(ShipSystems.ATTRACTOR_FIELD, GLOW_WHIRL_SPRITE_KEY),
                this.ship,
                getShieldOffset(this.ship), new Vector2f(),
                new Vector2f(GLOW_WHIRL_CORE_RADIUS, GLOW_WHIRL_CORE_RADIUS), new Vector2f(),
                0.0f, GLOW_WHIRL_CORE_TURN_RATE, true,
                GLOW_WHIRL_CORE_COLOUR, false, 0f, 0f,
                0f, 0f, 0f,
                glowWhirlFadeIn, glowWhirlFull, glowWhirlFadeOut, true,
                CombatEngineLayers.BELOW_SHIPS_LAYER
        );
    }

    /**
     * Adjusts the pulsed VFX to spawn in some motes if there's space.
     */
    @Override
    protected void playPulseVFX() {
        super.playPulseVFX();
        if (!this.isBoss) return;

        // --------------------------------
        // If this is the NPC boss and has space for new motes
        // --------------------------------
        CombatEngineAPI engine = Global.getCombatEngine();
        if (GhostPossessed.getFreeMoteCapacity(this.ship) <= 0) return;

        int num_particles = MathUtils.getRandomNumberInRange(2, 4);
        float particle_angle, particle_velocity_angle;
        Vector2f particle_location = new Vector2f();
        Vector2f ship_location = ship.getLocation();
        String weaponId = GhostPossessed.getWeaponId(this.ship);

        for (int i = 0; i < num_particles; i++) {
            particle_angle = MathUtils.getRandomNumberInRange(0f, 360f);
            particle_velocity_angle = getParticleVelocityAngle(particle_angle);

            Vector2f.add(
                    HyperLibVector.getVectorForAngle(
                            particle_angle, getParticleSpawnRadius()
                    ),
                    ship_location,
                    particle_location
            );
            MissileAPI mote = (MissileAPI) engine.spawnProjectile(
                    ship, null,
                    weaponId,
                    particle_location, particle_velocity_angle, null
            );
            mote.setWeaponSpec(weaponId);
            mote.getVelocity().set(getParticleVelocity(particle_velocity_angle));
            GhostPossessed.associateMote(
                    ship, mote,
                    ((AttractorFieldMk1) ship.getSystem().getScript()).getDurationRemaining() + 1f
            );
        }
    }

    /**
     * Called every frame to apply the system effects.
     * <p>
     * Just calls the super apply, then
     *
     * @param stats       The stats of the ship this system is installed on.
     * @param id          ???
     * @param state       Whether the system is charging up (IN), down (OUT) or fully active (ACTIVE).
     * @param effectLevel The normalised effect level; scales from 0-1 over charge-up/charge-down time.
     */
    @Override
    public void apply(MutableShipStatsAPI stats, String id, State state, float effectLevel) {
        if (state != this.lastState && state == State.IN) this.awardedAmmoBoss = false;
        super.apply(stats, id, state, effectLevel);

        ShipAPI ship = null;
        boolean player = false;
        if (stats.getEntity() instanceof ShipAPI) {
            ship = (ShipAPI) stats.getEntity();
            player = ship == Global.getCombatEngine().getPlayerShip();
            id = id + "_" + ship.getId();
        } else {
            return;
        }

//        if (player) {
//            maintainStatus(ship, state, effectLevel);
//        }

        if (Global.getCombatEngine().isPaused()) return;

        ShipSystemAPI cloak = ship.getPhaseCloak();
        if (cloak == null) cloak = ship.getSystem();
        if (cloak == null) return;

        if (FLUX_LEVEL_AFFECTS_SPEED) {
            if (state == State.ACTIVE || state == State.OUT || state == State.IN) {
                float mult = getSpeedMult(ship, effectLevel);
                if (mult < 1f) {
                    stats.getMaxSpeed().modifyMult(id + "_2", mult);
                } else {
                    stats.getMaxSpeed().unmodifyMult(id + "_2");
                }
                ((PhaseCloakSystemAPI) cloak).setMinCoilJitterLevel(getDisruptionLevel(ship));
            }
        }

        if (state == State.COOLDOWN || state == State.IDLE) {
            unapply(stats, id);
            return;
        }

        float speedPercentMod = stats.getDynamic().getMod(Stats.PHASE_CLOAK_SPEED_MOD).computeEffective(0f);
        float accelPercentMod = stats.getDynamic().getMod(Stats.PHASE_CLOAK_ACCEL_MOD).computeEffective(0f);
        stats.getMaxSpeed().modifyPercent(id, speedPercentMod * effectLevel);
        stats.getAcceleration().modifyPercent(id, accelPercentMod * effectLevel);
        stats.getDeceleration().modifyPercent(id, accelPercentMod * effectLevel);

        float speedMultMod = stats.getDynamic().getMod(Stats.PHASE_CLOAK_SPEED_MOD).getMult();
        float accelMultMod = stats.getDynamic().getMod(Stats.PHASE_CLOAK_ACCEL_MOD).getMult();
        stats.getMaxSpeed().modifyMult(id, speedMultMod * effectLevel);
        stats.getAcceleration().modifyMult(id, accelMultMod * effectLevel);
        stats.getDeceleration().modifyMult(id, accelMultMod * effectLevel);

        float level = effectLevel;
        float levelForAlpha = level;

        if (state == State.IN || state == State.ACTIVE) {
            ship.setPhased(true);
            levelForAlpha = level;
        } else if (state == State.OUT) {
            if (level > 0.5f) {
                ship.setPhased(true);
            } else {
                ship.setPhased(false);
            }
            levelForAlpha = level;
        }

        ship.setExtraAlphaMult(1f - (1f - SHIP_ALPHA_MULT) * levelForAlpha);
        ship.setApplyExtraAlphaToEngines(true);

        float extra = 0f;
        float shipTimeMult = 1f + (getMaxTimeMult(stats) - 1f) * levelForAlpha * (1f - extra);
        stats.getTimeMult().modifyMult(id, shipTimeMult);
        if (player) {
            Global.getCombatEngine().getTimeMult().modifyMult(id, 1f / shipTimeMult);
        } else {
            Global.getCombatEngine().getTimeMult().unmodify(id);
        }
    }

    /**
     * Called when the system finishes running.
     * <p>
     * Charges up the built-in weapon based on the damage taken,
     * ensuring that if they're the boss they fill at least half their ammo.
     *
     * @param stats The stats API for the ship the system is installed on.
     * @param id    The ID of the ship?
     */
    @Override
    public void unapply(MutableShipStatsAPI stats, String id) {
        super.unapply(stats, id);
        if (this.isBoss && !this.awardedAmmoBoss) {
            this.awardedAmmoBoss = true;
            this.weapon.getAmmoTracker().setAmmo(
                    Math.max(
                            (int) (this.weapon.getAmmoTracker().getMaxAmmo() / 2),
                            this.weapon.getAmmoTracker().getAmmo()
                    )
            );
        }

        // --------------------------------
        // Copied from PhaseCloakStats
        // --------------------------------
        ShipAPI ship = null;
        if (stats.getEntity() instanceof ShipAPI) {
            ship = (ShipAPI) stats.getEntity();
        } else {
            return;
        }

        Global.getCombatEngine().getTimeMult().unmodify(id);
        stats.getTimeMult().unmodify(id);

        stats.getMaxSpeed().unmodify(id);
        stats.getMaxSpeed().unmodifyMult(id + "_2");
        stats.getAcceleration().unmodify(id);
        stats.getDeceleration().unmodify(id);

        ShipSystemAPI cloak = ship.getPhaseCloak();
        if (cloak == null) cloak = ship.getSystem();
        if (cloak != null) {
            ((PhaseCloakSystemAPI) cloak).setMinCoilJitterLevel(0f);
        }

        ship.setPhased(false);
        ship.setExtraAlphaMult(1f);
    }

    // --------------------------------
    // Copied from PhaseCloakStats
    // --------------------------------
    public static Color JITTER_COLOR = new Color(255, 175, 255, 255);
    public static float JITTER_FADE_TIME = 0.5f;

    public static float SHIP_ALPHA_MULT = 0.25f;
    public static float VULNERABLE_FRACTION = 0f;
    public static float INCOMING_DAMAGE_MULT = 0.25f;

    public static float MAX_TIME_MULT = 3f;
    public static boolean FLUX_LEVEL_AFFECTS_SPEED = true;
    public static float MIN_SPEED_MULT = 0.33f;
    public static float BASE_FLUX_LEVEL_FOR_MIN_SPEED = 0.5f;

    public static float getMaxTimeMult(MutableShipStatsAPI stats) {
        return 1f + (MAX_TIME_MULT - 1f) * stats.getDynamic().getValue(Stats.PHASE_TIME_BONUS_MULT);
    }

    protected float getDisruptionLevel(ShipAPI ship) {
        if (FLUX_LEVEL_AFFECTS_SPEED) {
            float threshold = ship.getMutableStats().getDynamic().getMod(
                    Stats.PHASE_CLOAK_FLUX_LEVEL_FOR_MIN_SPEED_MOD).computeEffective(BASE_FLUX_LEVEL_FOR_MIN_SPEED);
            if (threshold <= 0) return 1f;
            float level = ship.getHardFluxLevel() / threshold;
            if (level > 1f) level = 1f;
            return level;
        }
        return 0f;
    }

//    protected void maintainStatus(ShipAPI playerShip, State state, float effectLevel) {
//        float level = effectLevel;
//        float f = VULNERABLE_FRACTION;
//
//        ShipSystemAPI cloak = playerShip.getPhaseCloak();
//        if (cloak == null) cloak = playerShip.getSystem();
//        if (cloak == null) return;
//
//        if (level > f) {
//            Global.getCombatEngine().maintainStatusForPlayerShip(STATUSKEY2,
//                    cloak.getSpecAPI().getIconSpriteName(), cloak.getDisplayName(), "time flow altered", false);
//        }
//
//        if (FLUX_LEVEL_AFFECTS_SPEED) {
//            if (level > f) {
//                if (getDisruptionLevel(playerShip) <= 0f) {
//                    Global.getCombatEngine().maintainStatusForPlayerShip(STATUSKEY3,
//                            cloak.getSpecAPI().getIconSpriteName(), "phase coils stable", "top speed at 100%", false);
//                } else {
//                    String speedPercentStr = (int) Math.round(getSpeedMult(playerShip, effectLevel) * 100f) + "%";
//                    Global.getCombatEngine().maintainStatusForPlayerShip(STATUSKEY3,
//                            cloak.getSpecAPI().getIconSpriteName(),
//                            "phase coil stress",
//                            "top speed at " + speedPercentStr, true);
//                }
//            }
//        }
//    }

    /**
     * @param ship        The ship.
     * @param effectLevel The system's effect level.
     * @return The multiplier arising from the ship's disruption.
     */
    public float getSpeedMult(ShipAPI ship, float effectLevel) {
        if (getDisruptionLevel(ship) <= 0f) return 1f;
        return MIN_SPEED_MULT + (1f - MIN_SPEED_MULT) * (1f - getDisruptionLevel(ship) * effectLevel);
    }

    /**
     * Tweak that only shows status when the system is running.
     *
     * @param index       ???
     * @param state       Whether the system is charging up (State.IN),
     *                    down (State.OUT) or fully active (State.ACTIVE).
     * @param effectLevel The normalised effect level; scales from 0-1 over charge-up/charge-down time.
     * @return The displayed status data.
     */
    @Override
    public StatusData getStatusData(int index, State state, float effectLevel) {
        if (state == State.IDLE || state == State.COOLDOWN) return null;
        return super.getStatusData(index, state, effectLevel);
    }
}
