package toaster.hp.shipsystems;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.impl.combat.BaseShipSystemScript;
import com.fs.starfarer.api.loading.ProjectileSpecAPI;
import com.fs.starfarer.api.util.Misc;
import org.apache.log4j.Logger;
import org.lazywizard.lazylib.FastTrig;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.VectorUtils;
import org.lazywizard.lazylib.combat.CombatUtils;
import org.lwjgl.util.vector.Vector2f;
import org.magiclib.util.MagicRender;
import org.hyperlib.FXColours;
import toaster.hp.campaign.ids.ShipSystems;
import toaster.hp.campaign.ids.Weapons;

import java.awt.*;


/**
 * System that attracts projectiles towards the ship on activation.
 */
public class AttractorField extends BaseShipSystemScript {
    // --------------------------------
    // Static values for effect
    // --------------------------------
    public static float DAMAGE_PER_CHARGE = 1000f;  /// How much damage it takes to charge
    public static final float PULSE_INTERVAL_SECONDS = 0.05f;  /// Time between redirections when system is active.
    public static final float PULSE_RANGE = 1600f;  /// Maximum range at which projectiles are affected.
    public static final float ATTRACTOR_TURN_RATE = 120.0f;  /// Maximum projectile turn rate, in degrees per second.
    public static final float ATTRACTOR_SLOW_RATE = 0.67f;  /// What fraction of projectile speed the attractor reduces per second.

    // --------------------------------
    // Static values for visuals
    // --------------------------------
    // - Visuals for the whirly glow sprites
    public static final String GLOW_WHIRL_SPRITE_KEY = "glow_whirl";
    public static final Color GLOW_WHIRL_INNER_COLOUR = FXColours.DEEP_HYPERSPACE_STORMY;
    public static final float GLOW_WHIRL_INNER_TURN_RATE = -20.0f;
    public static final float GLOW_WHIRL_INNER_RADIUS = 1200f;
    public static final Color GLOW_WHIRL_OUTER_COLOUR = FXColours.DEEP_HYPERSPACE_QUIET;
    public static final float GLOW_WHIRL_OUTER_TURN_RATE = -10.0f;
    public static final float GLOW_WHIRL_OUTER_RADIUS = 1600f;

    // - Visuals for the glow on projectiles
    public static final Color GLOW_PROJECTILE_COLOUR = FXColours.DEEP_HYPERSPACE_STORMY;
    public static final float GLOW_PROJECTILE_SIZE_MULT = 1.0f;

    // - Visuals for the glowy particles
    public static final Vector2f GLOW_PARTICLE_SIZE = new Vector2f(32f, 32f);
    public static final String GLOW_PARTICLE_SPRITE_KEY = "glow_particle";
    public static final float GLOW_PARTICLE_RADIUS_RANGE = 32f;
    public static final float GLOW_PARTICLE_ANGLE_IN_MIN = 15f;
    public static final float GLOW_PARTICLE_ANGLE_IN_MAX = 30f;
    public static final float GLOW_PARTICLE_VELOCITY_MIN = 350;
    public static final float GLOW_PARTICLE_VELOCITY_MAX = 400f;
    public static final float GLOW_PARTICLE_FADE_IN = 0.35f;
    public static final float GLOW_PARTICLE_FADE_OUT = 0.15f;

    // --------------------------------
    // Save local copies of the variables that persist over loops
    // --------------------------------
    protected boolean inited = false;  /// If the system has been initialised this combat.
    protected ShipAPI ship = null;  /// This ship.
    protected WeaponAPI weapon  = null;  /// The lightning gun
    protected SpriteAPI glowParticleSprite = null;  /// The glow sprite

    protected float glowWhirlFadeIn;
    protected float glowWhirlFull;
    protected float glowWhirlFadeOut;

    // --------------------------------
    // Variables that change during run
    // --------------------------------
    protected State lastState = null;  /// Used to tracks if the system is transitioning from off to on.
    protected float pulseCountdown = 0.0f;  /// Timer for the pulse effect, pulse triggers when it's zero.
    protected float damageTotal = 0.0f;  /// Used to count up total damage of projectiles affected
    protected boolean awardedAmmo = false;  /// Set true once ammo has been awarded after the system runs.
    protected float durationRemaining = 0.0f;  /// How long is left in the activation.

