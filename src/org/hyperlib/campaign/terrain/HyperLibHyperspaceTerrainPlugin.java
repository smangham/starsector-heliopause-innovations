package org.hyperlib.campaign.terrain;

import com.fs.starfarer.api.util.Misc;
import org.hyperlib.HyperLibTags;
import org.hyperlib.campaign.terrain.hyperspace.ApplyStormStrikesHandler;

import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.impl.campaign.terrain.HyperspaceTerrainPlugin;

/**
 * Extends the default hyperspace terrain plugin with a framework for modifying other behaviours.
 */
public class HyperLibHyperspaceTerrainPlugin extends HyperspaceTerrainPlugin {
    /**
     * Applies the results of a hyperspace storm strike.
     * <p>
     * Basically just wraps the storm strike plugin handler, or defers to the vanilla version.
     *
     * @param cell  The hyperspace cell the fleet is currently in.
     * @param fleet The fleet itself.
     * @param days  Days passed since hyperspace was last advanced.
     */
    protected void applyStormStrikes(HyperspaceTerrainPlugin.CellStateTracker cell, CampaignFleetAPI fleet, float days) {
        if (!ApplyStormStrikesHandler.fireBestPlugin(this, cell, fleet, days)) {
            // No plugins had priority > 0, or those that did had no weight, so do the vanilla strikes if applicable.
            this.applyVanillaStormStrikes(cell, fleet, days);
        }
    }

    /**
     * Applies a vanilla storm strike.
     * <p>
     * Done as a separate function, to allow for plugins to call it easily.
     *
     * @param cell  The hyperspace cell the fleet is currently in.
     * @param fleet The fleet itself.
     * @param days  Days passed since hyperspace was last advanced.
     */
    public void applyVanillaStormStrikes(HyperspaceTerrainPlugin.CellStateTracker cell, CampaignFleetAPI fleet, float days) {
        if (!fleet.getMemoryWithoutUpdate().contains(HyperLibTags.HYPERSPACE_STORM_STRIKE_IMMUNE_FLAG)) {
            super.applyStormStrikes(cell, fleet, days);
        }
    }

    /**
     * Saves the hyperspace tiles.
     * <p>
     * For some reason this isn't inherited from BaseTiledTerrain and needs redeclaring.
     * Without it, all clouds outside of the currently-loaded zone is scrubbed each save/load.
     *
     * @return The HyperspaceTerrainPlugin.
     */
    @SuppressWarnings("unused")
    Object writeReplace() {
        params.tiles = null;
        savedTiles = encodeTiles(tiles);
        return this;
    }
}
