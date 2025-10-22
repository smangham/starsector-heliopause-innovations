package org.hyperlib.campaign.terrain.hyperspace;

import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import org.hyperlib.campaign.terrain.HyperLibHyperspaceTerrainPlugin;


/**
 *
 */
public abstract class ApplyStormStrikesBase {
    /**
     * Returns the ID of this plugin, to avoid double-adding of the same plugin.
     *
     * @return A string that is the ID. Best to include mod name in it.
     */
    abstract public String getID();

    /**
     * Calculates the priority of this plugin.
     * <p>
     * If a plugin has higher priority than all others, it wins and will be called.
     * If no plugin has a priority > 0, then the vanilla behaviour will be used.
     *
     * @param hyperspace    The calling hyperspace object.
     * @param cell          The cell the fleet is in.
     * @param fleet         The fleet to check.
     * @param days          ???
     * @return              The priority. Suggested range is 1-100, higher priorities should be for
     *                      more specific situations. Priority of 0 or below means 'prefer vanilla'.
     */
    public int getPriority(
            HyperLibHyperspaceTerrainPlugin hyperspace,
            HyperLibHyperspaceTerrainPlugin.CellStateTracker cell,
            CampaignFleetAPI fleet,
            float days
    ) {
        return 0;
    }

    /**
     * Calculates the weight of this plugin.
     * <p>
     * If a plugin has equal priority to all others, then which is used is selected at random using their relative weights.
     *
     * @param hyperspace    The calling hyperspace object.
     * @param cell          The cell the fleet is in.
     * @param fleet         The fleet to check.
     * @param days          ???
     * @return              The weight, relative to other plugins.
     *                      Try to keep it around 1; if the weight is 100+, consider using priority instead.
     */
    public float getWeight(
            HyperLibHyperspaceTerrainPlugin hyperspace,
            HyperLibHyperspaceTerrainPlugin.CellStateTracker cell,
            CampaignFleetAPI fleet,
            float days
    ) {
        return 1f;
    }

    /**
     * Replaces the vanilla storm strike code.
     * <p>
     * This should be implemented by the plugin extending this base.
     *
     * @param hyperspace    The calling hyperspace object.
     * @param cell          The cell the fleet is in.
     * @param fleet         The fleet to check.
     * @param days          ???
     */
    public void applyStormStrikes(
            HyperLibHyperspaceTerrainPlugin hyperspace,
            HyperLibHyperspaceTerrainPlugin.CellStateTracker cell,
            CampaignFleetAPI fleet,
            float days
    ) {
        hyperspace.applyVanillaStormStrikes(cell, fleet, days);
    }
}
