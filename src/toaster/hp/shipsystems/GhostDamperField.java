package toaster.hp.shipsystems;

import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.impl.combat.DamperFieldStats;
import toaster.hp.hullmods.GhostPossessed;

/**
 * Modified version of the damper field
 */
@SuppressWarnings("unused")
public class GhostDamperField extends DamperFieldStats {
    public static final float DAMAGE_TAKEN_MULT = 0.25f;
    public static final float CHARGE_COST = 1f;

    protected State lastState = State.IDLE;

    /**
     * @param stats
     * @param id
     * @param state
     * @param effectLevel
     */
    @Override
    public void apply(MutableShipStatsAPI stats, String id, State state, float effectLevel) {
        if (state != lastState) {
            lastState = state;
            if (state == State.IN) {
                stats.getHullDamageTakenMult().modifyMult(id, DAMAGE_TAKEN_MULT);
                stats.getArmorDamageTakenMult().modifyMult(id, DAMAGE_TAKEN_MULT);

                if (stats.getEntity() instanceof ShipAPI ship) {
                    GhostPossessed ghostPossessed = GhostPossessed.getGhostPossessedScriptFor(ship);
                    if (ghostPossessed != null) ghostPossessed.modifyCharge(-CHARGE_COST);
                }
            }
        }
    }
}
