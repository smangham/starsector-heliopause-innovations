package toaster.hp.campaign.missions;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.combat.BattleCreationContext;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.DerelictShipEntityPlugin;
import com.fs.starfarer.api.impl.campaign.FleetEncounterContext;
import com.fs.starfarer.api.impl.campaign.FleetInteractionDialogPluginImpl;
import com.fs.starfarer.api.impl.campaign.RuleBasedInteractionDialogPluginImpl;
import com.fs.starfarer.api.impl.campaign.fleets.FleetParamsV3;
import com.fs.starfarer.api.impl.campaign.ghosts.BaseSensorGhostCreator;
import com.fs.starfarer.api.impl.campaign.ids.*;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.missions.hub.HubMissionWithTriggers;
import com.fs.starfarer.api.impl.campaign.procgen.themes.BaseThemeGenerator;
import com.fs.starfarer.api.impl.campaign.procgen.themes.RemnantSeededFleetManager;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.special.ShipRecoverySpecial;
import com.fs.starfarer.api.loading.Description;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import org.apache.log4j.Logger;
import org.hyperlib.HyperLibTags;
import org.magiclib.util.MagicUI;
import toaster.hp.GhostUtil;
import org.lwjgl.util.vector.Vector2f;

import java.awt.*;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import toaster.hp.campaign.ids.*;
import toaster.hp.campaign.ids.Missions;


/**
 * Mission to recover the prototype Pinnacle.
 */
public class HeliopauseRecoverPinnacle extends HubMissionWithTriggers {
    public static final String ID = Missions.HELIOPAUSE_RECOVER_PINNACLE;

    public static class PinnacleFIDConfig implements FleetInteractionDialogPluginImpl.FIDConfigGen {
        public FleetInteractionDialogPluginImpl.FIDConfig createConfig() {
            FleetInteractionDialogPluginImpl.FIDConfig config = new FleetInteractionDialogPluginImpl.FIDConfig();
//			config.alwaysAttackVsAttack = true;
			config.leaveAlwaysAvailable = true;
			config.showFleetAttitude = false;
            config.showTransponderStatus = false;
            config.showEngageText = false;
//            config.alwaysPursue = true;
            config.dismissOnLeave = false;
            //config.lootCredits = false;
            config.withSalvage = false;
            //config.showVictoryText = false;
            config.printXPToDialog = true;

            config.noSalvageLeaveOptionText = "Continue";
//			config.postLootLeaveOptionText = "Continue";
//			config.postLootLeaveHasShortcut = false;

            config.delegate = new FleetInteractionDialogPluginImpl.BaseFIDDelegate() {
                public void postPlayerSalvageGeneration(InteractionDialogAPI dialog, FleetEncounterContext context, CargoAPI salvage) {
                    new RemnantSeededFleetManager.RemnantFleetInteractionConfigGen().createConfig().delegate.
                            postPlayerSalvageGeneration(dialog, context, salvage);
                }
                public void notifyLeave(InteractionDialogAPI dialog) {
                    SectorEntityToken other = dialog.getInteractionTarget();
                    if (!(other instanceof CampaignFleetAPI)) {
                        dialog.dismiss();
                        return;
                    }
                    CampaignFleetAPI fleet = (CampaignFleetAPI) other;

                    if (!fleet.isEmpty()) {
                        dialog.dismiss();
                        return;
                    }

                    Global.getSector().getMemoryWithoutUpdate().set(getStageMemKey(Stage.RECOVER), true);

                    ShipRecoverySpecial.PerShipData ship = new ShipRecoverySpecial.PerShipData(
                            Variants.PINNACLE_MK1, ShipRecoverySpecial.ShipCondition.WRECKED, 0f
                    );
                    ship.shipName = "HPS Friedrich";
                    DerelictShipEntityPlugin.DerelictShipData params = new DerelictShipEntityPlugin.DerelictShipData(ship, false);
                    CustomCampaignEntityAPI entity = (CustomCampaignEntityAPI) BaseThemeGenerator.addSalvageEntity(
                            fleet.getContainingLocation(),
                            Entities.WRECK, Factions.NEUTRAL, params
                    );
                    Misc.makeImportant(entity, ID);

                    RepLevel church_rep_level = Global.getSector().getPlayerFleet().getFaction().getRelationshipLevel(Factions.LUDDIC_CHURCH);
                    RepLevel path_rep_level = Global.getSector().getPlayerFleet().getFaction().getRelationshipLevel(Factions.LUDDIC_PATH);
                    boolean would_have_priest = church_rep_level.isAtWorst(RepLevel.SUSPICIOUS) || path_rep_level.isAtWorst(RepLevel.SUSPICIOUS);

                    HeliopauseRecoverPinnacle mission = (HeliopauseRecoverPinnacle) Global.getSector().getMemoryWithoutUpdate().get(getMissionMemKey()+"_ref");
                    entity.getMemoryWithoutUpdate().set("$"+Variants.PINNACLE_MK1, true);
                    entity.getMemoryWithoutUpdate().set("$would_have_priest", would_have_priest);
                    entity.getMemoryWithoutUpdate().set("$giver_name", mission.getPerson().getName());

                    entity.getLocation().x = fleet.getLocation().x + (50f - (float) Math.random() * 100f);
                    entity.getLocation().y = fleet.getLocation().y + (50f - (float) Math.random() * 100f);

                    ShipRecoverySpecial.ShipRecoverySpecialData data = new ShipRecoverySpecial.ShipRecoverySpecialData(null);
                    data.notNowOptionExits = true;
                    data.noDescriptionText = true;
                    DerelictShipEntityPlugin dsep = (DerelictShipEntityPlugin) entity.getCustomPlugin();
                    ShipRecoverySpecial.PerShipData copy = (ShipRecoverySpecial.PerShipData) dsep.getData().ship.clone();
                    copy.variant = Global.getSettings().getVariant(copy.variantId).clone();
                    copy.variantId = null;
                    copy.variant.addTag(Tags.SHIP_CAN_NOT_SCUTTLE);
                    data.addShip(copy);

                    Misc.setSalvageSpecial(entity, data);

                    dialog.setInteractionTarget(entity);
                    RuleBasedInteractionDialogPluginImpl plugin = new RuleBasedInteractionDialogPluginImpl(
                            "tstHPMissionPinnacleFleetDefeated"
                    );
                    dialog.setPlugin(plugin);
                    plugin.init(dialog);
                }

                public void battleContextCreated(InteractionDialogAPI dialog, BattleCreationContext bcc) {
                    bcc.aiRetreatAllowed = false;
                    bcc.objectivesAllowed = false;
                    bcc.fightToTheLast = true;
                    bcc.enemyDeployAll = true;
                }
            };
            return config;
        }
    }

