package toaster.hp.campaign.intel.events;

import java.util.*;

import java.awt.Color;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BattleAPI;
import com.fs.starfarer.api.campaign.CampaignEventListener.FleetDespawnReason;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.PersistentUIDataAPI.AbilitySlotAPI;
import com.fs.starfarer.api.campaign.PersistentUIDataAPI.AbilitySlotsAPI;
import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.campaign.listeners.CharacterStatsRefreshListener;
import com.fs.starfarer.api.campaign.listeners.CurrentLocationChangedListener;
import com.fs.starfarer.api.campaign.listeners.FleetEventListener;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.intel.events.BaseEventIntel;
import com.fs.starfarer.api.impl.campaign.intel.events.BaseFactorTooltip;
import com.fs.starfarer.api.impl.campaign.intel.events.EventFactor;
import com.fs.starfarer.api.impl.campaign.rulecmd.AddAbility;
import com.fs.starfarer.api.loading.Description;
import com.fs.starfarer.api.ui.SectorMapAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI.TooltipCreator;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.Misc.Token;
import com.fs.starfarer.api.util.Misc.TokenType;
import toaster.hp.campaign.ids.Abilities;
import toaster.hp.campaign.ids.Events;
import toaster.hp.campaign.ids.People;


/**
 *
 */
