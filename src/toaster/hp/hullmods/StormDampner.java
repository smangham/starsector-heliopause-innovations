package toaster.hp.hullmods;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import java.awt.Color;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BuffManagerAPI;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CampaignUIAPI;
import com.fs.starfarer.api.campaign.FleetDataAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.combat.HullModEffect;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.MutableStat;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.fleet.FleetAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.impl.combat.CRPluginImpl;
import com.fs.starfarer.api.loading.HullModSpecAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import org.apache.log4j.Logger;
import toaster.hp.campaign.ids.HullMods;
import toaster.hp.campaign.ids.Tags;


/**
 * Hullmod script for Storm Dampner
 * <p>
 * Handles tagging the fleet as using the alternate storm strike logic.
 */
public class StormDampner implements HullModEffect {
    public static final String ID = HullMods.STORM_DAMPNER;
    public static final String KEY = "$"+ID;

    /**
     * Initialises the hullmod, using data from hull_mods.csv.
     *
     * @param spec          The hullmod spec for this mod.
     */
    public void init(HullModSpecAPI spec) { }

    /**
     * Gets a fleet member with an active storm dampner, if any.
     * <p>
     * Expected to be called after filtering the fleet based on the key.
     *
     * @param fleet         The fleet to check.
     * @return              A member with a dampner hullmod, if any, or null.
     */
    public static FleetMemberAPI getFleetMemberWithDampner(CampaignFleetAPI fleet) {
        if (fleet == null) return null;
        Global.getLogger(StormDampner.class).info("Keys: "+fleet.getMemoryWithoutUpdate().getKeys());
        if (!fleet.getMemoryWithoutUpdate().contains(KEY)) return null;

        for (FleetMemberAPI member: fleet.getFleetData().getMembersListCopy()) {
            if (member.getType() != FleetMemberType.SHIP) continue;
            if (member.isMothballed()) continue;
            if (member.getRepairTracker().getCR() < CRPluginImpl.MALFUNCTION_START) continue;
            for (String hull_mod_id : member.getHullSpec().getBuiltInMods()) {
                if (Global.getSettings().getHullModSpec(hull_mod_id).hasTag(ID)) return member;
            }
        }
        return null;
    }

    /**
     * Called every frame in the campaign layer to keep the fleet flagged.
     * <p>
     * Keeps the general 'this fleet has a storm dampner' flag on a fleet for fast checking.
     * Duration lasts half a day and is constantly refreshed.
     *
     * @param member        The fleet member with the hullmod.
     * @param amount        How long has passed since last frame, in days.
     */
    public void advanceInCampaign(FleetMemberAPI member, float amount) {
        if (member.isMothballed()) return;
        if (member.getFleetData() == null) return;
        CampaignFleetAPI campaignFleet = member.getFleetData().getFleet();
        if (campaignFleet == null) return;
        if (!campaignFleet.isPlayerFleet()) return;
        campaignFleet.getMemoryWithoutUpdate().set(KEY, true, 0.5f);
    }

    public void advanceInCombat(ShipAPI ship, float amount) {}

    public void applyEffectsAfterShipCreation(ShipAPI ship, String id) {}
    public void applyEffectsBeforeShipCreation(ShipAPI.HullSize hullSize, MutableShipStatsAPI stats, String id) {}
    public void applyEffectsToFighterSpawnedByShip(ShipAPI fighter, ShipAPI ship, String id) {}

    public boolean affectsOPCosts() { return false; }

    // ********************************
    // Refit/install options
    // ********************************
    public boolean isApplicableToShip(ShipAPI ship) { return true; }
    public String getUnapplicableReason(ShipAPI ship) { return null; }

    public boolean canBeAddedOrRemovedNow(ShipAPI ship, MarketAPI marketOrNull, CampaignUIAPI.CoreUITradeMode mode) {
        return false;
    }
    public String getCanNotBeInstalledNowReason(
            ShipAPI ship, MarketAPI marketOrNull, CampaignUIAPI.CoreUITradeMode mode
    ) {
        return null;
    }
    public boolean showInRefitScreenModPickerFor(ShipAPI ship) { return false; }
    // ********************************

    // ********************************
    // Icon e.t.c. details
    // ********************************
    public Color getBorderColor() { return null; }
    public Color getNameColor() { return null; }
    public int getDisplaySortOrder() { return 100; }
    public int getDisplayCategoryIndex() { return -1; }
    // ********************************

    // ********************************
    // Tooltip details
    // ********************************
    public float getTooltipWidth() { return 412f; }
    public boolean shouldAddDescriptionToTooltip(ShipAPI.HullSize hullSize, ShipAPI ship, boolean isForModSpec) {
        return true;
    }
    public void addPostDescriptionSection(
            TooltipMakerAPI tooltip, ShipAPI.HullSize hullSize, ShipAPI ship, float width, boolean isForModSpec
    ) {
    }
    public String getDescriptionParam(int index, ShipAPI.HullSize hullSize) {
        return "" + (int) CRPluginImpl.MALFUNCTION_START;
    }
    public String getDescriptionParam(int index, ShipAPI.HullSize hullSize, ShipAPI ship) {
        return getDescriptionParam(index, hullSize);
    }
    @Override
    public void addRequiredItemSection(
            TooltipMakerAPI tooltip, FleetMemberAPI member, ShipVariantAPI currentVariant,
            MarketAPI dockedAt, float width, boolean isForModSpec
    ) {
        // TODO Auto-generated method stub
    }
    // ********************************

    // ********************************
    // SMod tooltip details
    // ********************************
    public boolean hasSModEffect() { return false; }
    public boolean hasSModEffectSection(ShipAPI.HullSize hullSize, ShipAPI ship, boolean isForModSpec) {
        return false;
    }
    public void addSModSection(
            TooltipMakerAPI tooltip, ShipAPI.HullSize hullSize, ShipAPI ship, float width, boolean isForModSpec
    ) {}
    public void addSModSection(
            TooltipMakerAPI tooltip, ShipAPI.HullSize hullSize, ShipAPI ship, float width,
            boolean isForModSpec, boolean isForBuildInList
    ) {
        // TODO Auto-generated method stub
    }
    public void addSModEffectSection(
            TooltipMakerAPI tooltip, ShipAPI.HullSize hullSize, ShipAPI ship, float width,
            boolean isForModSpec, boolean isForBuildInList
    ) {
        // TODO Auto-generated method stub
    }
    public String getSModDescriptionParam(int index, ShipAPI.HullSize hullSize) { return null; }
    public String getSModDescriptionParam(int index, ShipAPI.HullSize hullSize, ShipAPI ship) { return null; }
    public boolean isSModEffectAPenalty() { return false; }
    // ********************************
}