    /**
     * Creates a vector at a given length and direction.
     * <p>
     * More convenient than getUnitVectorAtDirection.
     *
     * @param angleDeg  The angle to create the vector at, in degrees.
     * @param length    The length of the vector.
     * @return A vector of the given r and theta.
     */
    public static Vector2f getVectorForAngle(float angleDeg, float length) {
        return new Vector2f(
                (float) FastTrig.cos(Math.toRadians(angleDeg)) * length,
                (float) FastTrig.sin(Math.toRadians(angleDeg)) * length
        );
    }

    /**
     * Gets the offset between the centre of the shield, and the centre of the ship.
     * <p>
     * Used for 'centering' VFX.
     *
     * @param ship  The ship to check.
     * @return The offset, as a vector.
     */
    public static Vector2f getShieldOffset(ShipAPI ship) {
        if (ship == null) return null;
        return Vector2f.sub(
                ship.getLocation(), ship.getShieldCenterEvenIfNoShield(), new Vector2f()
        );

    }

    /**
     * Initialises the system by setting local variables.
     *
     * @param ship  The ship the system belongs to.
     */
    protected void init(ShipAPI ship) {
        if (this.inited) return;
        this.inited = true;
        this.ship = ship;
        this.glowWhirlFadeIn = ship.getSystem().getChargeUpDur() * 2f;
        this.glowWhirlFadeOut = ship.getSystem().getChargeUpDur() * 2f;
        this.glowWhirlFull = ship.getSystem().getChargeActiveDur() - this.glowWhirlFadeOut - this.glowWhirlFadeIn;
        this.durationRemaining = glowWhirlFadeIn + glowWhirlFull + glowWhirlFadeOut;
        this.glowParticleSprite = Global.getSettings().getSprite(ShipSystems.ATTRACTOR_FIELD, GLOW_PARTICLE_SPRITE_KEY);
        for (WeaponAPI weapon: this.ship.getAllWeapons()) {
            if (weapon.getSpec().hasTag(Weapons.STORMCALLER)) this.weapon = weapon;
        }
    }

    /**
     * Gets how long left before the system finishes running.
     *
     * @return The remaining duration.
     */
    public float getDurationRemaining() {
        return this.durationRemaining;
    }

    /**
     * Attracts a projectile towards the ship, and plays a glow visual on it.
     *
     * @param ship_location         The ship's current location. Passed to avoid repeated ship.getLocation() calls.
     * @param projectile            The projectile to attract, may be a missile.
     * @param bearing_change_limit  How much the bearing can change by this pulse.
     * @param speed_change          How much the speed should be reduced by this pulse.
     */
    protected void attractProjectile(
            Vector2f ship_location,
            DamagingProjectileAPI projectile,
            float bearing_change_limit,
            float speed_change
    ) {
        // Get the projectile spec, if possible
        ProjectileSpecAPI projectile_spec = projectile.getProjectileSpec();
        if (projectile_spec == null) return;
        // If it's very large, it's probably a fake projectile; ignore.
        if (projectile.getProjectileSpec().getLength() > 256f || projectile.getProjectileSpec().getWidth() > 256f) {
            return;
        }

        // Calculate the bearing from the projectile to the attractor field ship
        Vector2f projectile_velocity = projectile.getVelocity();
        float bearing_to_ship = VectorUtils.getFacing(
                VectorUtils.getDirectionalVector(projectile.getLocation(), ship_location)
        ) - VectorUtils.getFacing(projectile_velocity);

        float bearing_change = MathUtils.clamp(bearing_to_ship, -bearing_change_limit, +bearing_change_limit);

        // Tweak the projectile's heading and velocity
        VectorUtils.rotate(projectile_velocity, bearing_change);
        projectile_velocity.scale(speed_change);
        projectile.setFacing(MathUtils.clampAngle(projectile.getFacing()+bearing_change));

        // Add this projectile to the damage count if we haven't already
        if (!projectile.getCustomData().containsKey(ShipSystems.ATTRACTOR_FIELD)) {
            projectile.setCustomData(ShipSystems.ATTRACTOR_FIELD, true);

            if (projectile.getDamageType() != null) {
                this.damageTotal += projectile.getDamageAmount() * projectile.getDamageType().getShieldMult();
            }
        }

        // Then add a glow effect that should last until the next pulse
        // Get the projectile's sprite, if any
        SpriteAPI sprite = Global.getSettings().getSprite(projectile_spec.getBulletSpriteName());
        if (sprite == null) sprite = this.glowParticleSprite;

        // Then add a jitter beneath it to show the pull effect
        MagicRender.objectspace(
                sprite, projectile,
                new Vector2f(), new Vector2f(),
                new Vector2f(
                        projectile_spec.getLength() * GLOW_PROJECTILE_SIZE_MULT,
                        projectile_spec.getWidth() * GLOW_PROJECTILE_SIZE_MULT
                ),
                new Vector2f(),
                90f, 0f,
                true,
                GLOW_PROJECTILE_COLOUR,
                true,
                2f, 0f,
                0f, 0f,
                0f,
                PULSE_INTERVAL_SECONDS, PULSE_INTERVAL_SECONDS, 0f, true,
                CombatEngineLayers.BELOW_SHIPS_LAYER
        );
    }

