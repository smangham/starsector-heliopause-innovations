package toaster.hp.hullmods;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.GameState;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CampaignUIAPI;
import com.fs.starfarer.api.campaign.CargoStackAPI;
import com.fs.starfarer.api.campaign.FleetDataAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MonthlyReport;
import com.fs.starfarer.api.campaign.listeners.EconomyTickListener;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.characters.MutableCharacterStatsAPI;
import com.fs.starfarer.api.characters.SkillSpecAPI;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.fleet.MutableFleetStatsAPI;
import com.fs.starfarer.api.impl.campaign.shared.SharedData;
import com.fs.starfarer.api.impl.combat.CRPluginImpl;
import com.fs.starfarer.api.loading.HullModSpecAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.DynamicStatsAPI;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.campaign.fleet.FleetData;
import com.fs.starfarer.prototype.Utils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import toaster.hp.campaign.ids.HullMods;
import toaster.hp.campaign.ids.MutableStats;
import toaster.hp.campaign.ids.Tags;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Class for hullmods that reduce crew upkeep
 */
public class ChronoSleeper extends BaseHullMod {
    public static final String ID = HullMods.CHRONOSLEEPER;
    /// The game ID for this hullmod.

    public static final String CAPACITY_TEXT = Global.getSettings().getString(ID, "capacity");

    public static final float CREW_SALARY_REDUCTION = 0.95f;
    ///  The reduction in slaary of crew in chronostasis.

    public static final String TAG = Tags.CHRONOSLEEPER;
    /// The tag for a ship with chronosleep facilities.

    public static final String MEMORY_KEY = "$"+Tags.CHRONOSLEEPER;
    ///  The memory key used to store whether or not there are active chronosleepers.

    public static final float DAYS_PER_CHECK = 1f;
    /// How long to wait between re-assessing the fleet

    protected float elapsed = 0f;
    /// Timer for assessing whether the global chronosleep tracker should be started (in case this is mothballed).

    /**
     * Gets ships with a chronostasis-tagged hullmod.
     *
     * @param fleet The fleet to check the ships for
     * @return The list, if any, of ships with a tagged hullmod.
     */
    public static List<FleetMemberAPI> getShipsWithHullmod(FleetDataAPI fleet) {
        ArrayList<FleetMemberAPI> members = new ArrayList<>();
        for (FleetMemberAPI member : fleet.getMembersListCopy()) {
            if (member.getType() != FleetMemberType.SHIP) continue;
            if (member.isMothballed()) continue;
            if (member.getRepairTracker().getCR() < CRPluginImpl.MALFUNCTION_START) continue;
            for (String hullModId : member.getHullSpec().getBuiltInMods()) {
                if (Global.getSettings().getHullModSpec(hullModId).hasTag(TAG)) members.add(member);
            }
        }
        return members;
    }

    /**
     * Gets the amount of cryostasis berths available.
     *
     * @param member The ship to check.
     * @return The number of berths this ship has.
     */
    protected static int getChronoSleepCapacity(FleetMemberAPI member) {
        return (int) (member.getMaxCrew() - member.getMinCrew());
    }

    /**
     * If this ship would contribute to the global tracker, see if it's running and start it if not.
     *
     * @param member The ship the hullmod is on.
     * @param amount The clock time passed since last advance.
     */
    @Override
    public void advanceInCampaign(FleetMemberAPI member, float amount) {
        if (member.isMothballed()) return;

        this.elapsed += Misc.getDays(amount);
        if (this.elapsed > DAYS_PER_CHECK) {
            if (member.getFleetData() == null) return;
            if (member.getFleetData().getFleet() == null) return;
            if (!member.getFleetData().getFleet().isPlayerFleet()) return;

            if (!Global.getSector().getMemoryWithoutUpdate().contains(MEMORY_KEY)) {
                new ChronoSleeperStipend();
            }
        }
    }

    /**
     * @param tooltip
     * @param hullSize
     * @param ship
     * @param width
     * @param isForModSpec
     */
    @Override
    public void addPostDescriptionSection(TooltipMakerAPI tooltip, ShipAPI.HullSize hullSize, ShipAPI ship, float width, boolean isForModSpec) {
        super.addPostDescriptionSection(tooltip, hullSize, ship, width, isForModSpec);

        if (ship == null) return;
        if (ship.getFleetMember() == null) return;

        tooltip.addPara(
                CAPACITY_TEXT.replace("%s", "" + getChronoSleepCapacity(ship.getFleetMember())),
                10f, Misc.getTextColor(), Misc.getHighlightColor(),
                "" + getChronoSleepCapacity(ship.getFleetMember())
        );
    }

