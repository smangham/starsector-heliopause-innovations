package toaster.hp.campaign.terrain.hyperspace;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.RepairTrackerAPI;
import com.fs.starfarer.api.impl.campaign.terrain.HyperspaceTerrainPlugin;
import com.fs.starfarer.api.impl.campaign.terrain.HyperspaceTerrainPlugin.CellStateTracker;
import com.fs.starfarer.api.util.Misc;
import org.hyperlib.campaign.terrain.hyperspace.BaseApplyStormStrikesPlugin;
import toaster.hp.campaign.ids.Tags;
import toaster.hp.hullmods.StormDampner;

/**
 * Applies the modified storm strike behaviour for fleets with storm dampners.
 */
public class StormDampnerApplyStormStrikesPlugin extends BaseApplyStormStrikesPlugin {
    protected static final int VALID_PRIORITY = 100;  /// The priority for a fleet matching the criteria.

    /**
     * Gets the ID, used to ensure multiple of the same class can't be registered.
     *
     * @return The ID, generated from the class name.
     */
    @Override
    public String getId() {
        return StormDampnerApplyStormStrikesPlugin.class.getName();
    }

    /**
     * Gets the priority for the plugin. Only nonzero if the fleet is a player fleet with a Storm Dampner tag on it..
     *
     * @param hyperspace The calling hyperspace object.
     * @param cell       The cell the fleet is in.
     * @param fleet      The fleet to check.
     * @param days       Days passed since hyperspace was last advanced.
     * @return Constant if there is a ship in the fleet with a dampner, or 0 if not.
     */
    public int getPriority(
            HyperspaceTerrainPlugin hyperspace,
            CellStateTracker cell,
            CampaignFleetAPI fleet, float days
    ) {
        if (!fleet.isPlayerFleet()) return 0;
        if (fleet.getMemoryWithoutUpdate().contains(StormDampner.KEY)) return VALID_PRIORITY;
        return 0;
    }

    /**
     * Applies the modified storm strike effects; reduces ship CR and applies a more controlled speed boost.
     *
     * @param hyperspace The calling hyperspace object.
     * @param cell       The cell the fleet is in.
     * @param fleet      The fleet to check.
     * @param days       Days passed since hyperspace was last advanced.
     * @return Whether this plugin was applied successfully.
     */
    public boolean applyStormStrikes(
            HyperspaceTerrainPlugin hyperspace,
            CellStateTracker cell,
            CampaignFleetAPI fleet, float days
    ) {
        // Last-minute check - there should always be a member at this point, but if there isn't fall back to vanilla.
        FleetMemberAPI member = StormDampner.getFleetMemberWithDampner(fleet);
        if (member == null) return false;

        // Copied from the vanilla code
        if (cell.flicker != null && cell.flicker.getWait() > 0) {
            cell.flicker.setNumBursts(0);
            cell.flicker.setWait(0);
            cell.flicker.newBurst();
        }
        if (cell.flicker == null || !cell.flicker.isPeakFrame()) return true;

        // Call to a modified HyperStormBoost.
        fleet.addScript(new StormDampnerHyperStormBoost(cell, fleet));

        String key = HyperspaceTerrainPlugin.STORM_STRIKE_TIMEOUT_KEY;
        MemoryAPI mem = fleet.getMemoryWithoutUpdate();
        if (mem.contains(key)) return true;
        mem.set(
                key, true,
                (float) (HyperspaceTerrainPlugin.STORM_MIN_TIMEOUT +
                        (HyperspaceTerrainPlugin.STORM_MAX_TIMEOUT - HyperspaceTerrainPlugin.STORM_MIN_TIMEOUT) * Math.random())
        );

        // New code, for dealing CR damage to the dampner vessel.
        RepairTrackerAPI repair_tracker = member.getRepairTracker();
        if (repair_tracker != null) {
            repair_tracker.applyCREvent(
                    -member.getDeployCost(),
                    Global.getSettings().getString(Tags.STORM_DAMPNER, "cr_event")
            );

            if (fleet.isPlayerFleet()) {
                Global.getSector().getCampaignUI().addMessage(
                        Global.getSettings().getString(Tags.STORM_DAMPNER, "resisted_message")
                                .replace("{{ship}}", member.getShipName())
                                .replace("{{cr}}", "" + (int) (member.getDeployCost() * 100) + "%%"),
                        Misc.getTextColor(),
                        "" + member.getShipName(),
                        "" + (int) (member.getDeployCost() * 100) + "%%",
                        Misc.getHighlightColor(),
                        Misc.getHighlightColor()
                );
            }
        }
        return true;
    }
}