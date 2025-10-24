package toaster.hp.shipsystems;

import com.fs.starfarer.api.combat.*;
import org.lwjgl.util.vector.Vector2f;


/**
 * Modified version of the Attractor Field AI that also checks for personal safety.
 */
@SuppressWarnings("unused")
public class AttractorFieldMk1AI extends AttractorFieldAI {
    /**
     * Pulses on intervals, triggers the system if the ship or an ally needs help and is in range.
     *
     * @param amount             The amount of time elapsed since last call.
     * @param missileDangerDir   ???
     * @param collisionDangerDir ???
     * @param target             The ship's current AI target.
     */
    @Override
    public void advance(float amount, Vector2f missileDangerDir, Vector2f collisionDangerDir, ShipAPI target) {
        this.tracker.advance(amount);
        this.sinceLast += amount;

        if (this.tracker.intervalElapsed()) {
            // Don't bother checking if the system is on cooldown.
            if (this.system.getCooldownRemaining() > 0) return;
            if (this.system.isOutOfAmmo()) return;
            if (this.system.isActive()) return;
            // Don't trigger the system if the flux is too high.
            if (this.ship.getHardFluxLevel() > 0.7) return;
            if (this.ship.getFluxLevel() > 0.7) return;

            // Trigger the system if the AI for this ship is unhappy.
            if (this.flags.hasFlag(ShipwideAIFlags.AIFlags.NEEDS_HELP)) this.shouldTrigger = true;
            if (this.flags.hasFlag(ShipwideAIFlags.AIFlags.RUN_QUICKLY)) this.shouldTrigger = true;
            if (this.flags.hasFlag(ShipwideAIFlags.AIFlags.HAS_INCOMING_DAMAGE)) this.shouldTrigger = true;

            // Check nearby friendly ships to see if any of them are in need of help.
            if (hasThreatenedAllies()) this.shouldTrigger = true;

            // If the AI has evaluated that it should call the system.
            if (this.shouldTrigger) {
                this.ship.useSystem();
                this.sinceLast = 0f;
                this.shouldTrigger = false;
            }
        }
    }
}