    /**
     * @param index
     * @param hullSize
     * @param ship
     * @return
     */
    @Override
    public String getDescriptionParam(int index, ShipAPI.HullSize hullSize, ShipAPI ship) {
        if (index == 0) return "" + (int) (CREW_SALARY_REDUCTION * 100);
        return super.getDescriptionParam(index, hullSize, ship);
    }

    @Override
    public String getDescriptionParam(int index, ShipAPI.HullSize hullSize) {
        if (index == 0) return "" + hullSize;
        return super.getDescriptionParam(index, hullSize);
    }

    /**
     *
     */
    public static class ChronoSleeperStipend implements EconomyTickListener, TooltipMakerAPI.TooltipCreator {
        protected int availableBerths;  /// The total chronostasis berths in the fleet.
        protected int usedBerths;  /// How many of the berths them are used.
        protected int unusedBerths;  /// The unused chronostasis berths.
        protected int surplusCrew;  /// The number of crew above the skeleton crew.

        /**
         *
         */
        public ChronoSleeperStipend() {
            Global.getSector().getListenerManager().addListener(this);
            Global.getSector().getMemoryWithoutUpdate().set(MEMORY_KEY, 0f);
        }

        /**
         * Evaluated with the economy ticks to build the monthly financial report.
         *
         * @param iterIndex The iteration of the economy.
         */
        public void reportEconomyTick(int iterIndex) {
            CampaignFleetAPI playerFleet = Global.getSector().getPlayerFleet();
            if (playerFleet == null) return;

            int lastIterInMonth = (int) Global.getSettings().getFloat("economyIterPerMonth") - 1;
            if (iterIndex != lastIterInMonth) return;

            float numIter = Global.getSettings().getFloat("economyIterPerMonth");
            float f = 1f / numIter;

            MonthlyReport report = SharedData.getData().getCurrentReport();
            MonthlyReport.FDNode fleetNode = report.getNode(MonthlyReport.FLEET);
            MonthlyReport.FDNode crewNode = report.getNode(fleetNode, MonthlyReport.CREW);
            MonthlyReport.FDNode discountNode = report.getNode(crewNode, ID);

            this.availableBerths = 0;
            for (FleetMemberAPI member : getShipsWithHullmod(playerFleet.getFleetData())) {
                if (member.isMothballed()) continue;
                this.availableBerths += getChronoSleepCapacity(member);
            }

            this.surplusCrew = (int) (playerFleet.getCargo().getTotalCrew() - playerFleet.getFleetData().getMinCrew());
            this.usedBerths = Math.min(this.availableBerths, this.surplusCrew);

            if (this.usedBerths == 0f) {
                Global.getSector().getListenerManager().removeListener(this);
                Global.getSector().getMemoryWithoutUpdate().unset(MEMORY_KEY);
                return;
            }

            float crewSalaryDiscount = this.usedBerths * Global.getSettings().getInt("crewSalary") * f;
            this.unusedBerths = this.availableBerths - this.usedBerths;

            discountNode.income = crewSalaryDiscount * CREW_SALARY_REDUCTION;
            discountNode.name = "Chronostasis savings";
            discountNode.icon = Global.getSettings().getSpriteName(ID, "icon");
            discountNode.tooltipCreator = this;
        }

        public void reportEconomyMonthEnd() {
        }

        /**
         * @param tooltip
         * @param expanded
         * @param tooltipParam
         */
        public void createTooltip(TooltipMakerAPI tooltip, boolean expanded, Object tooltipParam) {
            tooltip.addPara(
                    "You have %s excess crew and %s cryostasis berths. %s crew are in cryostasis, with a %s salary discount.", 0f,
                    Misc.getTextColor(), Misc.getHighlightColor(),
                    ""+this.surplusCrew, ""+this.availableBerths, ""+this.usedBerths,
                    ""+(int) (CREW_SALARY_REDUCTION * 100)+"%"
            );

            List<FleetMemberAPI> fleetMembers = getShipsWithHullmod(Global.getSector().getPlayerFleet().getFleetData());
            if (!fleetMembers.isEmpty()) {
                tooltip.addPara("You have the following ships with chronostasis facilities:", 0f);
                tooltip.addShipList(
                        fleetMembers.size(), 1,440f / fleetMembers.size(),
                        Misc.getBasePlayerColor(), fleetMembers, 10f
                );
            }

            if (unusedBerths > 0) {
                tooltip.addPara(
                        "You had "+unusedBerths+" unused chronostasis berths this month.",
                        0f, Misc.getNegativeHighlightColor(), ""+this.unusedBerths
                );
            }
        }

        public float getTooltipWidth(Object tooltipParam) {
            return 450;
        }

        public boolean isTooltipExpandable(Object tooltipParam) {
            return false;
        }
    }
}
