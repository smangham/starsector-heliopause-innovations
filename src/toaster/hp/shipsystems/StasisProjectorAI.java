package toaster.hp.shipsystems;

import org.lazywizard.lazylib.combat.CombatUtils;
import org.lwjgl.util.vector.Vector2f;

import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipSystemAIScript;
import com.fs.starfarer.api.combat.ShipSystemAPI;
import com.fs.starfarer.api.combat.ShipwideAIFlags;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.combat.ShipwideAIFlags.AIFlags;

/**
 *
 */
@SuppressWarnings("unused")
public class StasisProjectorAI implements ShipSystemAIScript {
    public static final float FLUX_HIGH_THRESHOLD = 0.9f;  /// Don't use the system if it'd bump flux above this level!
    public static final float TARGET_SCORE_MINIMUM = 30f;  /// The minimum 'adjusted' DP of target to use the system on.

	private ShipAPI ship;  /// The ship this system is mounted on.
    @SuppressWarnings("all")
	private CombatEngineAPI engine;  /// The combat engine
    @SuppressWarnings("all")
	private ShipwideAIFlags flags;  /// The AI flags for the ship this system is on.
	private ShipSystemAPI system;  /// The spec for this system.
	
	private IntervalUtil tracker = new IntervalUtil(0.5f, 1f);  /// The interval between AI evaluations.

    @SuppressWarnings("unused")
    private float sinceLast = 0f;  /// Time since the system last ran

    /**
     * Initialises the system AI with the values for this ship.
     * (Why isn't this in the constructor?)
     *
     * @param ship The ship this system is mounted on.
     * @param system The system spec.
     * @param flags The ship's AI flags.
     * @param engine The combat engine.
     */
	public void init(ShipAPI ship, ShipSystemAPI system, ShipwideAIFlags flags, CombatEngineAPI engine) {
		this.ship = ship;
		this.flags = flags;
		this.engine = engine;
		this.system = system;
	}

    /**
     * Called every frame and periodically tries to find a target.
     *
     * @param amount The amount of time since the last frame.
     * @param missileDangerDir The direction, if any, of inbound missiles.
     * @param collisionDangerDir The direction, if any of potential collision.
     * @param target The ship's current target.
     */
	public void advance(float amount, Vector2f missileDangerDir, Vector2f collisionDangerDir, ShipAPI target) {
		tracker.advance(amount);
		sinceLast += amount;
		
		if (tracker.intervalElapsed()) {
			if (system.getCooldownRemaining() > 0) return;
			if (system.isOutOfAmmo()) return;
			if (system.isActive()) return;

            // --------------------------------
            // Is the ship flux too high to use this?
            // --------------------------------
            float fluxLevel = ship.getFluxTracker().getFluxLevel();
            float remainingFluxLevel = 1f - fluxLevel;

            float fluxFractionPerUse = system.getFluxPerUse() / ship.getFluxTracker().getMaxFlux();
            if (fluxFractionPerUse > remainingFluxLevel) return;

            float fluxLevelAfterUse = fluxLevel + fluxFractionPerUse;
            if (fluxLevelAfterUse > FLUX_HIGH_THRESHOLD && fluxFractionPerUse > 0.025f) return;

            // --------------------------------
            // Evaluate possible targets
            // --------------------------------
            ShipAPI systemTarget = null;
            float bestScore = 0f;

			float targetScore;
            ShipwideAIFlags otherFlags;

            for (ShipAPI other: CombatUtils.getShipsWithinRange(ship.getLocation(), StasisProjector.getMaxRange(ship))) {
                otherFlags = other.getAIFlags();

                if (ship == other) continue;  // Can't freeze self!

                if (other.getOwner() == ship.getOwner()) {
                    // Check for friendly ships that are under threat
                   if (otherFlags.hasFlag(AIFlags.IN_CRITICAL_DPS_DANGER)) {
                       targetScore = other.getDeployCost() * 100f;
                       if (targetScore > bestScore) {
                           bestScore = targetScore;
                           systemTarget = other;
                       }
                   }

                } else {
                    // Check for enemy ships that are running away and need stopping
                    if (otherFlags.hasFlag(AIFlags.RUN_QUICKLY) || otherFlags.hasFlag(AIFlags.BACKING_OFF)) {
                        if (otherFlags.hasFlag(AIFlags.IN_CRITICAL_DPS_DANGER)) continue;  // It's already toast
                        if (!otherFlags.hasFlag(AIFlags.NEEDS_HELP)) continue;  // It's not under threat

                        targetScore = other.getDeployCost() * 10f;
                        if (targetScore > bestScore) {
                            bestScore = targetScore;
                            systemTarget = other;
                        }
                    }
                }
            }

			if (bestScore >= TARGET_SCORE_MINIMUM) {
                ship.getAIFlags().setFlag(AIFlags.TARGET_FOR_SHIP_SYSTEM, 1f, systemTarget);
				ship.useSystem();
				sinceLast = 0f;
			}
		}
	}
}
