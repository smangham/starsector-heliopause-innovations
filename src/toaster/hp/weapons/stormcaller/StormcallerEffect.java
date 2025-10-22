package toaster.hp.weapons.stormcaller;

import java.util.ArrayList;
import java.util.List;

import java.awt.Color;
import java.util.Random;

import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.combat.listeners.ApplyDamageResultAPI;
import com.fs.starfarer.api.impl.combat.dweller.DwellerShroud;
import org.hyperlib.combat.HyperspaceStormRenderingPlugin;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.util.vector.Vector2f;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.EmpArcEntityAPI.EmpArcParams;
import com.fs.starfarer.api.combat.WeaponAPI.WeaponType;
import com.fs.starfarer.api.impl.combat.dweller.DwellerShroud.DwellerShroudParams;
import com.fs.starfarer.api.util.Misc;
import org.hyperlib.FXColours;
import toaster.hp.campaign.ids.HullMods;
import toaster.hp.campaign.ids.Weapons;
import org.hyperlib.combat.sound.HyperspaceStormSoundLoop;


/**
 * Copy of RiftLightningEffect
 * <p>
 * Multiple instances of this plugin - one for every projectile (on hit), and one for each weapon.
 * The goal is for the on-hit effect to fire off a lightning arc in case of a hit, and for the onfire/every frame copy
 * of the plugin to fire off a lightning arc in case there is a miss.
 * <p>
 * Modified to spawn a mine.
 *
 * @author Alex originally
 * @author Toaster modified
 */
@SuppressWarnings("unused")
public class StormcallerEffect implements OnHitEffectPlugin, OnFireEffectPlugin, EveryFrameWeaponEffectPlugin {
    public static final float RIFT_LIGHTNING_SPEED = 10000f;
    public static final String RIFT_LIGHTNING_DAMAGE_REMOVER = Weapons.STORMCALLER+"_damage_remover";
    public static final String RIFT_LIGHTNING_FIRED_TAG = Weapons.STORMCALLER+"_fired_tag";
    public static final String RIFT_LIGHTNING_SOURCE_WEAPON = Weapons.STORMCALLER+"_source_weapon";
    public static final Color RIFT_LIGHTNING_COLOR = FXColours.DEEP_HYPERSPACE_STORMY;

    public static class FiredLightningProjectile {
        public DamagingProjectileAPI projectile;
    }
    protected List<FiredLightningProjectile> fired = new ArrayList<>();

    /**
     * Manages the list of fired projectiles.
     * <p>
     * Runs every frame on the *weapon*. Removes any fired projectiles, fires into space if they didn't hit.
     *
     * @param amount    How much time has elapsed since last frame.
     * @param engine    The combat engine.
     * @param weapon    The weapon.
     */
    @Override
    public void advance(float amount, CombatEngineAPI engine, WeaponAPI weapon) {
        if (engine != null && engine.isInFastTimeAdvance()) return;

        List<FiredLightningProjectile> remove = new ArrayList<>();

        float maxRange = weapon.getRange();
        for (FiredLightningProjectile data : fired) {
            float dist = Misc.getDistance(data.projectile.getSpawnLocation(), data.projectile.getLocation());
            boolean firedAlready = data.projectile.getCustomData().containsKey(RIFT_LIGHTNING_FIRED_TAG);
            if (dist > maxRange || firedAlready) {
                remove.add(data);
                if (!firedAlready) {
                    fireArc(data.projectile, weapon, null, null);
                }
            }
        }
        fired.removeAll(remove);
    }

    /**
     * Triggered when fired.
     * <p>
     * Runs on the *weapon*. Tags the projectile with the source weapon, so it can link back on hit.
     *
     * @param projectile    The projectile fired.
     * @param weapon        The weapon that fired it.
     * @param engine        The combat engine.
     */
    @Override
    public void onFire(DamagingProjectileAPI projectile, WeaponAPI weapon, CombatEngineAPI engine) {
        projectile.getDamage().getModifier().modifyMult(RIFT_LIGHTNING_DAMAGE_REMOVER, 0f);
        projectile.setCustomData(RIFT_LIGHTNING_SOURCE_WEAPON, weapon);

        FiredLightningProjectile data = new FiredLightningProjectile();
        data.projectile = projectile;
        fired.add(data);
    }

    /**
     * Triggers when a projectile hits a target.
     * <p>
     * Fetches the source weapon using tags.
     * This needs to be overridden as you can't override static methods,
     * so without this it calls the RiftLightning onHit.
     *
     * @param projectile    The projectile that hit.
     * @param target        The entity struck, if any.
     * @param point         The point the projectile struck.
     * @param shieldHit     Did it hit a shield?
     * @param damageResult  The API for dealing damage to the target.
     * @param engine        The combat engine.
     */
    @Override
    public void onHit(DamagingProjectileAPI projectile, CombatEntityAPI target,
                      Vector2f point, boolean shieldHit, ApplyDamageResultAPI damageResult, CombatEngineAPI engine) {

        WeaponAPI weapon = (WeaponAPI) projectile.getCustomData().get(RIFT_LIGHTNING_SOURCE_WEAPON);
        if (weapon == null) return;
        fireArc(projectile, weapon, point, target);
    }

