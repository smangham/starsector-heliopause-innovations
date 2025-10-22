package org.hyperlib.campaign.terrain.hyperspace;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;


public class ApplyStormStrikesHandler {
    /**
     * List of plugins for handling storm strikes.
     * <p>
     * Registered by mods on start. They need to reregister each startup.
     */
    protected static List<ApplyStormStrikesBase> applyStormStrikesPlugins = new ArrayList<ApplyStormStrikesBase>();

    /**
     * Registers a new plugin with the storm strike handler.
     * <p>
     * Checks to prevent double-adding, though since the list is transient it shouldn't matter.
     * Plugins are cleared and must be re-added every launch.
     *
     * @param plugin    An implementation of the plugin to register.
     * @return          True if the plugin was added, false if it already existed.
     */
    public static boolean registerStormStrikes(
            ApplyStormStrikesBase plugin
    ) {
        boolean needs_adding = true;
        if (applyStormStrikesPlugins == null) applyStormStrikesPlugins = new ArrayList<ApplyStormStrikesBase>();
        for (ApplyStormStrikesBase existing_plugin : applyStormStrikesPlugins) {
            if (Objects.equals(existing_plugin.getID(), plugin.getID())) needs_adding = false;
        }
        if (needs_adding) applyStormStrikesPlugins.add(plugin);
        return needs_adding;
    }

    public static List<ApplyStormStrikesBase> getPlugins() { return applyStormStrikesPlugins; }
}
