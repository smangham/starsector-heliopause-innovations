package toaster.hp.shipsystems;

import org.lazywizard.lazylib.combat.CombatUtils;
import org.lwjgl.util.vector.Vector2f;

import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipSystemAIScript;
import com.fs.starfarer.api.combat.ShipSystemAPI;
import com.fs.starfarer.api.combat.ShipwideAIFlags;
import com.fs.starfarer.api.util.IntervalUtil;


/**
 * Evaluates whether the EnergyAttractor system should be triggered.
 * <p>
 * Checks to see if there's nearby ships to help, and if this ship is in a fit state to risk pulling in projectiles.
 */
public class AttractorFieldAI implements ShipSystemAIScript {
    // Variables set during initialisation of the system.
	protected ShipAPI ship;   /// The ship with the AI.
    protected CombatEngineAPI engine;   /// The combat engine.
    protected ShipwideAIFlags flags;   /// The ship's general AI flags.
    protected ShipSystemAPI system;   /// The ship system this AI is for.

    // Variables set during actual loops.
    protected IntervalUtil tracker = new IntervalUtil(0.5f, 1f);   /// Used to space assessment.
    protected boolean shouldTrigger = false;  /// Whether the system should be triggered
    protected float sinceLast = 0f;  /// Tracks time since last run.

    /**
     * Initialise the system variables.
     *
     * @param ship              The ship the system is mounted on.
     * @param system            The system this AI is managing.
     * @param flags             The AI flags for the ship.
     * @param engine            The current combat engine.
     */
	public void init(ShipAPI ship, ShipSystemAPI system, ShipwideAIFlags flags, CombatEngineAPI engine) {
		this.ship = ship;
		this.flags = flags;
		this.engine = engine;
		this.system = system;
	}

    /**
     * Check nearby friendly ships to see if any of them are in need of help.
     *
     * @return                  True if any friendlies in range are threatened.
     */
    protected boolean hasThreatenedAllies() {
        for (ShipAPI ship_search: CombatUtils.getShipsWithinRange(this.ship.getLocation(), AttractorField.PULSE_RANGE)) {
            if (ship_search.getOwner() == this.ship.getOwner()) {
                ShipwideAIFlags ship_search_flags = ship_search.getAIFlags();
                if (ship_search_flags.hasFlag(ShipwideAIFlags.AIFlags.HAS_INCOMING_DAMAGE)
                        | ship_search_flags.hasFlag(ShipwideAIFlags.AIFlags.NEEDS_HELP)
                        | ship_search_flags.hasFlag(ShipwideAIFlags.AIFlags.BACKING_OFF)
                ) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Pulses on intervals, triggers the system if an ally needs help and is in range.
     *
     * @param amount                The amount of time elapsed since last call.
     * @param missileDangerDir      ???
     * @param collisionDangerDir    ???
     * @param target                The ship's current AI target.
     */
	@SuppressWarnings("unchecked")
	public void advance(float amount, Vector2f missileDangerDir, Vector2f collisionDangerDir, ShipAPI target) {
        this.tracker.advance(amount);
        this.sinceLast += amount;
		
		if (tracker.intervalElapsed()) {
            // Don't bother checking if the system is on cooldown.
			if (this.system.getCooldownRemaining() > 0) return;
			if (this.system.isOutOfAmmo()) return;
			if (this.system.isActive()) return;
            // Don't trigger the system if the flux is too high.
            if (this.ship.getHardFluxLevel() > 0.5) return;
            if (this.ship.getFluxLevel() > 0.7) return;

            // Don't trigger the system if the AI for this ship is unhappy.
			if (this.flags.hasFlag(ShipwideAIFlags.AIFlags.BACKING_OFF)) return;
            if (this.flags.hasFlag(ShipwideAIFlags.AIFlags.NEEDS_HELP)) return;
            if (this.flags.hasFlag(ShipwideAIFlags.AIFlags.RUN_QUICKLY)) return;

            // Does the ship have any threatened allies to protect?
			if (hasThreatenedAllies()) {
				this.ship.useSystem();
				this.sinceLast = 0f;
                this.shouldTrigger = false;
			}
		}
	}
}