    /**
     * Triggered when the projectile strikes a target, to play an arc.
     * <p>
     * Split from onHit as it's static.
     *
     * @param projectile    The projectile fired.
     * @param weapon        The weapon that fired it, if any.
     * @param point         The point the projectile struck.
     * @param target        The entity struck, if any.
     */
	public static void fireArc(
            DamagingProjectileAPI projectile, WeaponAPI weapon, Vector2f point, CombatEntityAPI target
    ) {
		boolean firedAlready = projectile.getCustomData().containsKey(RIFT_LIGHTNING_FIRED_TAG);
		if (firedAlready) return;
		
		projectile.setCustomData(RIFT_LIGHTNING_FIRED_TAG, true);
		
		CombatEngineAPI engine = Global.getCombatEngine();
		
		ShipAPI ship = weapon.getShip();
		if (ship == null) return;

		Vector2f from = projectile.getSpawnLocation();

		float dist = Float.MAX_VALUE;
		if (point != null) dist = Misc.getDistance(from, point);
		
		float maxRange = weapon.getRange();
		if (dist > maxRange || point == null) {
			dist = maxRange * (0.85f + 0.15f * (float) Math.random());
			if (projectile.didDamage()) {
				dist = maxRange;
			}
			point = Misc.getUnitVectorAtDegreeAngle(projectile.getFacing());
			point.scale(dist);
			Vector2f.add(point, from, point);
		}
		
		float arcSpeed = RIFT_LIGHTNING_SPEED;
		
		DwellerShroud shroud = DwellerShroud.getShroudFor(ship);
//        if (shroud != null) {
//			float angle = Misc.getAngleInDegrees(ship.getLocation(), point);
//			from = Misc.getUnitVectorAtDegreeAngle(angle + 90f - 180f * (float) Math.random());
//			from.scale((0.5f + (float) Math.random() * 0.25f) * shroud.getShroudParams().maxOffset);
//			Vector2f.add(ship.getLocation(), from, from);
//		}

        if (ship.getVariant().getHullMods().contains(HullMods.GHOST_POSSESSED) || ship.getHullSpec().isBuiltInMod(HullMods.GHOST_POSSESSED)) {
            from = Misc.getPointWithinRadius(ship.getLocation(), ship.getCollisionRadius()/4f);
            Vector2f.add(ship.getLocation(), from, from);
        }

        EmpArcParams params = new EmpArcParams();
		params.segmentLengthMult = 8f;
		params.zigZagReductionFactor = 0.15f;
		params.fadeOutDist = 50f;
		params.minFadeOutMult = 10f;
//		params.flickerRateMult = 0.7f;
		params.flickerRateMult = 0.3f;
//		params.flickerRateMult = 0.05f;
//		params.glowSizeMult = 3f;
//		params.brightSpotFullFraction = 0.5f;
		params.movementDurOverride = Math.max(0.05f, dist / arcSpeed);

		EmpArcEntityAPI arc = (EmpArcEntityAPI) engine.spawnEmpArcVisual(
                from, ship, point, null,
				80f, // thickness
				RIFT_LIGHTNING_COLOR,
				new Color(255,255,255,255),
				params
        );
		arc.setCoreWidthOverride(40f);
		arc.setRenderGlowAtStart(false);
		arc.setFadedOutAtStart(true);
		arc.setSingleFlickerMode(true);

        // --------------------------------
        // Spawn mines to produce cloud
        // --------------------------------
        // Play sound


//        int mine_spawn_tries;
        int ammo_count = weapon.getAmmoTracker().getAmmo();
        float mine_spawn_exclusion_radius = StormcallerParams.EXPLOSION_CORE_RADIUS * StormcallerParams.MINE_EXCLUSION_RADIUS_MULT;

        float mine_spawn_radius = (float) Math.sqrt(ammo_count) * StormcallerParams.MINE_SPAWN_RADIUS_PER_ROOT_CHARGE * mine_spawn_exclusion_radius;
        boolean mine_location_valid;
        Vector2f mine_location = new Vector2f(point);
        ArrayList<MissileAPI> mines = new ArrayList<>();
        Random random = new Random();

//        log.info(
//                "Mine_spawn_radius: "+ mine_spawn_radius+", from ammo count: "+ammo_count+", radius: "+EXPLOSION_CORE_RADIUS+", exclusion radius: "+mine_spawn_exclusion_radius
//        );
        mines.add(
                spawnMineCloud(
                        ship, mine_location,
                        params.movementDurOverride
                )
        );
        // Also, pin the sound to this first mine. Doesn't matter if it finishes early, as the lightning will cover it.
        engine.addPlugin(new HyperspaceStormSoundLoop(mines.get(0), 8f));

        // Place the N mines
        for (int i=1; i < ammo_count; i++) {
//            mine_spawn_tries = 0;

            // For each, try to find a valid location that's not too close to another
            mine_location_valid = true;
            for (int j = 0; j < StormcallerParams.MINE_SPAWN_TRIES; j++) {
                mine_location = Misc.getPointWithinRadiusUniform(point, mine_spawn_radius, random);
//                mine_spawn_tries++;
                // Is this location too close to another mine?
                for (MissileAPI mine : mines) {
                    if (Misc.getDistance(mine_location, mine.getLocation()) < mine_spawn_exclusion_radius) {
                        mine_location_valid = false;
                        break;
                    }
                }
                if (mine_location_valid) break;  // We found a location, stop looking.
            }
            if (mine_location_valid) {
                mines.add(
                        spawnMineCloud(
                                ship,
                                mine_location,
                                minePlacementDelay(
                                        point, mine_location, params.movementDurOverride,
                                        StormcallerParams.MINE_SPAWN_SPREAD_SPEED_RADIUS_MULT * StormcallerParams.EXPLOSION_RADIUS
                                )
                        )
                );
//                log.info("Placed mine "+i+" after "+mine_spawn_tries+" tries");
//            } else {
//                log.info("Failed to place mine "+i+" after "+mine_spawn_tries+" tries");
            }
        }
        weapon.getAmmoTracker().setAmmo(0);
		
		if (shroud != null) {
			DwellerShroudParams shroudParams = shroud.getShroudParams();
			params = new EmpArcParams();
			params.segmentLengthMult = 4f;
			params.glowSizeMult = 4f;
			params.flickerRateMult = 0.5f + (float) Math.random() * 0.5f;
			params.flickerRateMult *= 1.5f;

			float thickness = shroudParams.overloadArcThickness;
			
			float angle = Misc.getAngleInDegrees(from, ship.getLocation());
			angle = angle + 90f * ((float) Math.random() - 0.5f);
			Vector2f dir = Misc.getUnitVectorAtDegreeAngle(angle);
			dist = shroudParams.maxOffset;
			dist = dist * 0.5f + dist * 0.5f * (float) Math.random();
			//dist *= 1.5f;
			dist *= 0.5f;
			dir.scale(dist);
			Vector2f to = Vector2f.add(from, dir, new Vector2f());
			
			arc = (EmpArcEntityAPI) engine.spawnEmpArcVisual(
					from, ship, to, ship, thickness, RIFT_LIGHTNING_COLOR, Color.white, params
            );
			
			arc.setCoreWidthOverride(shroudParams.overloadArcCoreThickness);
			arc.setSingleFlickerMode(false);
		}
	}

