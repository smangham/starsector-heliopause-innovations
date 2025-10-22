package toaster.hp.campaign.submarkets;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoStackAPI;
import com.fs.starfarer.api.campaign.CoreUIAPI;
import com.fs.starfarer.api.campaign.FactionAPI.ShipPickMode;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.campaign.econ.SubmarketAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.submarkets.BaseSubmarketPlugin;
import toaster.hp.campaign.ids.Submarkets;

import java.util.*;


/**
 * Sets up faction-limited submarket.
 * <p>
 * The goods and actions on the submarket can be filtered using a range of blacklists.
 * Because Java doesn't let you override member variables (even statics) this is messier than it should be.
 */
public class HeliopauseSubmarketPlugin extends BaseSubmarketPlugin {
    public static String ID = Submarkets.HELIOPAUSE;  /// Used for keys for strings.csv e.t.c.
    public static float TARIFF = 0.3f;  /// Tariff for sales on this market.
    public static float DP_PER_SIZE = 10f;  /// DP of ships sold per market size.
    public static int WEAPONS_PER_SIZE_MIN = 0;
    public static int WEAPONS_PER_SIZE_MAX = 0;
    public static int FIGHTERS_PER_SIZE_MIN = 0;
    public static int FIGHTERS_PER_SIZE_MAX = 0;
    public static int TIER_PER_SIZE = 0;  /// Maximum tier of equipment sold per market size.

    /**
     * The types of restriction by which a transfer can be illegal.
     * <p>
     * Each has an associated message in `strings.json` under `<ID>:"illegal_<ILLEGAL_TYPE>"`.
     * Everything but `GENERIC` has a "%s" in to be replaced with the illegal item/tag
     */
    protected enum IllegalType {
        ACTION, HULL, COMMODITY, DEMAND_WHITELIST, DEMAND_BLACKLIST, SPECIAL, TAG, GENERIC
    }

    /**
     * Actions that are banned.
     * <p>
     * Show the `illegal_ACTION` string, with %s replaced by the action name.
     */
    public static final Set<TransferAction> ACTION_BLACKLIST = Set.of();

    /**
     * Actions that are permitted. All others are banned. Use this or the blacklist.
     * <p>
     * Show the `illegal_ACTION` string, with %s replaced by the action name.
     */
    public static final Set<TransferAction> ACTION_WHITELIST = Set.of();

    /**
     * Hull IDs that cannot be sold here.
     * <p>
     * Show the `illegal_HULL` key, with %s subbed for the hull name.
     */
    public static final Set<String> HULL_ID_BLACKLIST = Set.of();

    /**
     * Commodity demand types that are permitted. All others are banned. Use this or the blacklist.
     * <p>
     * Show the `illegal_DEMAND_WHITELIST` key, with %s subbed for the submarket name.
     */
    public static final Set<String> COMMODITY_DEMAND_WHITELIST = Set.of();

    /**
     * Commodity demand types that are banned.
     * <p>
     * Keys of demand types, paired with a human-readable name.
     * Shows the `illegal_DEMAND_BLACKLIST` key, with %s subbed for the name for that demand type.
     */
    public static final Map<String, String> COMMODITY_DEMAND_BLACKLIST_MAP = Map.of(
            "survey_data", "Survey data"
    );

    /**
     * Commodity IDs that are banned.
     * <p>
     * Show the `illegal_COMMODITY` key, with %s subbed for the commodity name.
     */
    public static final Set<String> COMMODITY_ID_BLACKLIST = Set.of();

    /**
     * Special item IDs that are banned.
     * <p>
     * Show the `illegal_SPECIAL` key, with %s subbed for the special item name.
     */
    public static final Set<String> SPECIAL_ID_BLACKLIST = Set.of(
            "topographic_data"
    );

    /**
     * Tags that are banned.
     * <p>
     * Strings of illegal tags, paired with a human-readable name.
     * Show the `illegal_TAG` key, with %s subbed for the tag name.
     */
    public static final Map<String, String> TAG_BLACKLIST_MAP = Map.of();