public class HeliopauseEventIntel extends BaseEventIntel implements FleetEventListener,
        CharacterStatsRefreshListener,
        CurrentLocationChangedListener {

    public static Color BAR_COLOR = Global.getSettings().getColor("progressBarFleetPointsColor");

    public static final int PROGRESS_MAX = 200;
    public static final int PROGRESS_1 = 75;
    public static final int PROGRESS_2 = 150;
    public static final int PROGRESS_3 = 400;
    public static final int PROGRESS_4 = 550;
    public static final int PROGRESS_5 = 700;

    public static float BASE_DETECTION_RANGE_LY = 3f;
    public static float RANGE_WITHIN_WHICH_SENSOR_ARRAYS_HELP_LY = 5f;
    public static float RANGE_PER_DOMAIN_SENSOR_ARRAY = 2f;
    public static float RANGE_PER_MAKESHIFT_SENSOR_ARRAY = 1f;
    public static int MAX_SENSOR_ARRAYS = 3;
    public static float WAYSTATION_BONUS = 2f;

    public static float SLIPSTREAM_FUEL_MULT = 0.25f;
    public static float HYPER_BURN_BONUS = 3f;

    protected static String ID = Events.HELIOPAUSE;
    protected static Set<String> TAGS = Set.of(Tags.INTEL_EXPLORATION);

    /**
     * List of the stages.
     */
    public enum Stage {
        START,
        MISSIONS,
        /// Unlocks missions for Heliopause
        STORMCALLER,
        /// Ability to use the Pinnacle to generate storms.
        MAX             /// The maximum value.
    }

    /**
     * Map of stages that add abilities, and the ability they add.
     * (Java's declaration of constant dicts is weird, don't like it)
     */
    protected static Map<Stage, String> STAGE_ABILITIES_MAP = Map.of(
            Stage.STORMCALLER, Abilities.STORMCALLER
    );

    /**
     * Adds a factor to the progress bar. If the event doesn't exist, creates it first.
     *
     * @param factor The factor contributing.
     * @param dialog The dialog that created it.
     */
    public static void addFactorCreateIfNecessary(EventFactor factor, InteractionDialogAPI dialog) {
        if (get() == null) {
            //TextPanelAPI text = dialog == null ? null : dialog.getTextPanel();
            //new HyperspaceTopographyEventIntel(text);
            // adding a factor anyway, so it'll show a message - don't need to double up
            new HeliopauseEventIntel(null, false);
        }
        if (get() != null) {
            get().addFactor(factor, dialog);
        }
    }

    /**
     * Singleton getter for this; fetches it from memory using the tag.
     * <p>
     * Uses the event ID as the tag, just prepends `$`.
     *
     * @return The singleton intel event.
     */
    public static HeliopauseEventIntel get() {
        return (HeliopauseEventIntel) Global.getSector().getMemoryWithoutUpdate().get("$" + ID);
    }

    /**
     * Constructor for the class, that can possibly send an intel notification.
     *
     * @param text                  The text that should be used for a nodification.
     * @param withIntelNotification If true, sends a notification with the text.
     */
    public HeliopauseEventIntel(TextPanelAPI text, boolean withIntelNotification) {
        super();
        Global.getSector().getMemoryWithoutUpdate().set("$" + ID, this);
        setup();
        // now that the event is fully constructed, add it and send notification
        Global.getSector().getIntelManager().addIntel(this, !withIntelNotification, text);
    }

    /**
     * Sets up the event stages.
     */
    protected void setup() {
        factors.clear();
        stages.clear();

        setMaxProgress(PROGRESS_MAX);
        addStage(Stage.START, 0);
        addStage(Stage.MISSIONS, PROGRESS_1, StageIconSize.MEDIUM);
        addStage(Stage.STORMCALLER, PROGRESS_2, StageIconSize.MEDIUM);
        addStage(Stage.MAX, PROGRESS_MAX, true, StageIconSize.SMALL);
    }

    /**
     * Loads the object? Defining it if it's not set up.
     *
     * @return The generated IntelEvent.
     */
    protected Object readResolve() {
        if (getDataFor(Stage.START) == null) {
            setup();
        }
        return this;
    }

    /**
     * Called before the intel event gets unhooked from everything.
     */
    @Override
    protected void notifyEnding() {
        super.notifyEnding();
    }

    /**
     * Called to unhook the intel event from everything once complete.
     */
    @Override
    protected void notifyEnded() {
        super.notifyEnded();
        Global.getSector().getMemoryWithoutUpdate().unset("$" + ID);
    }

    /**
     * Displays a short, bullet-point description.
     *
     * @param info     The UI component to add the descripition to.
     * @param mode     The component mode.
     * @param isUpdate
     * @param tc
     * @param initPad  The initial padding for the bullets.
     */
    protected void addBulletPoints(
            TooltipMakerAPI info, ListInfoMode mode, boolean isUpdate, Color tc, float initPad
    ) {
        if (addEventFactorBulletPoints(info, mode, isUpdate, tc, initPad)) return;

        if (isUpdate && getListInfoParam() instanceof EventStageData esd) {
            info.addPara(
                    Global.getSettings().getDescription(getIdForESD(esd), Description.Type.CUSTOM).getText1FirstPara(),
                    tc, initPad
            );

//            if (esd.id == com.fs.starfarer.api.impl.campaign.intel.events.ht.HyperspaceTopographyEventIntel.Stage.SLIPSTREAM_NAVIGATION) {
////				info.addPara("Fuel use while traversing slipstreams multiplied by %s", initPad, tc,
////						h, "" + SLIPSTREAM_FUEL_MULT + Strings.X);
//                info.addPara("Fuel use while traversing slipstreams reduced by %s", initPad, tc,
//                        h, "" + (int)Math.round((1f - SLIPSTREAM_FUEL_MULT) * 100f) + "%");
//            }
//            if (esd.id == com.fs.starfarer.api.impl.campaign.intel.events.ht.HyperspaceTopographyEventIntel.Stage.HYPERFIELD_OPTIMIZATION) {
//                info.addPara("Maximum burn increased by %s while in hyperspace", initPad, tc,
//                        h, "" + (int) HYPER_BURN_BONUS);
//            }
//            if (esd.id == com.fs.starfarer.api.impl.campaign.intel.events.ht.HyperspaceTopographyEventIntel.Stage.REVERSE_POLARITY) {
//                info.addPara("%s ability unlocked", initPad, tc, h, "Reverse Polarity");
//            }
//            if (esd.id == com.fs.starfarer.api.impl.campaign.intel.events.ht.HyperspaceTopographyEventIntel.Stage.GENERATE_SLIPSURGE) {
//                info.addPara("%s ability unlocked", initPad, tc, h, "Generate Slipsurge");
//            }
            return;
        }
    }

    /**
     * Turns stage data into a key used for string/sprite lookup.
     *
     * @param esd Event stage data.
     * @return The 'effective' name of it, "stage_<ENUM NAME>", used in `settings.json`.
     */
    protected String getStageKeyForESD(EventStageData esd) {
        return "stage_" + ((Stage) esd.id).name();
    }

    /**
     * Turns stage data into an ID that can be used for string lookup.
     *
     * @param esd Event stage data.
     * @return The 'effective' name of it, "ID_stage_<ENUM_NAME>", used in `strings.csv`.
     */
    protected String getIdForESD(EventStageData esd) {
        return ID + "_" + getStageKeyForESD(esd);
    }

    /**
     * Gets the size of the icons used for the intel tracker.
     *
     * @param stageId The Stage enum for the event.
     * @return The size, in pixels, of the icon for that stage.
     * @see this.getImageSizeForStageDesc
     */
    public float getImageSizeForStageDesc(Object stageId) {
        if (stageId == Stage.START) return 64f;
        return 48f;
    }

    /**
     * Gets the offset of the icons used for the intel tracker.
     *
     * @param stageId The Stage enum for the event.
     * @return The offset, in pixels, of the icon for that stage.
     * @see this.getImageSizeForStageDesc
     */
    public float getImageIndentForStageDesc(Object stageId) {
        if (stageId == Stage.START) return 0f;
        return 16f;
    }

    /**
     * Adds descriptive text to the tooltip mouseover of a stage.
     *
     * @param info    The tooltip box.
     * @param width   The width of the tooltip box.
     * @param stageId The stage being mouse-overed.
     */
    @Override
    public void addStageDescriptionText(TooltipMakerAPI info, float width, Object stageId) {
        float opad = 10f;
        float small = 0f;
        Color h = Misc.getHighlightColor();

        EventStageData esd = getDataFor(stageId);
        if (esd == null) return;

        if (isStageActive(stageId)) {
            addStageDesc(info, stageId, small, false);
        }
    }

    /**
     * Generates a description for this stage of the event.
     *
     * @param info       The info panel to add to.
     * @param stageId    The stage the description is for.
     * @param initPad    ???
     * @param forTooltip Is this a tooltip or full-screen description.
     */
    public void addStageDesc(
            TooltipMakerAPI info, Object stageId, float initPad, boolean forTooltip
    ) {
        EventStageData esd = getDataFor(stageId);
        Description desc = Global.getSettings().getDescription(getIdForESD(esd), Description.Type.CUSTOM);
        float opad = 10f;

        info.addPara(desc.getText1(), initPad, Misc.getHighlightColor(), desc.getText2());
    }

    /**
     * Creates the tooltip for a stage.
     *
     * @param stageId The ID of the stage reached.
     * @return The created tooltip.
     */
    public TooltipCreator getStageTooltipImpl(Object stageId) {
        final EventStageData esd = getDataFor(stageId);

        if (esd != null && esd.id != Stage.START) {
            return new BaseFactorTooltip() {
                @Override
                public void createTooltip(TooltipMakerAPI tooltip, boolean expanded, Object tooltipParam) {
                    float opad = 10f;
                    tooltip.addTitle(Global.getSettings().getString(ID, getStageKeyForESD(esd) + "_name"));
                    addStageDesc(tooltip, esd.id, opad, true);
                    esd.addProgressReq(tooltip, opad);
                }
            };
        }
        return null;
    }

    /**
     * Gets the icon for this event.
     *
     * @return The icon for the whole event.
     */
    @Override
    public String getIcon() {
        return Global.getSettings().getSpriteName(ID, "icon");
    }

    /**
     * Gets the icon for a stage of this event.
     * <p>
     * If the stage adds an ability, uses that ability's icon.
     *
     * @param stageId The ID of the stage.
     * @return The name of the sprite.
     */
    protected String getStageIconImpl(Object stageId) {
        EventStageData esd = getDataFor(stageId);
        if (esd == null) return null;

        if (STAGE_ABILITIES_MAP.containsKey((Stage) esd.id)) {
            return Global.getSettings().getAbilitySpec(STAGE_ABILITIES_MAP.get((Stage) esd.id)).getIconName();
        }
        return Global.getSettings().getSpriteName(ID, getStageKeyForESD(esd));
    }

    @Override
    public Color getBarColor() {
        Color color = BAR_COLOR;
        //color = Misc.getBasePlayerColor();
        color = Misc.interpolateColor(color, Color.black, 0.25f);
        return color;
    }

    @Override
    public Color getBarProgressIndicatorColor() {
        return super.getBarProgressIndicatorColor();
    }

    @Override
    protected int getStageImportance(Object stageId) {
        return super.getStageImportance(stageId);
    }

    @Override
    protected String getName() {
        return Global.getSettings().getString(ID, "name");
    }

    public void reportFleetDespawnedToListener(CampaignFleetAPI fleet, FleetDespawnReason reason, Object param) {
    }

    public void reportBattleOccurred(CampaignFleetAPI fleet, CampaignFleetAPI primaryWinner, BattleAPI battle) {
//		if (isEnded() || isEnding()) return;
//
//		if (!battle.isPlayerInvolved()) return;

//		HAShipsDestroyedFactor factor = new HAShipsDestroyedFactor(-1 * points);
//		sendUpdateIfPlayerHasIntel(factor, false);
//		addFactor(factor);
    }

    public int getResetProgressMin() {
        return getDataFor(Stage.STORMCALLER).progress;
    }

    public int getResetProgressMax() {
        return getResetProgressMin() + 10;
    }

    public void resetProgress() {
        int resetProgress = getResetProgressMin() + getRandom().nextInt(getResetProgressMax() - getResetProgressMin() + 1);
        setProgress(resetProgress);
    }

    /**
     * Sets the tags this event has on the Intel window.
     *
     * @param map The map view.
     * @return The tags this event sits under.
     */
    @Override
    public Set<String> getIntelTags(SectorMapAPI map) {
        Set<String> tags = super.getIntelTags(map);
        tags.addAll(TAGS);
        return tags;
    }

    /**
     * Everyframe script that applies the effects of this event.
     *
     * @param amount The amount of time passed (in days?).
     */
    @Override
    protected void advanceImpl(float amount) {
        super.advanceImpl(amount);
        applyFleetEffects();
        // Currently unused - not tracking recent events.
        // float days = Global.getSector().getClock().convertToDays(amount);
    }

    /**
     * Internal function to add an ability and put it on the bar if possible.
     *
     * @param id The ID of the ability.
     */
    public void addAbility(String id) {
        if (Global.getSector().getPlayerFleet().hasAbility(id)) return;

        List<Token> params = new ArrayList<Token>();
        Token t = new Token(id, TokenType.LITERAL);
        params.add(t);
        t = new Token("-1", TokenType.LITERAL);
        params.add(t); // don't want to assign it to a slot - will assign as hyper-only alternate later here
        new AddAbility().execute(null, null, params, null);

        AbilitySlotsAPI slots = Global.getSector().getUIData().getAbilitySlotsAPI();
        int curr = slots.getCurrBarIndex();
        OUTER:
        for (int i = 0; i < 5; i++) {
            slots.setCurrBarIndex(i);
            for (AbilitySlotAPI slot : slots.getCurrSlotsCopy()) {
                if (Abilities.STORMCALLER.equals(id) && Abilities.STORMCALLER.equals(slot.getAbilityId())) {
                    slot.setInHyperAbilityId(Abilities.STORMCALLER);
                    break OUTER;
                }
            }
        }
        slots.setCurrBarIndex(curr);
    }

    /**
     * Called when the event progress hits a stage.
     * <p>
     * Executes the effect of hitting that stage.
     *
     * @param esd The event stage reached.
     */
    @Override
    protected void notifyStageReached(EventStageData esd) {
        Stage stage = (Stage) esd.id;

        //applyFleetEffects();
        if (STAGE_ABILITIES_MAP.containsKey(stage)) {
            addAbility(STAGE_ABILITIES_MAP.get(stage));

        } else if (stage == Stage.MAX) {
            resetProgress();
        } else if (stage == Stage.MISSIONS) {
            Global.getSector().getImportantPeople().getPerson(People.HELIOPAUSE_CEO).getMemoryWithoutUpdate().set(
                    "$hasStageMissions", true
            );
        }
    }

    public void reportCurrentLocationChanged(LocationAPI prev, LocationAPI curr) {
        //applyFleetEffects();
    }

    public void reportAboutToRefreshCharacterStatEffects() {
    }

    public void reportRefreshedCharacterStatEffects() {
        // called when opening colony screen, so the Spaceport tooltip gets the right values
        applyFleetEffects();
    }

    public void applyFleetEffects() {
//        String id1 = "hypertopology1";

//        CampaignFleetAPI pf = Global.getSector().getPlayerFleet();
//        pf.getStats().getFleetwideMaxBurnMod().unmodifyFlat(id1);

//        MutableStat stat = pf.getStats().getDynamic().getStat(Stats.FUEL_USE_NOT_SHOWN_ON_MAP_MULT);
//        stat.unmodifyMult(id1);

        //if (pf.isInHyperspace()) { // doesn't work; after reportCurrentLocationChanged()
        // the current location is right but the player fleet hasn't been added to it yet
//        if (Global.getSector().getCurrentLocation().isHyperspace()) {
//            if (isStageActive(Stage.SLIPSTREAM_NAVIGATION)) {
//                for (StatMod mod : stat.getMultMods().values()) {
//                    if (SlipstreamTerrainPlugin2.FUEL_USE_MODIFIER_DESC.equals(mod.desc)) {
//                        stat.modifyMult(id1, SLIPSTREAM_FUEL_MULT,
//                                SlipstreamTerrainPlugin2.FUEL_USE_MODIFIER_DESC + " (hyperspace topography)");
//                        break;
//                    }
//                }
//            }
//
//            if (isStageActive(Stage.HYPERFIELD_OPTIMIZATION)) {
//                pf.getStats().getFleetwideMaxBurnMod().modifyFlat(id1, HYPER_BURN_BONUS, "Hyperspace topography");
//            }
//
//        }
    }

//    public void updateMarketDetectionRanges() {
//        if (isStageActive(Stage.SLIPSTREAM_DETECTION)) {
//            String id1 = "hypertopology1";
//            String id2 = "hypertopology2";
//            String id3 = "hypertopology3";
//            String id4 = "hypertopology4";
//            for (MarketAPI market : Global.getSector().getEconomy().getMarketsCopy()) {
//                if (market.isHidden()) continue;
//
//                boolean unapplicable = false;
//                Industry spaceport = market.getIndustry(Industries.SPACEPORT);
//                if (spaceport == null) {
//                    spaceport = market.getIndustry(Industries.MEGAPORT);
//                }
//                if (spaceport == null || !spaceport.isFunctional()) {
//                    unapplicable = true;
//                }
//
//                StatBonus mod = market.getStats().getDynamic().getMod(Stats.SLIPSTREAM_REVEAL_RANGE_LY_MOD);
//                if (!market.isPlayerOwned() || unapplicable) {
//                    mod.unmodify(id1);
//                    mod.unmodify(id2);
//                    mod.unmodify(id3);
//                    mod.unmodify(id4);
//                    continue;
//                }
//
//                mod.modifyFlat(id1, BASE_DETECTION_RANGE_LY, "Base detection range");
//                mod.modifyFlat(id2, market.getSize(), "Colony size");
//
//                float arraysBonus = gerSensorArrayBonusFor(market, RANGE_WITHIN_WHICH_SENSOR_ARRAYS_HELP_LY);
//
//                mod.modifyFlatAlways(id3, arraysBonus,
//                        "Claimed sensor arrays within " + (int) RANGE_WITHIN_WHICH_SENSOR_ARRAYS_HELP_LY +
//                                " ly (max: " + (int) MAX_SENSOR_ARRAYS + " arrays)");
//            }
//        }
//    }

//    public float gerSensorArrayBonusFor(MarketAPI market, float range) {
//        int countDomain = 0;
//        int countMakeshift= 0;
//        Vector2f locInHyper = market.getLocationInHyperspace();
//        for (StarSystemAPI system : Global.getSector().getStarSystems()) {
//            float dist = Misc.getDistanceLY(locInHyper, system.getLocation());
//            if (dist > range && Math.round(dist * 10f) <= range * 10f) {
//                dist = range;
//            }
//            if (dist <= range) {
//                for (SectorEntityToken entity : system.getEntitiesWithTag(Tags.SENSOR_ARRAY)) {
//                    if (entity.getFaction() != null && entity.getFaction().isPlayerFaction()) {
//                        if (entity.hasTag(Tags.MAKESHIFT)) {
//                            countMakeshift++;
//                        } else {
//                            countDomain++;
//                        }
//                    }
//                }
//            }
//        }
//
//        float bonus = Math.min(countDomain, MAX_SENSOR_ARRAYS) * RANGE_PER_DOMAIN_SENSOR_ARRAY;
//        float useMakeshift = Math.min(MAX_SENSOR_ARRAYS - countDomain, countMakeshift);
//        if (useMakeshift < 0) useMakeshift = 0;
//        bonus += useMakeshift * RANGE_PER_MAKESHIFT_SENSOR_ARRAY;
//        //bonus += Math.min(Math.max(0, countMakeshift - countDomain), MAX_SENSOR_ARRAYS) * RANGE_PER_MAKESHIFT_SENSOR_ARRAY;
//
//        return bonus;
//    }

    /**
     * Does this event have monthly progress? (No)
     *
     * @return Always false.
     */
    public boolean withMonthlyFactors() {
        return false;
    }

    /**
     * Selects which sound, if any, to play when a given stage is reached.
     * <p>
     * Used to play the "Ability learned!" sound when hitting ranks that give an ability.
     *
     * @param stageId The ID of the stage reached.
     * @return The key of the sound to play.
     */
    protected String getSoundForStageReachedUpdate(Object stageId) {
        if (STAGE_ABILITIES_MAP.containsKey((Stage) stageId)) return "ui_learned_ability";
        return super.getSoundForStageReachedUpdate(stageId);
    }

    /**
     * Selects what sound to play after a one-time addition to the event.
     * <p>
     * No sound is used.
     *
     * @param factor The factor influencing this event.
     * @return The key of the sound to play; always null.
     */
    @Override
    protected String getSoundForOneTimeFactorUpdate(EventFactor factor) {
        return null;
    }
}
