package toaster.hp.campaign.rulecmd;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.impl.campaign.CoreReputationPlugin;
import com.fs.starfarer.api.impl.campaign.ids.Items;
import com.fs.starfarer.api.impl.campaign.ids.Strings;
import com.fs.starfarer.api.impl.campaign.rulecmd.AddRemoveCommodity;
import com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin;
import com.fs.starfarer.api.impl.campaign.rulecmd.FireBest;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.campaign.rules.MemKeys;
import org.apache.log4j.Logger;
import toaster.hp.campaign.ids.Events;
import toaster.hp.campaign.intel.events.HeliopauseEventFactorBulk;
import toaster.hp.campaign.intel.events.HeliopauseEventFactorUnique;
import toaster.hp.campaign.intel.events.HeliopauseEventIntel;


/**
 * For command tasks from tstHP mod.
 * <p>
 * Usage: tstHP_Heliopause <action> <parameters>
 */
public class tstHP_Heliopause extends BaseCommandPlugin {
    public static final String ID = "tstHP_Heliopause";

    public static final float VALUE_TO_REP_MULT = 0.0001f;
    /// So a 50k value hyperspace topology = 5 rep
    public static final float VALUE_TO_PROGRESS_MULT = 0.0005f;
    /// So a 50k value hyperspace topology = 25 progress

    public static final String ADD_DATA = "addData";
    /// Command for adding one-off data donations
    public static final String CAN_SELL_DATA = "canSellData";
    /// Command to see if the player can bulk sell data
    public static final String SELL_DATA = "sellData";
    /// Command to actually bulk-sell data
    public static final String NEXT_RULE = "toasterHPCEOSoldData";
    /// The rule triggered once the sell window shuts
    public static final String BOUNTY_TAG = "$bountyValue";
    /// The tag used to store the value of items sold

//    protected static final Logger log = Global.getLogger(tstHP_Heliopause.class);

    protected CampaignFleetAPI playerFleet;
    protected SectorEntityToken entity;
    protected FactionAPI playerFaction;
    protected FactionAPI entityFaction;
    protected TextPanelAPI textPanel;
    protected OptionPanelAPI options;
    protected CargoAPI playerCargo;
    protected MemoryAPI memory;
    protected InteractionDialogAPI dialog;
    protected Map<String, MemoryAPI> memoryMap;
    protected PersonAPI person;
    protected FactionAPI personFaction;

    /**
     * Called when "tstHP_Heliopause" is encountered in rules.csv.
     *
     * @param ruleId    Should just be "tstHP_Heliopause".
     * @param dialog    The interaction dialog currently used.
     * @param params    The list of parameters.
     * @param memoryMap A map to the memory parameters are stored in.
     * @return True if successful, false if not.
     */
    public boolean execute(
            String ruleId, InteractionDialogAPI dialog, List<Misc.Token> params, Map<String, MemoryAPI> memoryMap
    ) {
        if (dialog == null) return false;
        this.dialog = dialog;
        this.memoryMap = memoryMap;
        this.memory = memoryMap.get(MemKeys.LOCAL);
        if (this.memory == null) return false; // should not be possible unless there are other big problems already

        this.options = dialog.getOptionPanel();
        this.textPanel = dialog.getTextPanel();
        this.playerFleet = Global.getSector().getPlayerFleet();
        this.playerCargo = playerFleet.getCargo();

        switch (params.get(0).getString(memoryMap)) {
            case ADD_DATA:
                HeliopauseEventIntel.addFactorCreateIfNecessary(
                        new HeliopauseEventFactorUnique(
                                params.get(1).getInt(memoryMap),
                                params.get(2).getString(memoryMap)
                        ),
                        dialog
                );
                return true;

            case SELL_DATA:
                this.person = dialog.getInteractionTarget().getActivePerson();
                this.personFaction = this.person.getFaction();
                selectSellableItems();
                return true;

            case CAN_SELL_DATA:
                return playerHasSellableItems();

            default:
                this.textPanel.addPara(
                        "ERROR: Command '" + params.get(0).getString(memoryMap) + "' is not valid."
                );
        }
        return false;
    }