    public static String getId() { return Submarkets.HELIOPAUSE; }  /// Getter for class variable.
    public static Set<TransferAction> getActionBlacklist() { return ACTION_BLACKLIST; };  /// Getter for class variable.
    public static Set<TransferAction> getActionWhitelist() { return ACTION_WHITELIST; };  /// Getter for class variable.
    public static Set<String> getHullIdBlacklist() { return HULL_ID_BLACKLIST; };  /// Getter for class variable.
    public static Set<String> getCommodityDemandWhitelist() { return COMMODITY_DEMAND_WHITELIST; };  /// Getter for class variable.
    public static Map<String, String> getCommodityDemandBlacklistMap() { return COMMODITY_DEMAND_BLACKLIST_MAP; };  /// Getter for class variable.
    public static Set<String> getCommodityIdBlacklist() { return COMMODITY_ID_BLACKLIST; };  /// Getter for class variable.
    public static Set<String> getSpecialIdBlacklist() { return SPECIAL_ID_BLACKLIST; };  /// Getter for class variable.
    public static Map<String, String> getTagBlacklistMap() { return TAG_BLACKLIST_MAP; };  /// Getter for class variable.

    @Override
    public void init(SubmarketAPI submarket) { super.init(submarket); }

    /**
     * Gets the tariff paid on sales and purchases on this submarket.
     *
     * @return              By default, the class constant.
     */
    @Override
    public float getTariff() { return TARIFF; }

    @Override
    public String getTooltipAppendix(CoreUIAPI ui) {
        RepLevel level = market.getFaction().getRelationshipLevel(Global.getSector().getFaction(Factions.PLAYER));
        return super.getTooltipAppendix(ui);
    }

    @Override
    public boolean isEnabled(CoreUIAPI ui) { return true; }

    /**
     * Every time the player visits, clears the auto-generated supplies and generates a custom set.
     *
     * @author Shoi (original, from Arma Armatura).
     * @author Toaster (modified).
     */
    @Override
    public void updateCargoPrePlayerInteraction() {
        sinceLastCargoUpdate = 0f;

        if (okToUpdateShipsAndWeapons()) {
            int size = this.submarket.getMarket().getSize();
            sinceSWUpdate = 0f;

            // Clear the auto-generated ships, then add a subset to our specific doctrine.
            this.getCargo().getMothballedShips().clear();
            addShips(
                    this.submarket.getFaction().getId(),
                    0f, // combat
                    0f, // freighter
                    0f, // tanker
                    0f, // transport
                    0f, // liner
                    size * DP_PER_SIZE, // utilityPts
                    null, // qualityOverride
                    0f, // qualityMod
                    ShipPickMode.PRIORITY_THEN_ALL,
                    this.submarket.getFaction().getDoctrine().clone()
            );
            // Then delete half the ships on offer (?)
            pruneShips(0.5f);

            // Clear the existing weapon stockpile, then add a small number of weapons
            pruneWeapons(0f);
            if(WEAPONS_PER_SIZE_MAX > 0) {
                addWeapons(
                        size * WEAPONS_PER_SIZE_MIN,
                        size * WEAPONS_PER_SIZE_MAX,
                        size * TIER_PER_SIZE,
                        submarket.getFaction().getId()
                );
            }
            if(FIGHTERS_PER_SIZE_MAX > 0){
                addFighters(
                        size * FIGHTERS_PER_SIZE_MIN,
                        size * FIGHTERS_PER_SIZE_MAX,
                        size * TIER_PER_SIZE,
                        submarket.getFaction().getId()
                );
            }
        }
        getCargo().sort();
    }

    /**
     * Filters out banned special items.
     *
     * @param stack         Stack of cargo items to check.
     * @param action        What's the player trying to do with them?
     * @return              True if it's blocked on this submarket.
     */
    @Override
    public boolean isIllegalOnSubmarket(CargoStackAPI stack, TransferAction action) {
        if (getActionBlacklist().contains(action)) {
            return true;
        } else if (stack.isSpecialStack()) {
            if (getSpecialIdBlacklist().contains(stack.getSpecialItemSpecIfSpecial().getId())) {
                return true;
            } else if (!getIllegalTags(stack.getSpecialItemSpecIfSpecial().getTags()).isEmpty()){
                return true;
            }
        }
        return super.isIllegalOnSubmarket(stack, action);
    }

    /**
     * Filters out commodities that are from a banned demand class.
     *
     * @param commodityId   The ID of the commodity.
     * @param action        What the player is trying to do.
     * @return              True if it's a banned commodity.
     */
    @Override
    public boolean isIllegalOnSubmarket(String commodityId, TransferAction action) {
        if (getActionBlacklist().contains(action)) {
            return true;
        } else if (getCommodityIdBlacklist().contains(commodityId)) {
            return true;
        } else if (getCommodityDemandBlacklistMap().containsKey(
                Global.getSettings().getCommoditySpec(commodityId).getDemandClass())
        ) {
            return true;
        }
        return super.isIllegalOnSubmarket(commodityId, action);
    }