    /**
     * The mission stages.
     */
    public enum Stage {
        FIND,
        FIGHT,
        RECOVER,
        RETURN,
        COMPLETED
    }

    public static final int XP_REWARD = 7000;  /// The XP reward for completion.
    public static final Set<Stage> STAGE_HAS_MARKET_DESCRIPTION = Set.of(Stage.RETURN);  /// Stages with market descriptions (where do these show?)

    protected SectorEntityToken targetToken;  /// The target used to spawn the fleet at.

    /**
     * Creates the event, if possible.
     *
     * @param createdAt The location the mission was created at.
     * @param barEvent  Whether this was created as a bar event (always false).
     * @return  Whether the mission can be successfully created.
     */
    @Override
    protected boolean create(MarketAPI createdAt, boolean barEvent) {
        if (!setGlobalReference(getMissionMemKey()+"_ref", getMissionMemKey()+"_inProgress")) {
            return false;
        }

        setRepPersonChangesHigh();
        setXPReward(XP_REWARD);
        setCreditReward(CreditReward.HIGH);

        Vector2f targetPoint = BaseSensorGhostCreator.findDeepHyperspaceArea(
                new Vector2f(-getUnits(20f), getUnits(15f)),
                0f, getUnits(8f), getUnits(1f), new Random()
        );
        if (targetPoint == null) return false;

//        targetPoint = new Vector2f(getPerson().getMarket().getStarSystem().getHyperspaceAnchor().getLocation());

        this.targetToken = Global.getSector().getHyperspace().createToken(targetPoint.x, targetPoint.y);
        this.targetToken.getMemoryWithoutUpdate().set(getMissionMemKey()+"_token", true);

        setStoryMission();
        setStartingStage(Stage.FIND);
        addSuccessStages(Stage.COMPLETED);

        // Stage importances
        makeImportant(this.getPerson(), getStageMemKey(Stage.RETURN), Stage.RETURN);
        makeImportant(this.getPerson().getMarket(), getStageMemKey(Stage.RETURN), Stage.RETURN);
        makeImportant(this.targetToken, getStageMemKey(Stage.FIND), Stage.FIND);

        // Set the flags that transition between stages
        connectWithGlobalFlag(Stage.FIND, Stage.FIGHT, getStageMemKey(Stage.FIGHT));
        connectWithGlobalFlag(Stage.FIGHT, Stage.RECOVER, getStageMemKey(Stage.RECOVER));
        connectWithGlobalFlag(Stage.RECOVER, Stage.RETURN, getStageMemKey(Stage.RETURN));
        setStageOnGlobalFlag(Stage.COMPLETED, getStageMemKey(Stage.COMPLETED));

        // --------------------------------
        // Stage: Find, spawn the fleet when nearby.
        // --------------------------------
        beginWithinHyperspaceRangeTrigger(
                this.targetToken,
                3f,
                true,
                Stage.FIND
        );
        triggerPickLocationAroundEntity(this.targetToken, 0f);

        triggerCreateFleet(
                FleetSize.MEDIUM,
                FleetQuality.DEFAULT,
                HPFactions.POSSESSED,
                FleetTypes.PATROL_MEDIUM,
                this.targetToken
        );

        triggerFleetSetFlagship(Variants.PINNACLE_BOSS);
        triggerFleetSetCommander(GhostUtil.getGhostCaptain());
        triggerFleetSetAllWeapons();
        triggerFleetSetNoFactionInName();

        triggerFleetSetPatrolActionText("Unknown");

        triggerFleetNoAutoDespawn();
        triggerFleetMakeImportant(null, Stage.FIND);
        triggerFleetMakeImportant(null, Stage.FIGHT);
        triggerMakeFleetIgnoreOtherFleets();
        triggerMakeFleetIgnoredByOtherFleets();
        triggerMakeAllFleetFlagsPermanent();

        triggerSetFleetFlag(MemFlags.MEMORY_KEY_NO_REP_IMPACT);
        triggerSetFleetFlag(MemFlags.MEMORY_KEY_NO_SHIP_RECOVERY);
        triggerSetFleetFlag(MemFlags.MEMORY_KEY_MAKE_AGGRESSIVE);
        triggerSetFleetFlag(MemFlags.MEMORY_KEY_DO_NOT_SHOW_FLEET_DESC);
        triggerSetFleetMemoryValue(MemFlags.FLEET_INTERACTION_DIALOG_CONFIG_OVERRIDE_GEN, new PinnacleFIDConfig());
        triggerSetFleetMemoryValue("$"+HyperLibTags.HYPERSPACE_STORM_STRIKE_IMMUNE, true);
        triggerDoNotShowFleetDesc();
        triggerSpawnFleetAtPickedLocation(getMissionMemKey()+"_fleet",null);
        endTrigger();

        // --------------------------------
        // Stage: Return with salvaged ship.
        // --------------------------------
        beginStageTrigger(Stage.RETURN);
        triggerDespawnEntity(this.targetToken);
        endTrigger();

        return true;
    }

