package toaster.hp.weapons.stormcaller;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.combat.EmpArcEntityAPI;
import com.fs.starfarer.api.combat.EmpArcEntityAPI.EmpArcParams;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import org.hyperlib.HyperLibTags;
import org.hyperlib.HyperLibSoundIds;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.VectorUtils;
import org.lazywizard.lazylib.combat.CombatUtils;
import org.lwjgl.util.vector.Vector2f;

import org.hyperlib.HyperLibColours;
import toaster.hp.campaign.ids.Projectiles;

import java.awt.*;
import java.util.Objects;
import java.util.Random;


/**
 * Script for when a Stormcaller 'mine' (i.e. stormcloud) explodes (i.e. lightning bolts).
 */
public class StormcallerMineExplosion implements ProximityExplosionEffect {
//    private static Logger log = Logger.getLogger(StormcallerMineExplosion.class);   /// The logger.

    public static float PARTICLE_VELOCITY_SIZE_FACTOR = 0.15f;
    public static float PARTICLE_VELOCITY_MULT_MIN = 0.5f;
    public static float PARTICLE_VELOCITY_MULT_MAX = 1.0f;
    public static float PARTICLE_FADE_IN = 0.1f;
    public static float PARTICLE_DURATION_MIN = 2f;
    public static float PARTICLE_DURATION_MAX = 3f;
    public static int PARTICLE_COUNT = 8;
    public static float PARTICLE_SIZE_MIN = 64f;
    public static float PARTICLE_SIZE_MAX = 96f;
    public static Color PARTICLE_COLOUR = HyperLibColours.DEEP_HYPERSPACE_QUIET;

