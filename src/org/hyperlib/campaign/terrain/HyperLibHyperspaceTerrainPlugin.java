package org.hyperlib.campaign.terrain;

import java.util.*;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.impl.campaign.terrain.HyperspaceTerrainPlugin;
import org.hyperlib.HyperLibTags;

import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;

import com.fs.starfarer.api.util.WeightedRandomPicker;
import org.hyperlib.campaign.terrain.hyperspace.ApplyStormStrikesBase;
import org.hyperlib.campaign.terrain.hyperspace.ApplyStormStrikesHandler;


/**
 *
 */
public class HyperLibHyperspaceTerrainPlugin extends HyperspaceTerrainPlugin {
    /**
     * The initialiser.
     * <p>
     * So far, the only difference is the log entry, but leaving things open.
     *
     * @param terrainId ???
     * @param entity    ???
     * @param param     ???
     */
    @Override
    public void init(String terrainId, SectorEntityToken entity, Object param) {
        super.init(terrainId, entity, param);
        Global.getLogger(this.getClass()).info("init: HyperLib plugin initialised");
    }

    /**
     * Applies the results of a hyperspace storm strike.
     * <p>
     * Evaluates the relevant plugin lists, and then picks the best one.
     *
     * @param cell  The hyperspace cell the fleet is currently in.
     * @param fleet The fleet itself.
     * @param days  ???
     */
    protected void applyStormStrikes(CellStateTracker cell, CampaignFleetAPI fleet, float days) {
        float priority, priority_max = 0;
        List<ApplyStormStrikesBase> valid_plugins = new ArrayList<>();

        // Assemble a list of plugins by priority for this case.
        for (ApplyStormStrikesBase plugin : ApplyStormStrikesHandler.getPlugins()) {
            if ((priority = plugin.getPriority(this, cell, fleet, days)) > 0) {
                if (priority > priority_max) {
                    priority_max = priority;
                    valid_plugins.clear();
                    valid_plugins.add(plugin);
                } else if (priority == priority_max) {
                    valid_plugins.add(plugin);
                }
            }
        }

        if (priority_max > 0) {
            // If any supercede vanilla priority, pick one by weight.
            float weight;
            WeightedRandomPicker<ApplyStormStrikesBase> picker = new WeightedRandomPicker<>();

            for (ApplyStormStrikesBase plugin : valid_plugins) {
                if ((weight = plugin.getWeight(this, cell, fleet, days)) > 0f) {
                    picker.add(plugin, weight);
                }
            }
            ApplyStormStrikesBase plugin = picker.pick();
            if (plugin != null) {
//                log.info("Selected plugin "+plugin.getID());
                plugin.applyStormStrikes(this, cell, fleet, days);
                return;
            }
        }

        // No plugins had priority > 0, or those that did had no weight, so do the vanilla strikes if applicable.
        this.applyVanillaStormStrikes(cell, fleet, days);
    }

    /**
     * Applies a vanilla storm strike.
     * <p>
     * Done as a separate function, to allow for plugins to call it easily.
     *
     * @param cell  The hyperspace cell the fleet is currently in.
     * @param fleet The fleet itself.
     * @param days  ???
     */
    public void applyVanillaStormStrikes(CellStateTracker cell, CampaignFleetAPI fleet, float days) {
        if (!fleet.getMemoryWithoutUpdate().contains("$"+HyperLibTags.HYPERSPACE_STORM_STRIKE_IMMUNE)) {
            super.applyStormStrikes(cell, fleet, days);
        }
    }
}