    /**
     *
     */
    protected void selectSellableItems() {
        CargoAPI copy = getSellableItems();

        final float width = 310f;
        dialog.showCargoPickerDialog(
                "Select items to turn in", "Confirm", "Cancel", true, width, copy,
                new CargoPickerListener() {
                    public void pickedCargo(CargoAPI cargo) {
                        if (cargo.isEmpty()) {
                            cancelledCargoSelection();
                            return;
                        }
                        cargo.sort();

                        float bounty = 0f;

                        for (CargoStackAPI stack : cargo.getStacksCopy()) {
                            playerCargo.removeItems(stack.getType(), stack.getData(), stack.getSize());
                            AddRemoveCommodity.addStackLossText(stack, textPanel);
                            bounty += getStackValue(stack);
                        }

                        if (bounty > 0f) {
                            playerCargo.getCredits().add(bounty);
                            AddRemoveCommodity.addCreditsGainText((int) bounty, textPanel);

                            CoreReputationPlugin.CustomRepImpact impact = new CoreReputationPlugin.CustomRepImpact();
                            impact.delta = bounty * VALUE_TO_REP_MULT * 0.01f;
                            Global.getSector().adjustPlayerReputation(
                                    new CoreReputationPlugin.RepActionEnvelope(
                                            CoreReputationPlugin.RepActions.CUSTOM, impact,
                                            null, textPanel, true
                                    ),
                                    personFaction.getId()
                            );
                            Global.getSector().adjustPlayerReputation(
                                    new CoreReputationPlugin.RepActionEnvelope(
                                            CoreReputationPlugin.RepActions.CUSTOM, impact,
                                            null, textPanel, true
                                    ),
                                    person
                            );
                            HeliopauseEventIntel.addFactorCreateIfNecessary(
                                    new HeliopauseEventFactorBulk(
                                            (int) (bounty * VALUE_TO_PROGRESS_MULT)
                                    ),
                                    dialog
                            );
                        }
                        person.getMemoryWithoutUpdate().set(BOUNTY_TAG, bounty, 1);
                        FireBest.fire(null, dialog, memoryMap, NEXT_RULE);
                    }

                    public void cancelledCargoSelection() {
                    }

                    public void recreateTextPanel(
                            TooltipMakerAPI panel, CargoAPI cargo, CargoStackAPI pickedUp,
                            boolean pickedUpFromSource, CargoAPI combined
                    ) {
                        float bounty = 0f;
                        for (CargoStackAPI stack : combined.getStacksCopy()) {
                            bounty += stack.getSize() * stack.getBaseValuePerUnit();
                        }
                        float pad = 3f;
                        float small = 5f;
                        float opad = 10f;

                        panel.setParaFontOrbitron();
                        panel.addPara(Misc.ucFirst(personFaction.getDisplayName()), personFaction.getBaseUIColor(), 1f);
                        //panel.addTitle(Misc.ucFirst(faction.getDisplayName()), faction.getBaseUIColor());
                        //panel.addPara(faction.getDisplayNameLong(), faction.getBaseUIColor(), opad);
                        //panel.addPara(faction.getDisplayName() + " (" + entity.getMarket().getName() + ")", faction.getBaseUIColor(), opad);
                        panel.setParaFontDefault();
                        panel.addImage(personFaction.getLogo(), width * 1f, 3f);
                        panel.addPara(
                                "If you turn in the selected items, you will receive a %s bounty, " +
                                        "progress the %s event by %s, " +
                                        "and improve your standing with " + personFaction.getDisplayName() + " by %s points.",
                                opad * 1f, Misc.getHighlightColor(),
                                Misc.getWithDGS(bounty) + Strings.C,
                                Global.getSettings().getString(Events.HELIOPAUSE, "name"),
                                "" + (int) (bounty * VALUE_TO_PROGRESS_MULT),
                                "" + (int) (bounty * VALUE_TO_REP_MULT)
                        );
                    }
                }
        );
    }

    /**
     * Convenience method for getting the value of a stack of items.
     *
     * @param stack A stack of items
     * @return The net sales value of the stack.
     */
    public static float getStackValue(CargoStackAPI stack) {
        return stack.getBaseValuePerUnit() * stack.getSize();
    }

    /**
     * Counts the number of items in the stacks of sellable items.
     *
     * @return True if the player has any sellable items.
     */
    protected boolean playerHasSellableItems() {
        int stacks = 0;
//        log.info(ID+" - stacks: " + getSellableItems().getStacksCopy());
        for (CargoStackAPI stack : getSellableItems().getStacksCopy()) {
//            log.info(ID+" - stack: " + stack.getDisplayName() +", size: " + stack.getSize()+", total: "+stacks);
            stacks += (int) stack.getSize();
        }
        return stacks > 0;
    }

    /**
     * Checks whether a stack of items is survey data
     *
     * @param stack A stack of items
     * @return True if the stack is survey data
     */
    public static boolean isSurveyStack(CargoStackAPI stack) {
        return stack.isCommodityStack() && Objects.equals(stack.getResourceIfResource().getDemandClass(), "survey_data");
    }

    /**
     * Checks whether a stack of items is topographic data
     *
     * @param stack A stack of items
     * @return True if the stack is topographic data
     */
    public static boolean isTopographicStack(CargoStackAPI stack) {
        return stack.isSpecialStack() && Objects.equals(
                stack.getSpecialItemSpecIfSpecial().getId(), Items.TOPOGRAPHIC_DATA
        );
    }

    /**
     * Filters the player's inventory to only sellable items
     *
     * @return A CargoAPI containing only sellable items.
     */
    protected CargoAPI getSellableItems() {
        CargoAPI copy = Global.getFactory().createCargo(false);
        for (CargoStackAPI stack : playerFleet.getCargo().getStacksCopy()) {
            boolean match = isSurveyStack(stack) || isTopographicStack(stack);
            if (match) {
                copy.addFromStack(stack);
            }
        }
        copy.sort();
        return copy;
    }
}
