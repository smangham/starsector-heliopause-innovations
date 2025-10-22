package toaster.hp.campaign.terrain.hyperspace;

import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import org.hyperlib.campaign.terrain.HyperLibHyperspaceTerrainPlugin;
import org.hyperlib.HyperLibTags;
import org.hyperlib.campaign.terrain.hyperspace.ApplyStormStrikesBase;

/**
 * Applies the modified storm strike behaviour for ghost fleets.
 */
public class GhostSensorProfileApplyStormStrikesPlugin extends ApplyStormStrikesBase {
    public static final String ID = "ghost_storm_sensor_boost";
    protected static final String IMMUNE_FLAG = "$"+HyperLibTags.HYPERSPACE_STORM_STRIKE_IMMUNE;
    protected static final int VALID_PRIORITY = 100;

    /**
     * Gets the ID, used to ensure multiple of the same class can't be registered.
     *
     * @return  The ID, generated from the class name.
     */
    @Override
    public String getID() {
        return GhostSensorProfileApplyStormStrikesPlugin.class.getName();
    }

    /**
     * Gets the priority for the plugin. Only nonzero if the fleet has the tag on it..
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
        if (fleet.getMemoryWithoutUpdate().contains(IMMUNE_FLAG)) return VALID_PRIORITY;
        return 0;
    }

    /**
     * Applies the modified storm strike effects; boosts sensor profile.
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
        // Copied from the vanilla code
        if (cell.flicker != null && cell.flicker.getWait() > 0) {
            cell.flicker.setNumBursts(0);
            cell.flicker.setWait(0);
            cell.flicker.newBurst();
        }
        if (cell.flicker == null || !cell.flicker.isPeakFrame()) return;

        FleetMemberAPI ship = fleet.getFlagship();
        ship.getBuffManager().addBuff(
                new GhostSensorProfileStormBoost(ID+"_"+ship.getId())
        );

        String key = HyperLibHyperspaceTerrainPlugin.STORM_STRIKE_TIMEOUT_KEY;
        MemoryAPI mem = fleet.getMemoryWithoutUpdate();
        if (mem.contains(key)) return;
        mem.set(key, true, (float) (HyperLibHyperspaceTerrainPlugin.STORM_MIN_TIMEOUT + (HyperLibHyperspaceTerrainPlugin.STORM_MAX_TIMEOUT - HyperLibHyperspaceTerrainPlugin.STORM_MIN_TIMEOUT) * Math.random()));
    }
}