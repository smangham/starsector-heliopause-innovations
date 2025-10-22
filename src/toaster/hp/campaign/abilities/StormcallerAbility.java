package toaster.hp.campaign.abilities;

import java.awt.Color;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BattleAPI;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.abilities.BaseDurationAbility;
import com.fs.starfarer.api.impl.campaign.terrain.HyperspaceTerrainPlugin;
import com.fs.starfarer.api.loading.CampaignPingSpec;
import com.fs.starfarer.api.loading.Description;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import org.apache.log4j.Logger;
import toaster.hp.campaign.ids.Abilities;
import toaster.hp.hullmods.StormDampner;
import toaster.hp.weapons.stormcaller.StormcallerParams;

/**
 * Ability that generates a pulse of storms across nearby hyperspace
 */
public class StormcallerAbility extends BaseDurationAbility {
    public static final float BASE_RANGE = 500f;  /// The range at which storms are triggered.

    protected boolean triggered = false;  /// Whether the stormcalling pulse has triggered.

    /**
     * Gets the range the storm should cover.
     *
     * @param fleet The fleet sending the pulse.
     * @return The range, a function of sensor strength.
     */
    public static float getRange(CampaignFleetAPI fleet) {
        float max = Global.getSettings().getMaxSensorRange();
        return Math.min(max, BASE_RANGE + fleet.getSensorRangeMod().computeEffective(fleet.getSensorStrength()) / 2f);
    }

    /**
     * Shows the activation text for the ability.
     *
     * @return  The constant.
     */
    @Override
    protected String getActivationText() {
        return Global.getSettings().getDescription(Abilities.STORMCALLER, Description.Type.CUSTOM).getText2();
    }

    /**
     * One-off called when the ability is activated.
     */
    @Override
    protected void activateImpl() {
        CampaignFleetAPI fleet = getFleet();
        if (fleet == null) return;

        this.triggered = false;
    }

    /**
     * Every frame script applying the stormcaller effect.
     *
     * @param amount    Time passed, in days?
     * @param level     The level of the ability, goes up to 1 during ramp-up, then 1 during active phase.
     */
    @Override
    protected void applyEffect(
            float amount, float level
    ) {
        if (this.triggered) return;
        CampaignFleetAPI fleet = getFleet();
        if (fleet == null) return;

        if (amount == 1) {
            this.triggered = true;
            float range = getRange(fleet);

            CampaignPingSpec ping_spec = new CampaignPingSpec();
            ping_spec.setColor(StormcallerParams.STRIKE_COLOUR);
            ping_spec.setWidth(15);
            ping_spec.setRange(range * level);
            ping_spec.setDuration(0.5f);
            ping_spec.setAlphaMult(1f);
            ping_spec.setInFraction(0.1f);
            ping_spec.setNum(3);
            Global.getSector().addPing(fleet, ping_spec);

            HyperspaceTerrainPlugin plugin = (HyperspaceTerrainPlugin) Misc.getHyperspaceTerrain().getPlugin();
            if (plugin != null && entity.isInHyperspace()) {
                plugin.setTileState(
                        entity.getLocation(), range * level,
                        HyperspaceTerrainPlugin.CellState.SIGNAL,
                        0f, 0.1f, 0.8f
                );
                plugin.setTileState(
                        entity.getLocation(), range * level * 0.5f,
                        HyperspaceTerrainPlugin.CellState.STORM,
                        0f, 0.1f, 0.2f
                );
            }
        }
    }

    /**
     * Called if the system is deactivated.
     * <p>
     * Calls the cleanup.
     */
    @Override
    protected void deactivateImpl() {
//        cleanupImpl();
    }

    /**
     * Called to cleanup once duration is over
     * <p>
     * Empty placeholder.
     */
    @Override
    protected void cleanupImpl() {
//        CampaignFleetAPI fleet = getFleet();
    }

