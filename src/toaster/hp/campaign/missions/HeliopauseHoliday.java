package toaster.hp.campaign.missions;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.combat.BattleCreationContext;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.*;
import com.fs.starfarer.api.impl.campaign.ids.*;
import com.fs.starfarer.api.impl.campaign.missions.academy.GACelestialObject;
import com.fs.starfarer.api.impl.campaign.missions.hub.HubMissionWithSearch;
import com.fs.starfarer.api.impl.campaign.missions.hub.ReqMode;
import com.fs.starfarer.api.impl.campaign.procgen.themes.BaseThemeGenerator;
import com.fs.starfarer.api.impl.campaign.procgen.themes.RemnantSeededFleetManager;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.special.ShipRecoverySpecial;
import com.fs.starfarer.api.loading.Description;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import org.lazywizard.lazylib.MathUtils;
import toaster.hp.campaign.ids.Missions;
import toaster.hp.campaign.ids.Variants;

import java.awt.*;
import java.util.List;
import java.util.Map;
import java.util.Set;


/**
 * Mission to test out time dilation in the event horizon.
 */
public class HeliopauseHoliday extends HubMissionWithSearch {
    public static final String ID = Missions.HELIOPAUSE_TRIAL_HOLIDAY;

    protected StarSystemAPI system;  /// The system the black hole is in.
    protected CampaignTerrainAPI eventHorizon;  /// The black hole event horizon.
    protected MarketAPI nomios;  /// Local reference to Nomios.
    protected PersonAPI executive;  /// The executive at Nomios to collect from.

    /**
     * The mission stages.
     */
    public enum Stage {
        COLLECT_CRYO,
        REACH,
        RETURN_CRYO,
        RETURN_HELIOPAUSE,
        COMPLETED
    }

    public static final int XP_REWARD = 4000;
    /// The XP reward for completion.

    public static final Set<Stage> STAGE_HAS_MARKET_DESCRIPTION = Set.of(
            Stage.COLLECT_CRYO, Stage.RETURN_CRYO, Stage.RETURN_HELIOPAUSE
    );
    /// Stages with market descriptions (where do these show?)