    /**
     * Called every frame to apply the system effects.
     *
     * @param stats         The stats of the ship this system is installed on.
     * @param id            ???
     * @param state         Whether the system is charging up (IN), down (OUT) or fully active (ACTIVE).
     * @param effectLevel   The normalised effect level; scales from 0-1 over charge-up/charge-down time.
     */
    public void apply(MutableShipStatsAPI stats, String id, State state, float effectLevel) {
        if (state == State.IDLE || state == State.COOLDOWN) return;
        ShipAPI ship = (ShipAPI) stats.getEntity();
        if (ship == null || ship.isHulk()) return;
        init(ship);

        float amount;
        if (Global.getCombatEngine().isPaused()) {
            amount = 0f;
        } else {
            amount = Global.getCombatEngine().getElapsedInLastFrame();
        }

        this.durationRemaining = -amount;
        this.pulseCountdown -= amount;
        if (this.pulseCountdown <= 0) {
            // If the amount of time equal to a pulse has elapsed...
            // --------------------------------
            // Track state switching FX
            // --------------------------------
            if (this.lastState != state) {
                // Track whether we need to switch any on/off behaviour
                this.lastState = state;

                if (state == State.IN) {
                    // If we're starting, begin the large-scale VFX
                    playStateInVFX();
                    this.awardedAmmo = false;
                }
            }
            // --------------------------------
            // Generate per-pulse VFX
            // --------------------------------
            if (state != State.OUT) {
                // If we're not fading out, spawn some particles to show the edge of the AoE
                playPulseVFX();
            }
            // --------------------------------
            // Pull projectiles towards ship
            // --------------------------------
            Vector2f ship_location = ship.getLocation();
            float bearing_change_limit = ATTRACTOR_TURN_RATE * PULSE_INTERVAL_SECONDS * effectLevel;
            float speed_change = 1f - (ATTRACTOR_SLOW_RATE * PULSE_INTERVAL_SECONDS * effectLevel);
            int owner = this.ship.getOwner();

            pulseCountdown += PULSE_INTERVAL_SECONDS;
            for (DamagingProjectileAPI projectile: CombatUtils.getProjectilesWithinRange(ship_location, PULSE_RANGE)) {
                if (projectile != null && projectile.getOwner() != owner) {
                    this.attractProjectile(ship_location, projectile, bearing_change_limit, speed_change);
                }
            }
            for (DamagingProjectileAPI projectile: CombatUtils.getMissilesWithinRange(ship_location, PULSE_RANGE)) {
                if (projectile != null && projectile.getOwner() != owner) {
                    this.attractProjectile(ship_location, projectile, bearing_change_limit, speed_change);
                }
            }
        }
    }

    /**
     * Plays the animated whirly vFX around the ship when the system starts.
     */
    protected void playStateInVFX() {
        MagicRender.objectspace(
                Global.getSettings().getSprite(ShipSystems.ATTRACTOR_FIELD, GLOW_WHIRL_SPRITE_KEY),
                this.ship,
                getShieldOffset(ship), new Vector2f(),
                new Vector2f(GLOW_WHIRL_OUTER_RADIUS, GLOW_WHIRL_OUTER_RADIUS), new Vector2f(),
                0.0f, GLOW_WHIRL_OUTER_TURN_RATE,true,
                GLOW_WHIRL_OUTER_COLOUR,false,0f, 0f,
                0f, 0f, 0f,
                glowWhirlFadeIn, glowWhirlFull, glowWhirlFadeOut, true, CombatEngineLayers.BELOW_SHIPS_LAYER
        );
        MagicRender.objectspace(
                Global.getSettings().getSprite(ShipSystems.ATTRACTOR_FIELD, GLOW_WHIRL_SPRITE_KEY),
                this.ship,
                getShieldOffset(ship), new Vector2f(),
                new Vector2f(GLOW_WHIRL_INNER_RADIUS, GLOW_WHIRL_INNER_RADIUS), new Vector2f(),
                0.0f, GLOW_WHIRL_INNER_TURN_RATE,true,
                GLOW_WHIRL_INNER_COLOUR,false,0f, 0f,
                0f, 0f, 0f,
                glowWhirlFadeIn, glowWhirlFull, glowWhirlFadeOut, true, CombatEngineLayers.BELOW_SHIPS_LAYER
        );
    }

