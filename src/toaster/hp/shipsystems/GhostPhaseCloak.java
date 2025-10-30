package toaster.hp.shipsystems;

import java.awt.Color;
import java.util.Random;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.impl.combat.BaseShipSystemScript;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;
import org.hyperlib.HyperLibColours;
import org.hyperlib.combat.graphics.HyperspaceTiledSpriteSamplers;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.util.vector.Vector2f;
import org.magiclib.util.MagicRender;
import toaster.hp.hullmods.GhostPossessed;

/**
 * Mini-phase jaunt to avoid damage.
 */
public class GhostPhaseCloak extends BaseShipSystemScript {
    public static final float SHIP_ALPHA_MULT = 0.25f;
    ///  The minimum value the ship alpha drops to
    public static final float CHARGE_COST = 0.5f;
    ///  How much charge is reduced by this effect

    // --------------------------------
    // Ship jitter FX
    // --------------------------------
    public static final int JITTER_COPIES = 8;
    /// How many copies of the ship to jitter out
    public static final float JITTER_RADIUS_MULT = 0.5f;
    /// How far out to jitter, and how far from the ship to spawn clouds, in ship radii

    // --------------------------------
    // Ship cloud FX
    // --------------------------------
    public static final float CLOUD_SIZE_BASE = 1f;
    public static final float CLOUD_SIZE_MULT = 0.5f;
    /// How much larger the clouds grow over the effect duration
    public static final float CLOUD_ROTATION_ANGLE_RANGE = 30f;
    ///  How wide the range of angular rotation in spawned clouds is
    public static final Color CLOUD_OVER_COLOUR = new Color(255, 255, 255, 32);
    ///  The colour of the clouds spawned over the ship; very transparent
    public static final float CLOUD_SPAWN_RADIUS_BASE = 0.5f;
    /// How far out to spawn clouds when effect level is 0.
    public static final float CLOUD_SPAWN_RADIUS_MULT = 0.5f;
    /// How much further out to spawn clouds when effect level is 1.

    // --------------------------------
    // Variables that change during run
    // --------------------------------
    protected IntervalUtil cloudInterval = new IntervalUtil(0.1f, 0.2f);
    /// The interval between cloud spawns
    protected Vector2f cloudSize;
    /// The size of the FX clouds
    protected State lastState = State.IDLE;
    /// Used to track ship system turn on/off

    /**
     * Called every frame whilst the ship is phasing
     *
     * @param stats The ship's stats.
     * @param id The id of the system, for applying modifiers.
     * @param state The state of the system (IN/ACTIVE/OUT/COOLDOWN/IDLE).
     * @param effectLevel The effect level, scales 0-1 during in, 1-0 during out.
     */
	public void apply(MutableShipStatsAPI stats, String id, State state, float effectLevel) {
        if (stats.getEntity() instanceof ShipAPI ship) {
            CombatEngineAPI engine = Global.getCombatEngine();

            if (state != this.lastState) {
                this.lastState = state;
                if (state == State.IN) {
                    ship.setPhased(true);
                    this.cloudSize = GhostPossessed.getCloudSize(ship);
                }
            }

            cloudInterval.advance(engine.getElapsedInLastFrame());
            if (cloudInterval.intervalElapsed()) {
                int cloudsBelow = MathUtils.getRandomNumberInRange(1, ship.getHullSize().ordinal()-1);
                int cloudsAbove = MathUtils.getRandomNumberInRange(1, ship.getHullSize().ordinal()-1);
                float spawnRadius = ship.getCollisionRadius() * (CLOUD_SPAWN_RADIUS_BASE + CLOUD_SPAWN_RADIUS_MULT * effectLevel);
                Vector2f cloudSize = (Vector2f) new Vector2f(this.cloudSize).scale(CLOUD_SIZE_BASE + CLOUD_SIZE_MULT * effectLevel);

                for (int i = 0; i < cloudsBelow; i++) {
                    MagicRender.battlespace(
                            HyperspaceTiledSpriteSamplers.getHyperspaceDarkSprite(),
                            Misc.getPointWithinRadiusUniform(ship.getLocation(), spawnRadius, new Random()), new Vector2f(),
                            cloudSize, new Vector2f(),
                            MathUtils.getRandomNumberInRange(0f, 360f),
                            MathUtils.getRandomNumberInRange(-CLOUD_ROTATION_ANGLE_RANGE, CLOUD_ROTATION_ANGLE_RANGE),
                            Color.WHITE, false,
                            0f, 0f, 0f, 0f, 0f,
                            GhostPossessed.CLOUD_FADE_IN, 0f, GhostPossessed.CLOUD_FADE_OUT,
                            CombatEngineLayers.ABOVE_PLANETS
                    );
                }
                for (int i = 0; i < cloudsAbove; i++) {
                    MagicRender.battlespace(
                            HyperspaceTiledSpriteSamplers.getHyperspaceSprite(),
                            Misc.getPointWithinRadiusUniform(ship.getLocation(), spawnRadius, new Random()), new Vector2f(),
                            cloudSize, new Vector2f(),
                            MathUtils.getRandomNumberInRange(0f, 360f),
                            MathUtils.getRandomNumberInRange(-CLOUD_ROTATION_ANGLE_RANGE, CLOUD_ROTATION_ANGLE_RANGE),
                            CLOUD_OVER_COLOUR, false,
                            0f, 0f, 0f, 0f, 0f,
                            GhostPossessed.CLOUD_FADE_IN, 0f, GhostPossessed.CLOUD_FADE_OUT,
                            CombatEngineLayers.ABOVE_SHIPS_LAYER
                    );
                }
            }

            ship.setExtraAlphaMult(1f - (1f - SHIP_ALPHA_MULT) * effectLevel);
            ship.setApplyExtraAlphaToEngines(true);
            ship.setJitter(
                    ship, HyperLibColours.DEEP_HYPERSPACE_STRIKE, effectLevel,
                    JITTER_COPIES, ship.getCollisionRadius() * effectLevel * JITTER_RADIUS_MULT
            );
            ship.setCircularJitter(true);
        }
	}

    /**
     * Removes the effect when wind-down finishes.
     *
     * @param stats The ship's stats.
     * @param id The id for modifiers.
     */
	public void unapply(MutableShipStatsAPI stats, String id) {
		if (stats.getEntity() instanceof ShipAPI ship) {
            ship.setPhased(false);
            ship.setExtraAlphaMult(1f);
            GhostPossessed.modifyCharge(ship, CHARGE_COST);
		}
	}
}