    /**
     * Creates the event, if possible.
     *
     * @param createdAt The location the mission was created at.
     * @param barEvent  Whether this was created as a bar event (always false).
     * @return Whether the mission can be successfully created.
     */
    @Override
    protected boolean create(MarketAPI createdAt, boolean barEvent) {
        if (!setGlobalReference(getMissionMemKey() + "_ref", getMissionMemKey() + "_inProgress")) {
            return false;
        }

        // Find Nomios, and the chief counsel
        this.nomios = getMarket("nomios");
        if (this.nomios == null) return false;
        if (!this.nomios.getFactionId().equals("independent")) return false;

        this.executive = findOrCreatePerson(
                "independent", this.nomios, false,
                Ranks.EXECUTIVE, Ranks.POST_EXECUTIVE
        );
        if (this.executive == null) return false;

        // Find a black hole event horizon, preferably in an unexplored system.
        requireSystemTags(ReqMode.NOT_ANY, Tags.THEME_HIDDEN, Tags.THEME_CORE);
        requireTerrainType(ReqMode.ALL, Terrain.EVENT_HORIZON);
        preferSystemUnexplored();
        preferTerrainInDirectionOfOtherMissions();

        this.eventHorizon = pickTerrain();
        if (this.eventHorizon == null) return false;

        this.system = this.eventHorizon.getStarSystem();
        setMapMarkerNameColorBasedOnStar(this.system);

        // Set the rewards for the mission
        setRepRewardPerson(CoreReputationPlugin.RepRewards.MEDIUM);
        setRepRewardFaction(CoreReputationPlugin.RepRewards.MEDIUM);
        setXPReward(XP_REWARD);

        // Start the mission setup
        setStoryMission();
        setStartingStage(Stage.COLLECT_CRYO);
        addSuccessStages(Stage.COMPLETED);

        // Create the node for the player to interact with
        SectorEntityToken node = spawnMissionNode(new LocData(this.eventHorizon));

        // Stage importances
        makeImportant(this.nomios, getStageMemKey(Stage.COLLECT_CRYO), Stage.COLLECT_CRYO);
        makeImportant(this.executive, getStageMemKey(Stage.COLLECT_CRYO), Stage.COLLECT_CRYO);
        makeImportant(node, getStageMemKey(Stage.REACH), Stage.REACH);
        makeImportant(this.eventHorizon, getStageMemKey(Stage.REACH), Stage.REACH);
        makeImportant(this.nomios, getStageMemKey(Stage.RETURN_CRYO), Stage.RETURN_CRYO);
        makeImportant(this.executive, getStageMemKey(Stage.RETURN_CRYO), Stage.RETURN_CRYO);
        makeImportant(this.getPerson(), getStageMemKey(Stage.RETURN_HELIOPAUSE), Stage.RETURN_HELIOPAUSE);
        makeImportant(this.getPerson().getMarket(), getStageMemKey(Stage.RETURN_HELIOPAUSE), Stage.RETURN_HELIOPAUSE);

        // Set the flags that transition between stages
        connectWithGlobalFlag(Stage.COLLECT_CRYO, Stage.REACH, getStageMemKey(Stage.REACH));
        connectWithGlobalFlag(Stage.REACH, Stage.RETURN_CRYO, getStageMemKey(Stage.RETURN_CRYO));
        connectWithGlobalFlag(Stage.RETURN_CRYO, Stage.RETURN_HELIOPAUSE, getStageMemKey(Stage.RETURN_HELIOPAUSE));
        connectWithGlobalFlag(Stage.RETURN_HELIOPAUSE, Stage.COMPLETED, getStageMemKey(Stage.COMPLETED));

        // --------------------------------
        // Stage: Reach black hole, intercepted by fleet from the company
        // --------------------------------
        beginEnteredLocationTrigger(this.system, Stage.REACH);
        triggerCreateFleet(
                FleetSize.MEDIUM,
                FleetQuality.DEFAULT,
                Factions.MERCENARY,
                FleetTypes.MERC_PRIVATEER,
                system
        );

        triggerAutoAdjustFleetStrengthModerate();
        triggerAutoAdjustFleetQuality(FleetQuality.HIGHER, FleetQuality.HIGHER);
        triggerFleetMakeImportant(null, Stage.REACH);
        triggerSetFleetMemoryValue(getMissionMemKey() + "_company_fleet", true);
        triggerMakeNoRepImpact();
        triggerSpawnFleetNear(this.eventHorizon, 512f, null, null);
        triggerOrderFleetInterceptPlayer(true, true);
        triggerOrderFleetMaybeEBurn();
        endTrigger();

        beginInRangeOfEntityTrigger(this.eventHorizon, 0f, Stage.REACH);
        triggerSetGlobalMemoryValueAfterDelay(
                MathUtils.getRandomNumberInRange(0.25f, 0.5f), getStageMemKey(Stage.RETURN_CRYO), true
        );

        // --------------------------------
        // Stage: Return to Nomios, intercepted by fleet from the clients
        // --------------------------------
        beginWithinHyperspaceRangeTrigger(this.nomios, 5f, true, Stage.RETURN_CRYO);
        triggerCreateFleet(
                FleetSize.MEDIUM,
                FleetQuality.DEFAULT,
                Factions.MERCENARY,
                FleetTypes.MERC_PRIVATEER,
                system
        );

        triggerAutoAdjustFleetStrengthModerate();
        triggerAutoAdjustFleetSize(FleetSize.LARGER, FleetSize.LARGER);
        triggerFleetMakeImportant(null, Stage.REACH);
        triggerMakeNoRepImpact();
        triggerSetFleetMemoryValue(getMissionMemKey() + "_lawyers_fleet", true);
        triggerSpawnFleetNear(this.system.getHyperspaceAnchor(), 0f, null, null);
        triggerOrderFleetInterceptPlayer(true, true);
        endTrigger();

        // --------------------------------
        // Stage: Player's wrapped it all up
        // --------------------------------
        beginStageTrigger(Stage.COMPLETED);
        triggerSetGlobalMemoryValue(getMissionMemKey() + "_missionCompleted", true);
        endTrigger();

        return true;
    }

    /**
     * Adds the quest variables to the interaction dialogue memory.
     */
    protected void updateInteractionDataImpl() {
        set(getMissionMemKey() + "_stage", getCurrentStage());
        set(getMissionMemKey() + "_eventHorizon", this.eventHorizon);
        set(getMissionMemKey() + "_reward", Misc.getWithDGS(getCreditsReward()));
        set(getMissionMemKey() + "_creditsReward", getCreditsReward());
        set(getMissionMemKey() + "_xpReward", getXPReward());
    }