    /**
     * Adds the quest variables to the interaction dialogue memory.
     */
    protected void updateInteractionDataImpl() {
        set(getMissionMemKey()+"_stage", getCurrentStage());
        set(getMissionMemKey()+"_token", this.targetToken);
        set(getMissionMemKey()+"_reward", Misc.getWithDGS(getCreditsReward()));
    }

    /**
     * Actions callable from rules.csv. None used.
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
     * @param info      The description box.
     * @param width     The height of the description box.
     * @param height    The height of the description box.
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
     * @param info      The description box.
     * @param width     The height of the description box.
     * @param height    The height of the description box.
     */
    @Override
    public void addDescriptionForNonEndStage(TooltipMakerAPI info, float width, float height) {
        float opad = 10f;

        String description = Global.getSettings().getString(ID, getStageKey((Stage) this.currentStage)+"_desc");
        info.addPara(getSubstitutedText(description), opad);

        if (STAGE_HAS_MARKET_DESCRIPTION.contains((Stage) this.currentStage)) {
            description = Global.getSettings().getString(ID, "stage_"+currentStage.toString()+"_market");
            addStandardMarketDesc(getSubstitutedText(description), this.getPerson().getMarket(), info, opad);
        }
    }

    /**
     * Adds a brief description for the next step, called when the stage advances in dialog.
     *
     * @param info  The info box generated.
     * @param tc    The tooltip colour.
     * @param pad   The initial padding for the box.
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
     * @param text  Text to substitute in.
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
    public static String getStageKey(Stage stage) { return "stage_"+stage.toString(); }

    /**
     * Gets the ID used for a stage in `descriptions.csv`.
     *
     * @param stage The stage to get a key for.
     * @return E.g. `questId_stage_STAGENAME`.
     */
    public static String getStageId(Stage stage) { return ID+"_"+getStageKey(stage); }

    /**
     * Gets a key used to refer to a stage in global memory.
     *
     * @param stage The stage to get a key for.
     * @return E.g. `$questId_stage_STAGENAME`.
     */
    public static String getStageMemKey(Stage stage) { return getMissionMemKey() + "_" + getStageKey(stage); }

    /**
     * Gets the key used to refer to the mission in global memory.
     *
     * @return E.g. `$questId`.
     */
    public static String getMissionMemKey() { return "$" + ID; }

    /**
     * Gets the mission name from strings.csv.
     *
     * @return      The mission name.
     */
    @Override
    public String getBaseName() { return Global.getSettings().getString(ID, "name"); }

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






