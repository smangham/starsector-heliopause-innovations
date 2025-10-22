package toaster.hp.hullmods;

import com.fs.starfarer.api.combat.BaseHullMod;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import toaster.hp.campaign.ids.MutableStats;


/**
 * Removes the fighter bays from the original model, and doubles the mote capacity compared to the hull default.
 */
public class PinnacleMk1 extends BaseHullMod {
    @Override
    public void applyEffectsBeforeShipCreation(ShipAPI.HullSize hullSize, MutableShipStatsAPI stats, String id) {
        super.applyEffectsBeforeShipCreation(hullSize, stats, id);
        stats.getBreakProb().modifyMult(id, 0f);
        stats.getNumFighterBays().modifyFlat(id, -2f);
        stats.getDynamic().getStat(MutableStats.GHOST_MOTE_CAPACITY).modifyMult(id, 2.0f);
    }
}