    /**
     * Filters out ships of banned hulls or with forbidden tags.
     *
     * @param member        The fleet member being transferred.
     * @param action        What the player is trying to do.
     * @return              True if it's a banned ship.
     */
    @Override
    public boolean isIllegalOnSubmarket(FleetMemberAPI member, TransferAction action) {
        if (getActionBlacklist().contains(action)) {
            return true;
        } else if (getHullIdBlacklist().contains(member.getHullId())) {
            return true;
        } else if(!getIllegalTags(member.getVariant().getTags()).isEmpty()) {
            return true;
        }
        return super.isIllegalOnSubmarket(member, action);
    }

    /**
     * Are any of these tags illegal?
     *
     * @param tags          A set of tags.
     * @return              True if any of the tags are illegal.
     */
    protected Set<String> getIllegalTags(Collection<String> tags) {
        HashSet<String> tagsCopy = new HashSet<>(){{
            addAll(tags);
        }};
        tagsCopy.retainAll(getTagBlacklistMap().keySet());
        return tagsCopy;
    }

    /**
     * Convenience method for illegal actions.
     *
     * @param type          The type of the illegal action (hull, tag, action, commodity).
     * @param name          The name of the thing to substitute in.
     * @return              The illegal string, with the name subbed in.
     */
    protected String getIllegalText(IllegalType type, String name) {
        return Global.getSettings().getString(ID, "illegal_"+type.name()).formatted(name);
    }

    /**
     * Tooltip message for illegal ship transfers.
     *
     * @param member        The fleet member being transferred.
     * @param action        The transfer action.
     * @return              String explaining why.
     */
    @Override
    public String getIllegalTransferText(FleetMemberAPI member, TransferAction action) {
        if (getActionBlacklist().contains(action) || (!getActionWhitelist().isEmpty() && !getActionWhitelist().contains(action))) {
            if (action == TransferAction.PLAYER_BUY) {
                return getIllegalText(IllegalType.ACTION, "buy");
            } else {
                return getIllegalText(IllegalType.ACTION, "sell");
            }

        } else if (!getIllegalTags(member.getVariant().getTags()).isEmpty()) {
            return getIllegalText(
                    IllegalType.TAG, getTagBlacklistMap().get((String) member.getVariant().getTags().toArray()[0])
            );

        } else if (getHullIdBlacklist().contains(member.getHullId())) {
            return getIllegalText(IllegalType.HULL, member.getVariant().getFullDesignationWithHullName());

        } else {
            return getIllegalText(IllegalType.GENERIC, this.getName());
        }
    }

    /**
     * Tooltip message for illegal item transfers.
     * <p>
     * Custom response per blacklisted class.
     *
     * @param stack         The illegal stack of items.
     * @param action        The action the player is performing.
     * @return              String explaining why.
     */
    @Override
    public String getIllegalTransferText(CargoStackAPI stack, TransferAction action) {
        if (getActionBlacklist().contains(action) || (!getActionWhitelist().isEmpty() && !getActionWhitelist().contains(action))) {
            return getIllegalText(IllegalType.ACTION, action.name());

        } else if (stack.isSpecialStack()) {
            if (!getIllegalTags(stack.getSpecialItemSpecIfSpecial().getTags()).isEmpty()) {
                return getIllegalText(
                        IllegalType.ACTION,
                        getTagBlacklistMap().get(
                                (String) getIllegalTags(stack.getSpecialItemSpecIfSpecial().getTags()).toArray()[0]
                        )
                );

            } else {
                return getIllegalText(IllegalType.SPECIAL, stack.getDisplayName());
            }

        } else if (stack.isCommodityStack()) {
            String demand = Global.getSettings().getCommoditySpec(stack.getCommodityId()).getDemandClass();
            if (!getCommodityDemandWhitelist().isEmpty() && !getCommodityDemandWhitelist().contains(demand)) {
                return getIllegalText(IllegalType.DEMAND_WHITELIST, this.getName());
            } else if (getCommodityDemandBlacklistMap().containsKey(demand)) {
                return getIllegalText(IllegalType.DEMAND_BLACKLIST, getCommodityDemandBlacklistMap().get(demand));
            } else {
                return getIllegalText(IllegalType.COMMODITY, stack.getDisplayName());
            }

        } else {
            return getIllegalText(IllegalType.GENERIC, this.getName());
        }
    }

    /**
     * Does this submarket take part in the global economy?
     *
     * @return              By default, false.
     */
    @Override
    public boolean isParticipatesInEconomy() { return false; }
}