    /**
     * Particles spawn around the edge of the AoE.
     *
     * @return      A random value in the valid range.
     */
    public static float getParticleSpawnRadius() {
        return MathUtils.getRandomNumberInRange(
                PULSE_RANGE - GLOW_PARTICLE_RADIUS_RANGE, PULSE_RANGE + GLOW_PARTICLE_RADIUS_RANGE
        );
    }

    /**
     * Particles spawn facing clockwise, with a small range of offsets.
     *
     * @param spawnAngle        The angle the particle is spawned at, from the source.
     * @return                  A random value in the valid range.
     */
    public static float getParticleVelocityAngle(float spawnAngle) {
        return Misc.normalizeAngle(
                spawnAngle - MathUtils.getRandomNumberInRange(
                        90f + GLOW_PARTICLE_ANGLE_IN_MIN, 90f + GLOW_PARTICLE_ANGLE_IN_MAX
                )
        );
    }

    /**
     * Particles spawn with velocities in a small range.
     *
     * @param velocityAngle     The angle the particle is travelling.
     * @return                  A random value in the valid range.
     */
    public static Vector2f getParticleVelocity(float velocityAngle) {
        return getVectorForAngle(
                velocityAngle,
                MathUtils.getRandomNumberInRange(GLOW_PARTICLE_VELOCITY_MIN, GLOW_PARTICLE_VELOCITY_MAX)
        );
    }

    /**
     * Plays the pulse VFX that spawns particles in several times a second.
     */
    protected void playPulseVFX() {
        int num_particles = MathUtils.getRandomNumberInRange(2, 4);
        float particle_angle, particle_velocity_angle;
        Vector2f particle_location, particle_velocity;

        for (int i = 0; i < num_particles; i++) {
            particle_angle = MathUtils.getRandomNumberInRange(0f, 360f);
            particle_velocity_angle = getParticleVelocityAngle(particle_angle);
            particle_location = getVectorForAngle(particle_angle, getParticleSpawnRadius());
            particle_velocity = getParticleVelocity(particle_velocity_angle);

            MagicRender.objectspace(
                    this.glowParticleSprite,
                    this.ship,
                    particle_location,
                    particle_velocity,
                    GLOW_PARTICLE_SIZE, new Vector2f(),
                    0f, 0f, false, GLOW_PROJECTILE_COLOUR,
                    true, GLOW_PARTICLE_FADE_IN, 0.0f, GLOW_PARTICLE_FADE_OUT, false
            );
        }
    }

    /**
     * Called when the system finishes running.
     * <p>
     * Charges up the built-in weapon based on the damage taken.
     *
     * @param stats         The stats API for the ship the system is installed on.
     * @param id            ???
     */
    public void unapply(MutableShipStatsAPI stats, String id) {
        if(this.ship == null) return;
        if(this.weapon == null) return;
        if(this.awardedAmmo) return;

        this.weapon.getAmmoTracker().setAmmo(
                Math.min(
                        (int) (this.damageTotal / DAMAGE_PER_CHARGE),
                        this.weapon.getAmmoTracker().getMaxAmmo()
                )
        );
        this.awardedAmmo = true;
    }

    /**
     * Shown in the active effects UI.
     *
     * @param index         ???
     * @param state         Whether the system is charging up (State.IN),
     *                      down (State.OUT) or fully active (State.ACTIVE).
     * @param effectLevel   The normalised effect level; scales from 0-1 over charge-up/charge-down time.
     * @return              ???
     */
    public StatusData getStatusData(int index, State state, float effectLevel) {
        if (index == 0) {
            return new StatusData("Attracting projectiles within "+PULSE_RANGE, false);
        }
        return null;
    }
}