    /**
     * Plays the particle visuals and applies knockback
     *
     * @param explosion          The 'explosion' entity created
     * @param originalProjectile The mine that just exploded
     */
    public void onExplosion(
            DamagingProjectileAPI explosion,
            DamagingProjectileAPI originalProjectile
    ) {
        CombatEngineAPI engine = Global.getCombatEngine();

        // ------------------------------
        // Add the explosion visuals
        // ------------------------------
        // Explosion parameters
        Vector2f explosionPoint = explosion.getLocation();
        Vector2f projectileVelocity = originalProjectile.getVelocity();
        float explosionRadiusCore = explosion.getExplosionSpecIfExplosion().getCoreRadius();
        float explosionRadius = explosion.getExplosionSpecIfExplosion().getRadius();
        float explosionRadiusEdge = explosionRadius - explosionRadiusCore;

        // Now spawn particles for the 'dissolving' cloud
        float particleDuration, particleSize;
        for (int i = 0; i < PARTICLE_COUNT; i++) {
            particleDuration = MathUtils.getRandomNumberInRange(PARTICLE_DURATION_MIN, PARTICLE_DURATION_MAX);
            particleSize = MathUtils.getRandomNumberInRange(PARTICLE_SIZE_MIN, PARTICLE_SIZE_MAX);

            Vector2f particlePoint = Misc.getPointAtRadius(explosionPoint, particleSize * 0.5f);
            Vector2f particleVelocity = Misc.getUnitVectorAtDegreeAngle((float) Math.random() * 360f);
            particleVelocity.scale(
                    MathUtils.getRandomNumberInRange(
                            PARTICLE_VELOCITY_MULT_MIN, PARTICLE_VELOCITY_MULT_MAX
                    ) * particleSize * PARTICLE_VELOCITY_SIZE_FACTOR
            );
            // Probably unnecessary, but if the cloud has a major velocity we want to add this to it
            Vector2f.add(projectileVelocity, particleVelocity, particleVelocity);

            engine.addNebulaParticle(
                    particlePoint, particleVelocity, particleSize, 2f,
                    PARTICLE_FADE_IN / particleDuration, 0f, particleDuration, PARTICLE_COLOUR,
                    false
            );
        }

        // ------------------------------
        // Lightning arc happens
        // ------------------------------
        // Iterate over ships caught in the explosion, pushing them away and playing a lightning strike visual.
        WeightedRandomPicker<ShipAPI> picker = new WeightedRandomPicker<>();

        for (ShipAPI ship : CombatUtils.getShipsWithinRange(explosionPoint, explosionRadius)) {
            if (
                    !ship.isFighter() && ship.isTargetable() && !ship.isPhased() &&
                            !ship.hasTag(HyperLibTags.HYPERSPACE_STORM_STRIKE_IMMUNE)
            ) {
                picker.add(ship, ship.getHullSize().ordinal());
            }
            explosion.addDamagedAlready(ship);
        }

        // Set the EMP arc parameters.
        EmpArcParams arcParams = new EmpArcParams();
        arcParams.segmentLengthMult = 2f;
        arcParams.glowSizeMult = 2f;
//            arc_params.flickerRateMult = 0.5f + (float) Math.random() * 0.5f;
//            arc_params.flickerRateMult *= 1.5f;
        arcParams.fadeOutDist = 1000f;
        arcParams.minFadeOutMult = 4f;
        arcParams.movementDurOverride = 0.5f;

        // Pick the target and zap them
        ShipAPI ship = picker.pick();

        if (ship != null) {
            // --------------------------------
            // Found a ship to hit
            // --------------------------------
            // Adjust the force by how close the ship was to the zap
            Vector2f shipPoint = ship.getLocation();
            float ship_distance = Misc.getDistance(shipPoint, explosionPoint);
            float explosionForceScaling;

            if (ship_distance >= explosionRadius) {
                // Half if they're outside the range
                explosionForceScaling = 0.5f;
            } else if (ship_distance <= explosionRadiusCore) {
                // Full if they're inside the blast
                explosionForceScaling = 1.0f;
            } else {
                // Scale smoothly between
                explosionForceScaling = 1.0f - ((ship_distance - explosionRadiusCore) / explosionRadiusEdge) / 2f;
            }

            // Now push the ship away from the strike
            CombatUtils.applyForce(
                    ship,
                    VectorUtils.getDirectionalVector(explosionPoint, ship.getLocation()),
                    StormcallerParams.STRIKE_IMPULSE * explosionForceScaling
            );

            // And make an EMP arc to it
            EmpArcEntityAPI arc = engine.spawnEmpArc(
                    originalProjectile.getSource(),
                    new Vector2f(
                            (shipPoint.x + explosionPoint.x * 2f) / 3f,
                            (shipPoint.y + explosionPoint.y * 2f) / 3f
                    ),
                    explosion,
                    ship,
                    originalProjectile.getDamageType(),
                    originalProjectile.getDamageAmount(),
                    originalProjectile.getEmpAmount(),
                    100000f,  // If they were hit by the blast, they're in range!
                    HyperLibSoundIds.HYPERSPACE_LIGHTNING,
                    StormcallerParams.STRIKE_ARC_WIDTH,
                    StormcallerParams.STRIKE_COLOUR,
                    Color.white,
                    arcParams
            );
            arc.setSingleFlickerMode(true);
            arc.setFadedOutAtStart(true);

        } else {
            // --------------------------------
            // No ship to hit; hit another cloud
            // --------------------------------
            WeightedRandomPicker<MissileAPI> minePicker = new WeightedRandomPicker<>();
            for (
                    MissileAPI mine : CombatUtils.getMissilesWithinRange(
                    explosionPoint, explosionRadius * StormcallerParams.STRIKE_TO_CLOUD_RADIUS_MULT
            )
            ) {
                if (Objects.equals(mine.getProjectileSpecId(), Projectiles.STORMCALLER_MINELAYER)) minePicker.add(mine);
            }

            MissileAPI arcTargetMissile = minePicker.pick();
            if (arcTargetMissile == null) arcTargetMissile = (MissileAPI) originalProjectile;

            EmpArcEntityAPI arc = engine.spawnEmpArcVisual(
                    Misc.getPointWithinRadiusUniform(
                            explosionPoint, explosionRadiusCore * 0.66f, new Random()
                    ),
                    arcTargetMissile,
                    Misc.getPointWithinRadiusUniform(
                            arcTargetMissile.getLocation(), explosionRadiusCore * 0.66f, new Random()
                    ),
                    explosion,
                    StormcallerParams.STRIKE_ARC_WIDTH,
                    StormcallerParams.STRIKE_COLOUR,
                    Color.white,
                    arcParams
            );
            arc.setFadedOutAtStart(true);
            Global.getSoundPlayer().playSound(
                    HyperLibSoundIds.HYPERSPACE_LIGHTNING, 1f, 1f, explosionPoint, new Vector2f()
            );
        }
    }
}