    /**
     * Actions callable from rules.csv.
     *
     * @param action
     * @param ruleId
     * @param dialog
     * @param params
     * @param memoryMap
     * @return
     */
    @Override
    protected boolean callAction(
            String action, String ruleId, final InteractionDialogAPI dialog,
            List<Misc.Token> params, final Map<String, MemoryAPI> memoryMap
    ) {
        return super.callAction(action, ruleId, dialog, params, memoryMap);
    }

    /**
     * Adds a description for the current stage to an info box in the intel window.
     *
     * @param info   The description box.
     * @param width  The height of the description box.
     * @param height The height of the description box.
     */
    @Override
    public void addDescriptionForCurrentStage(TooltipMakerAPI info, float width, float height) {
        addDescriptionForNonEndStage(info, width, height);
    }

    /**
     * Adds a description for the current stage to an info box in the intel window.
     * <p>
     * Not sure why this exists with the above?
     *
     * @param info   The description box.
     * @param width  The height of the description box.
     * @param height The height of the description box.
     */
    @Override
    public void addDescriptionForNonEndStage(TooltipMakerAPI info, float width, float height) {
        float opad = 10f;

        String description = Global.getSettings().getString(ID, getStageKey((Stage) this.currentStage) + "_desc");
        info.addPara(getSubstitutedText(description), opad);

        if (STAGE_HAS_MARKET_DESCRIPTION.contains((Stage) this.currentStage)) {
            description = Global.getSettings().getString(ID, "stage_" + currentStage.toString() + "_market");
            addStandardMarketDesc(getSubstitutedText(description), this.getPerson().getMarket(), info, opad);
        }
    }

    /**
     * Adds a brief description for the next step, called when the stage advances in dialog.
     *
     * @param info The info box generated.
     * @param tc   The tooltip colour.
     * @param pad  The initial padding for the box.
     * @return True if description added, else false.
     */
    @Override
    public boolean addNextStepText(TooltipMakerAPI info, Color tc, float pad) {
        if (this.currentStage == null) return false;

        Description desc = Global.getSettings().getDescription(
                getStageId((Stage) this.currentStage), Description.Type.CUSTOM
        );
        info.addPara(
                getSubstitutedText(desc.getText1()), pad,
                Misc.getHighlightColor(), getSubstitutedText(desc.getText2())
        );
        return true;
    }

    /**
     * Replaces tokens in input text with quest variables.
     *
     * @param text Text to substitute in.
     * @return The input text, with markers {{person}} and {{market}} replaced.
     */
    public String getSubstitutedText(String text) {
        return text.replace(
                "{{person}}", this.getPerson().getNameString()
        ).replace(
                "{{market}}", this.getPerson().getMarket().getName()
        );
    }

    /**
     * Gets the key used in `strings.json` for a stage (under ID:KEY).
     *
     * @param stage The stage to get a key for.
     * @return E.g. `stage_STAGENAME`.
     */
    public static String getStageKey(Stage stage) {
        return "stage_" + stage.toString();
    }

    /**
     * Gets the ID used for a stage in `descriptions.csv`.
     *
     * @param stage The stage to get a key for.
     * @return E.g. `questId_stage_STAGENAME`.
     */
    public static String getStageId(Stage stage) {
        return ID + "_" + getStageKey(stage);
    }

    /**
     * Gets a key used to refer to a stage in global memory.
     *
     * @param stage The stage to get a key for.
     * @return E.g. `$questId_stage_STAGENAME`.
     */
    public static String getStageMemKey(Stage stage) {
        return getMissionMemKey() + "_" + getStageKey(stage);
    }

    /**
     * Gets the key used to refer to the mission in global memory.
     *
     * @return E.g. `$questId`.
     */
    public static String getMissionMemKey() {
        return "$" + ID;
    }

    /**
     * Gets the mission name from strings.csv.
     *
     * @return The mission name.
     */
    @Override
    public String getBaseName() {
        return Global.getSettings().getString(ID, "name");
    }

    /**
     * Not entirely sure, I assume this is a postfix shown after a stage title? E.g. "(1/3)"?
     *
     * @return Blank string?
     */
    @Override
    public String getPostfixForState() {
        if (startingStage != null) return "";
        return super.getPostfixForState();
    }
}