    /**
     * Can this ability be used?
     *
     * @return True if the fleet has an active, working storm dampner.
     */
    @Override
    public boolean isUsable() {
        if (!super.isUsable()) return false;
        CampaignFleetAPI fleet = getFleet();
        if (fleet == null) return false;
        if (!fleet.isInHyperspace()) return false;
        if (!fleet.getMemoryWithoutUpdate().contains(StormDampner.KEY)) return false;
        return StormDampner.getFleetMemberWithDampner(fleet) != null;
    }

//	protected List<FleetMemberAPI> getNonReadyShips() {
//		List<FleetMemberAPI> result = new ArrayList<FleetMemberAPI>();
//		CampaignFleetAPI fleet = getFleet();
//		if (fleet == null) return result;
//
//		float crCostFleetMult = fleet.getStats().getDynamic().getValue(Stats.EMERGENCY_BURN_CR_MULT);
//		for (FleetMemberAPI member : fleet.getFleetData().getMembersListCopy()) {
//			//if (member.isMothballed()) continue;
//			float crLoss = member.getDeployCost() * CR_COST_MULT * crCostFleetMult;
//			if (Math.round(member.getRepairTracker().getCR() * 100) < Math.round(crLoss * 100)) {
//				result.add(member);
//			}
//		}
//		return result;
//	}

//	protected float computeSupplyCost() {
//		CampaignFleetAPI fleet = getFleet();
//		if (fleet == null) return 0f;
//
//		float crCostFleetMult = fleet.getStats().getDynamic().getValue(Stats.EMERGENCY_BURN_CR_MULT);
//
//		float cost = 0f;
//		for (FleetMemberAPI member : fleet.getFleetData().getMembersListCopy()) {
//			cost += member.getDeploymentPointsCost() * CR_COST_MULT * crCostFleetMult;
//		}
//		return cost;
//	}

    /**
     * Creates the tooltip for a stage.
     *
     * @param tooltip           The tooltip to add fields to.
     * @param expanded          Whether it's expanded?
     */
    @Override
    public void createTooltip(TooltipMakerAPI tooltip, boolean expanded) {
        CampaignFleetAPI fleet = getFleet();
        if (fleet == null) return;

        Color text_colour_gray = Misc.getGrayColor();
        Color text_colour_highlight = Misc.getHighlightColor();
        Description desc = Global.getSettings().getDescription(Abilities.STORMCALLER, Description.Type.CUSTOM);

        if (!Global.CODEX_TOOLTIP_MODE) {
            LabelAPI title = tooltip.addTitle(Global.getSettings().getString(Abilities.STORMCALLER, "name"));
        } else {
            tooltip.addSpacer(-10f);
        }
        float pad = 10f;

        Color highlight = text_colour_highlight;
        if (Global.CODEX_TOOLTIP_MODE) highlight = Misc.getBasePlayerColor();

        for (String para : desc.getText1Paras()) {
            tooltip.addPara(para, pad, highlight, desc.getText2());
        }

        tooltip.addPara(
                desc.getText3(), pad, text_colour_highlight, "" + (int) BASE_RANGE, "" + (int) getRange(fleet)
        );

        tooltip.addPara(Global.getSettings().getString("abilityCommon", "range"), text_colour_gray, pad);
        tooltip.addPara(Global.getSettings().getString("abilityCommon", "slow"), text_colour_gray, pad);
        addIncompatibleToTooltip(tooltip, expanded);
    }

    /**
     * Called when the fleet engages in battle; the disruption should stop.
     *
     * @param battle                The battle the fleet approached.
     * @param engagedInHostilities  If the fleet engaged, or left.
     */
    @Override
    public void fleetLeftBattle(BattleAPI battle, boolean engagedInHostilities) {
        if (engagedInHostilities) deactivate();
    }

    /**
     * Called when the fleet activates a market; the disruption should stop.
     *
    * @param market            The market the fleet interacted with.
     */
    @Override
    public void fleetOpenedMarket(MarketAPI market) { deactivate(); }
}
