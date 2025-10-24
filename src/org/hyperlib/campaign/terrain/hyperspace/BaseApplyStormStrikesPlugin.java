package org.hyperlib.campaign.terrain.hyperspace;

import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.impl.campaign.terrain.HyperspaceTerrainPlugin;
import com.fs.starfarer.api.impl.campaign.terrain.HyperspaceTerrainPlugin.CellStateTracker;

/**
 * Base class to extend when wanting to modify storm strike behaviour.
 * <p>
 * When extending, you need to:
 * <ul>
 *     <li>Implement a `getId` with your plugin's id.</li>
 *     <li>Implement `getPriority` for your plugin.</li>
 *     <li>Optionally override `getWeight`.</li>
 *     <li>Use `ApplyStormStrikesHandler.registerPlugin` to register your plugin in your ModPlugin's OnApplicationLoad.</li>
 * </ul>
 */
public abstract class BaseApplyStormStrikesPlugin {
    /**
     * Returns the ID of this plugin, to avoid double-adding of the same plugin.
     *
     * @return A string that is the ID. Best to include mod name in it.
     */
    public abstract String getId();

    /**
     * Calculates the priority of this plugin.
     * <p>
     * If a plugin has higher priority than all others, it wins and will be called.
     * If no plugin has a priority > 0, then the vanilla behaviour will be used.
     *
     * @param hyperspace The calling hyperspace object.
     * @param cell       The cell the fleet is in.
     * @param fleet      The fleet to check.
     * @param days       Days passed since hyperspace was last advanced.
     * @return The priority. Suggested range is 1-100, higher priorities should be for more specific situations.
     * Priority of 0 or below means 'prefer vanilla'.
     */
    public abstract int getPriority(
            HyperspaceTerrainPlugin hyperspace,
            CellStateTracker cell,
            CampaignFleetAPI fleet,
            float days
    );

    /**
     * Calculates the weight of this plugin.
     * <p>
     * If a plugin has equal priority to all others, then which is used is selected at random using their relative weights.
     *
     * @param hyperspace The calling hyperspace object.
     * @param cell       The cell the fleet is in.
     * @param fleet      The fleet to check.
     * @param days       Days passed since hyperspace was last advanced.
     * @return The weight, relative to other plugins. Try to keep it around 1.
     * If the weight is 100+, consider using priority instead.
     */
    @SuppressWarnings("unused")
    public float getWeight(
            HyperspaceTerrainPlugin hyperspace,
            CellStateTracker cell,
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
     * @param hyperspace The calling hyperspace object.
     * @param cell       The cell the fleet is in.
     * @param fleet      The fleet to check.
     * @param days       Days passed since hyperspace was last advanced.
     * @return True if the storm strike plugin was called successfully,
     * false if the code should fall back to vanilla behaviour.
     */
    public abstract boolean applyStormStrikes(
            HyperspaceTerrainPlugin hyperspace,
            CellStateTracker cell,
            CampaignFleetAPI fleet,
            float days
    );
}
