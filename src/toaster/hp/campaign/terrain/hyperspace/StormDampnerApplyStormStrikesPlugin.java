package toaster.hp.campaign.terrain.hyperspace;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.RepairTrackerAPI;
import org.hyperlib.campaign.terrain.HyperLibHyperspaceTerrainPlugin;
import com.fs.starfarer.api.util.Misc;
import org.hyperlib.campaign.terrain.hyperspace.ApplyStormStrikesBase;
import toaster.hp.hullmods.StormDampner;


/**
 * Applies the modified storm strike behaviour for fleets with storm dampners.
 */
public class StormDampnerApplyStormStrikesPlugin extends ApplyStormStrikesBase {
    protected static final int VALID_PRIORITY = 100;
//    protected static Logger log = Global.getLogger(StormDampnerApplyStormStrikesPlugin.class);

    /**
     * Gets the ID, used to ensure multiple of the same class can't be registered.
     *
     * @return  The ID, generated from the class name.
     */
    @Override
    public String getID() {
        return StormDampnerApplyStormStrikesPlugin.class.getName();
    }

    /**
     * Gets the priority for the plugin. Only nonzero if the fleet is a player fleet with a Storm Dampner tag on it..
     *
     * @param hyperspace    The calling hyperspace object.
     * @param cell          The cell the fleet is in.
     * @param fleet         The fleet to check.
     * @param days          ???
     * @return              Constant if there is a ship in the fleet with a dampner, or 0 if not.
     */
    public int getPriority(
            HyperLibHyperspaceTerrainPlugin hyperspace,
            HyperLibHyperspaceTerrainPlugin.CellStateTracker cell,
            CampaignFleetAPI fleet, float days
    ) {
        if (!fleet.isPlayerFleet()) return 0;
        Global.getLogger(StormDampnerApplyStormStrikesPlugin.class).info("getPriority: Tags are "+fleet.getMemoryWithoutUpdate().getKeys());
        if (fleet.getMemoryWithoutUpdate().contains(StormDampner.KEY)) return VALID_PRIORITY;
        return 0;
    }

    /**
     * Applies the modified storm strike effects; reduces ship CR and applies a more controlled speed boost.
     *
     * @param hyperspace    The calling hyperspace object.
     * @param cell          The cell the fleet is in.
     * @param fleet         The fleet to check.
     * @param days          ???
     */
    public void applyStormStrikes(
            HyperLibHyperspaceTerrainPlugin hyperspace,
            HyperLibHyperspaceTerrainPlugin.CellStateTracker cell,
            CampaignFleetAPI fleet, float days
    ) {
        // Last-minute check - there should always be a member at this point, but if there isn't fall back to vanilla.
        FleetMemberAPI member = StormDampner.getFleetMemberWithDampner(fleet);
        if (member == null) {
             Global.getLogger(this.getClass()).info("applyStormStrikes: No member with right tag");
            hyperspace.applyVanillaStormStrikes(cell, fleet, days);
            return;
        }

        // Copied from the vanilla code
        if (cell.flicker != null && cell.flicker.getWait() > 0) {
            cell.flicker.setNumBursts(0);
            cell.flicker.setWait(0);
            cell.flicker.newBurst();
        }
        if (cell.flicker == null || !cell.flicker.isPeakFrame()) return;

        // Call to a modified HyperStormBoost.
        fleet.addScript(new StormDampnerHyperStormBoost(cell, fleet));

        String key = HyperLibHyperspaceTerrainPlugin.STORM_STRIKE_TIMEOUT_KEY;
        MemoryAPI mem = fleet.getMemoryWithoutUpdate();
        if (mem.contains(key)) return;
        mem.set(key, true, (float) (HyperLibHyperspaceTerrainPlugin.STORM_MIN_TIMEOUT + (HyperLibHyperspaceTerrainPlugin.STORM_MAX_TIMEOUT - HyperLibHyperspaceTerrainPlugin.STORM_MIN_TIMEOUT) * Math.random()));

        // New code, for dealing CR damage to the dampner vessel.
        RepairTrackerAPI repair_tracker = member.getRepairTracker();
        if (repair_tracker != null) {
            repair_tracker.applyCREvent(-member.getDeployCost(), "Resisted hyperspace storm damage.");

            if (fleet.isPlayerFleet()) {
                Global.getSector().getCampaignUI().addMessage(
                        member.getShipName()+" loses " + (int) member.getDeployCost() + "% CR resisting storm damage",
                        Misc.getTextColor(),
                        "" + (int) member.getDeployCost() + "% CR",
                        "",
                        Misc.getHighlightColor(), Misc.getHighlightColor()
                );
            }
        }
    }
}