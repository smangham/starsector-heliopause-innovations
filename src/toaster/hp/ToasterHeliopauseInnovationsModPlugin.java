package toaster.hp;

import com.fs.starfarer.api.BaseModPlugin;
import com.fs.starfarer.api.Global;
import org.hyperlib.campaign.terrain.HyperLibHyperspaceTerrainPlugin;
import com.fs.starfarer.api.impl.campaign.terrain.HyperspaceTerrainPlugin;
import com.fs.starfarer.api.util.Misc;
import org.hyperlib.campaign.terrain.hyperspace.ApplyStormStrikesHandler;
import toaster.hp.campaign.terrain.hyperspace.GhostSensorProfileApplyStormStrikesPlugin;
import toaster.hp.campaign.terrain.hyperspace.StormDampnerApplyStormStrikesPlugin;
import toaster.hp.world.SectorGen;

/**
 *
 */
public class ToasterHeliopauseInnovationsModPlugin extends BaseModPlugin {
//    private void initialise() {
//        String last_version_str = Global.getSector().getPersistentData().get("tstHP_version");
//        if (last_version_str == null) {
//
//        }
//    }

    /**
     * Does the setup.
     */
    public static void setUp() {
        new SectorGen().generate(Global.getSector());
        HyperspaceTerrainPlugin hyper = Misc.getHyperspaceTerrainPlugin();
        Global.getLogger(ToasterHeliopauseInnovationsModPlugin.class).info(
                "setUp: HyperspaceTerrainPlugin class is "+hyper.getClass().getName()
        );
        if (hyper instanceof HyperLibHyperspaceTerrainPlugin hyperlib_subclass) {
            Global.getLogger(ToasterHeliopauseInnovationsModPlugin.class).info("setUp: Plugin correct, registering handlers");
            ApplyStormStrikesHandler.registerStormStrikes(new StormDampnerApplyStormStrikesPlugin());
            ApplyStormStrikesHandler.registerStormStrikes(new GhostSensorProfileApplyStormStrikesPlugin());
        }
    }

    @Override
    public void onApplicationLoad() throws Exception {
        super.onApplicationLoad();
        Global.getLogger(ToasterHeliopauseInnovationsModPlugin.class).info("onApplicationLoad: Loaded.");
    }

    /**
     * Run after the economy has been run on a new game.
     * <p>
     * Doing this, rather than `onNewGame`, to ensure that Ailmar e.t.c. exists.
     */
    @Override
    public void onNewGameAfterEconomyLoad() {
        Global.getLogger(ToasterHeliopauseInnovationsModPlugin.class).info("OnNewGameAfterEconomyLoad");
        super.onNewGameAfterEconomyLoad();
        setUp();
    }

    /**
     * Do sector gen and hyperspace plugin registration on load.
     *
     * @param newGame   Is this the 'load' of  starting a new game, true or false.
     */
    @Override
    public void onGameLoad(boolean newGame) {
        Global.getLogger(ToasterHeliopauseInnovationsModPlugin.class).info("OnGameLoad");
        super.onNewGame();
        setUp();
    }

    // You can add more methods from ModPlugin here. Press Control-O in IntelliJ to see options.
}
