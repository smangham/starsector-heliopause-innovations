package toaster.hp.shipsystems;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import com.fs.starfarer.api.combat.ShipSystemAPI.SystemState;
import com.fs.starfarer.api.combat.ShipwideAIFlags.AIFlags;
import com.fs.starfarer.api.impl.combat.BaseShipSystemScript;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.util.Misc;
import org.lazywizard.lazylib.MathUtils;
import org.magiclib.util.MagicAnim;
import toaster.hp.campaign.ids.ShipSystems;

import java.awt.*;
import java.util.List;

/**
 * The stasis projector, but modified to apply a brief boost to time rate after the system wears off.
 *
 * @author Alex originally.
 * @author Toaster modified.
 */
public class StasisProjectorMk1 extends StasisProjector {
    public static final String ID = ShipSystems.STASIS_FIELD_MK1;

    public static final float REBOUND_DURATION = 1f;  /// However long the accelerated time rebound lasts.
    public static final String FAST_FLOATING_TEXT = Global.getSettings().getString(ID, "floating_text");

    /**
     * Applies the rebound speed-up after the system wears off.
     *
     * @param targetData The target data used.
     * @param id The id of the ship for mod applying/unapplying.
     */
    @Override
    public void applyPostStasisEffect(TargetData targetData, String id) {
        if (targetData.ship.getOwner() == targetData.target.getOwner()) {
            CombatEngineAPI engine = Global.getCombatEngine();

            engine.addPlugin(
                    new StasisProjectorMk1ReboundPlugin(targetData, id)
            );
            if (targetData.target.getFluxTracker().showFloaty() || targetData.ship == engine.getPlayerShip() || targetData.target == engine.getPlayerShip()) {
                targetData.target.getFluxTracker().showOverloadFloatyIfNeeded(
                        FAST_FLOATING_TEXT, Misc.getHighlightColor(), 4f, true
                );
            }
        }
    }


    /**
     * Plugin added that handles time rebounding after the stasis effect.
     */
    public static class StasisProjectorMk1ReboundPlugin extends BaseEveryFrameCombatPlugin {
        private final String id;
        private final TargetData targetData;
        private float reboundDuration = REBOUND_DURATION;

        /**
         * During the rebound, time accelerates so that in 1 second the ship recovers their full 'active' frozen time.
         * (Integrated area under this 1 second rebound = area under the 3 seconds active)
         */
        private final float reboundTimeMult;


        public StasisProjectorMk1ReboundPlugin(TargetData targetData, String id) {
            this.id = id;
            this.targetData = targetData;
            this.reboundTimeMult = targetData.ship.getSystem().getChargeActiveDur();
        }

        /**
         * Applies the time rebound effect to the system target.
         *
         * @param amount
         * @param events
         */
        @Override
        public void advance(float amount, List<InputEventAPI> events) {
            CombatEngineAPI engine = Global.getCombatEngine();

            if (engine.isPaused()) return;

            this.reboundDuration -= amount;
            float fxMult = MagicAnim.smoothReturnNormalizeRange(
                    this.reboundDuration, 0f, 1f
            );
            float timeMult = fxMult * this.reboundTimeMult;
            float jitterMult = (float) Math.sqrt(MathUtils.clamp(timeMult, 0.25f, 1f));

            if (targetData.target == engine.getPlayerShip()) {
                engine.maintainStatusForPlayerShip(
                        KEY_TARGET,
                        targetData.ship.getSystem().getSpecAPI().getIconSpriteName(),
                        targetData.ship.getSystem().getDisplayName(),
                        "" + (int)(getTimeRate(timeMult) * 100f) + " " + STATUS_TARGET_TEXT, true
                );
            }

            if (this.reboundDuration < 0f || !targetData.ship.isAlive()) {
                engine.getTimeMult().unmodifyMult(id);
                targetData.target.getMutableStats().getTimeMult().unmodifyMult(id);
                targetData.target.getMutableStats().getProjectileSpeedMult().unmodify(id);
                engine.removePlugin(targetData.targetEffectPlugin);

            } else {
                targetData.target.getMutableStats().getTimeMult().modifyMult(id, timeMult);
                targetData.target.getMutableStats().getProjectileSpeedMult().modifyMult(id, timeMult);
                targetData.target.setJitter(
                        this, FAST_JITTER_COLOR, jitterMult,
                        3, 0, 0 + jitterMult * JITTER_MAX_RANGE_BONUS
                );
                targetData.target.setJitterUnder(
                        this, FAST_JITTER_UNDER_COLOR, jitterMult,
                        25, 0f, JITTER_UNDER_BONUS + jitterMult * JITTER_MAX_RANGE_BONUS
                );
                targetData.target.getEngineController().fadeToOtherColor(this, FAST_JITTER_COLOR, new Color(0,0,0,0), fxMult, 0.5f);
                targetData.target.getEngineController().extendFlame(this, -1f, -1f, -1f);
            }
        }
    }
}