    /**
     * Figures out how long to delay the appearance of a cloud.
     *
     * @param projectile_location   The location the projectile 'hit'
     * @param mine_location         The location the mine will be placed.
     * @param delay_base            The 'base' delay (from projectile travel time).
     * @param spread_speed          The speed at which cloud spawns spread out from the central point.
     * @return                      How long it should take for the cloud to fade in.
     */
    public static float minePlacementDelay(
            Vector2f projectile_location, Vector2f mine_location, float delay_base, float spread_speed
    ) {
        return delay_base + Misc.getDistance(projectile_location, mine_location) / spread_speed;
    }

    /**
     * Spawns a mine/stormcloud.
     *
     * @param source        The ship that fired this weapon.
     * @param mineLocation  The location the mine should be placed.
     * @param delay         The visual delay until the weapon arc finishes.
     * @return              The mine spawned.
     */
	public static MissileAPI spawnMineCloud(
            ShipAPI source, Vector2f mineLocation, float delay
    ) {
		CombatEngineAPI engine = Global.getCombatEngine();
        MissileAPI mine = (MissileAPI) engine.spawnProjectile(
                source, null,
                Weapons.STORMCALLER_MINELAYER,
                mineLocation,
                (float) Math.random() * 360f,
                null
        );

        // Possssibly not needed? Does the arc application apply damage buffs?
        if (source != null) {
            Global.getCombatEngine().applyDamageModifiersToSpawnedProjectileWithNullWeapon(
                    source, WeaponType.ENERGY, false, mine.getDamage());
        };

        // Set the mine to only live for a fraction of the full life, not blow up other mines, not move.
        float mine_lifetime = MathUtils.getRandomNumberInRange(0.5f, 1f) * mine.getMaxFlightTime();
        mine.getVelocity().scale(0);
        mine.setNoMineFFConcerns(true);
        mine.setMaxFlightTime(mine_lifetime);

        // Create a stormcloud that waits at most 2/3 of the lifetime before flashing
        HyperspaceStormRenderingPlugin stormRenderingPlugin = new HyperspaceStormRenderingPlugin(
                mine,
                StormcallerParams.CLOUD_SPRITE_SIZE,
                mine_lifetime * 0.66f,
                StormcallerParams.CLOUD_FLASH_WAIT_MULT,
                delay,
                StormcallerParams.CLOUD_FADE_OUT,
                mine_lifetime - 0.25f
        );
        engine.addLayeredRenderingPlugin(stormRenderingPlugin);
        return mine;
	}
}
