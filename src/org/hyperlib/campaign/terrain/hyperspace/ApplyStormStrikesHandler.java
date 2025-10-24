package org.hyperlib.campaign.terrain.hyperspace;

import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.impl.campaign.terrain.HyperspaceTerrainPlugin;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import org.hyperlib.campaign.terrain.HyperLibHyperspaceTerrainPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Class that registers storm strike handlers and selects and calls them as appropriate.
 */
public class ApplyStormStrikesHandler {
    /**
     * List of plugins for handling storm strikes.
     * <p>
     * Registered by mods on start. They need to reregister each startup, as static is implicitly transient.
     */
    protected static List<BaseApplyStormStrikesPlugin> applyStormStrikesPlugins = new ArrayList<>();

    public static List<BaseApplyStormStrikesPlugin> getPlugins() {
        return applyStormStrikesPlugins;
    }

    /**
     * Registers a new plugin with the storm strike handler.
     * <p>
     * Checks to prevent double-adding, though since the list is transient it shouldn't matter.
     * Plugins are cleared and must be re-added every launch.
     *
     * @param plugin An implementation of the plugin to register.
     * @return True if the plugin was added, false if it already existed.
     */
    public static boolean registerStormStrikesPlugin(
            BaseApplyStormStrikesPlugin plugin
    ) {
        boolean needsAdding = true;
        if (applyStormStrikesPlugins == null) applyStormStrikesPlugins = new ArrayList<>();
        for (BaseApplyStormStrikesPlugin existing_plugin : applyStormStrikesPlugins) {
            if (Objects.equals(existing_plugin.getId(), plugin.getId())) needsAdding = false;
        }
        HyperspaceTerrainPlugin terrainPlugin = Misc.getHyperspaceTerrainPlugin();
        HyperLibHyperspaceTerrainPlugin hyperlibPlugin = HyperLibHyperspaceTerrainPlugin.class.cast(terrainPlugin);


        if (needsAdding) applyStormStrikesPlugins.add(plugin);
        return needsAdding;
    }

    /**
     * Searches the list of plugins and calls the most appropriate one.
     *
     * @param hyperspace The calling hyperspace object.
     * @param cell       The cell the fleet is in.
     * @param fleet      The fleet to check.
     * @param days       Days passed since hyperspace was last advanced.
     * @return True if a plugin was found and called successfully, false it it should fall back to vanilla behaviour.
     */
    public static boolean fireBestPlugin(
            HyperspaceTerrainPlugin hyperspace, HyperspaceTerrainPlugin.CellStateTracker cell, CampaignFleetAPI fleet, float days
    ) {
        float priority, priorityMax = 0;
        List<BaseApplyStormStrikesPlugin> valid_plugins = new ArrayList<>();

        // Assemble a list of plugins by priority for this case.
        for (BaseApplyStormStrikesPlugin plugin : ApplyStormStrikesHandler.getPlugins()) {
            if ((priority = plugin.getPriority(hyperspace, cell, fleet, days)) > 0) {
                if (priority > priorityMax) {
                    priorityMax = priority;
                    valid_plugins.clear();
                    valid_plugins.add(plugin);
                } else if (priority == priorityMax) {
                    valid_plugins.add(plugin);
                }
            }
        }

        if (priorityMax > 0) {
            // If any supercede vanilla priority, pick one by weight.
            float weight;
            WeightedRandomPicker<BaseApplyStormStrikesPlugin> picker = new WeightedRandomPicker<>();

            for (BaseApplyStormStrikesPlugin plugin : valid_plugins) {
                if ((weight = plugin.getWeight(hyperspace, cell, fleet, days)) > 0f) {
                    picker.add(plugin, weight);
                }
            }
            BaseApplyStormStrikesPlugin plugin = picker.pick();
            if (plugin != null) {
                return plugin.applyStormStrikes(hyperspace, cell, fleet, days);
            }
        }
        return false;
    }
}
