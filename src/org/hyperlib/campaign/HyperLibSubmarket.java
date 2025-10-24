package org.hyperlib.campaign;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.CargoStackAPI;
import com.fs.starfarer.api.campaign.CoreUIAPI;
import com.fs.starfarer.api.campaign.FactionAPI.ShipPickMode;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.campaign.econ.SubmarketAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.submarkets.BaseSubmarketPlugin;
import org.hyperlib.util.SensibleHashMap;
import org.jetbrains.annotations.Nullable;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.*;

/**
 * Sets up a special submarket.
 * <p>
 * The goods and actions on the submarket can be filtered using a range of blacklists and whitelists.
 * These are defined in a `.json` file under `data/campaign/submarkets/`, with a name matching the id of the submarket
 * as defined in `submarkets.csv`. The file structure is:
 * <p>
 * <pre>
 *     {
 *         "no_sell_message": "OPTIONAL: If this is here, selling is banned and this message is shown as the reason.",
 *         "no_buy_message": "OPTIONAL: If this is here, selling is banned and this message is shown as the reason.",
 *         "message": "The generic message for why you can't sell/buy/transfer X at this submarket. %s is replaced by the faction."
 *         "tariff": 0.3            # OPTIONAL: If present, the submarket tariff; if not, uses the market default.
 *         "in_economy": False      # OPTIONAL: If present, whether it's part of the global econony; if not, it isn't.
 *
 *         "hulls" {                # OPTIONAL: If present, limits on hulls that can be bought/sold here.
 *             "blacklist": {           # OPTIONAL: If present, hulls in the blacklist cannot be bought/sold.
 *                 "ids": ["onslaught", "eagle"],    # Ids that can't be bought/sold.
 *                 "message": "The message for why this hull can't be sold. %s is replaced with the hull name."
 *             }
 *             "whitelist": {           # OPTIONAL: If present, only hulls in the whitelist can be bought/sold.
 *                 "ids": ["onslaught", "eagle"],    # Only these ids can be bought/sold.
 *                 "message": "The message for why this hull can't be sold. %s is replaced with the hull name."
 *             }
 *         },
 *         "special_items : {}      # OPTIONAL: If present, limits the special items that can be bought/sold/
 *                                  # Same format as hulls, %s is replaced by the special item name.
 *         "commodities : {}        # OPTIONAL: If present, limits the commodities that can be bought/sold.
 *                                  # Same format as hulls, %s is replaced by the commodity name.
 *         "demand_classes" {       # OPTIONAL: If present, limits on classes of commodities that can be bought/sold.
 *             "blacklist": {           # OPTIONAL: If present, demand classes in the blacklist cannot be bought/sold.
 *                 "ids": ["luxury_goods", "ai_cores"],    # Ids that can't be bought/sold.
 *                 "display_names": ["luxury goods", "AI cores"]  # Display names for those ids (the game doesn't have them).
 *                 "message": "The message for why this demand class can't be sold. %s is replaced with the corresponding value from 'display_names'."
 *             }
 *             "whitelist": {           # OPTIONAL: If present, only demand classes in the whitelist can be bought/sold.
 *                 "ids": ["luxury_goods", "ai_cores"],    # Only these ids can be bought/sold.
 *                 "message": "The message for why this demand class can't be sold. %s is replaced with the corresponding value from 'display_names'."
 *             }
 *         },
 *         "tags" {                 # OPTIONAL: If present, limits on tags on items that can be bought/sold.
 *             "blacklist": {           # OPTIONAL: If present, items with these tags cannot be bought/sold.
 *                 "ids": ["colony_item", "expensive"],                 # Items with these tags can't be bought/sold.
 *                 "display_names": ["colony items", "expensive items"] # Display names for those tags (the game doesn't have them).
 *                 "message": "The message for why this demand class can't be sold. %s is replaced with the corresponding value from 'display_names'."
 *             }
 *             "whitelist": {           # OPTIONAL: If present, only demand classes in the whitelist can be bought/sold.
 *                 "ids": ["colony_item", "expensive"],                 # Only items with these tags can be bought/sold.
 *                 "display_names": ["colony items", "expensive items"] # Display names for those tags (the game doesn't have them).
 *                 "message": "The message for why this demand class can't be sold. %s is replaced with the corresponding value from 'display_names'."
 *             }
 *         },
 *         "ships": {               # OPTIONAL: If present, the submarket sells ships as defined here.
 *              "quality_bonus": 1      # OPTIONAL: How much better the quality of ships is here than the parent market.
 *              "dp_per_size": {        # OPTIONAL: How many DP of ships to generate based on market size.
 *                  "tanker": 1             # OPTIONAL: Adds this many DP of tankers per market size.
 *                  "combat": 1             # OPTIONAL: Adds this many DP of combat ships per market size.
 *                  "liner": 1              # OPTIONAL: Adds this many DP of liners per market size.
 *                  "utility": 1            # OPTIONAL: Adds this many DP of utility ships per market size.
 *                  "freighter": 1          # OPTIONAL: Adds this many DP of freighters per market size.
 *                  "transport": 1          # OPTIONAL: Adds this many DP of transports per market size.
 *              },
 *              "dp_static": {}         # OPTIONAL: How many DP of ships to generate regardless of market size.
 *                                      # Uses the categories from `dp_per_size`.
 *              "static": [             # OPTIONAL: What ship variants should always be available for sale?
 *                  "onslaught_Elite", "eagle_Assault"
 *               ],
 *               "cull_fraction": 0.5   # OPTIONAL: Removes this fraction of ships after generating.
 *                                      # Useful if you need a high DP cap to spawn large ships but only want a few,
 *                                      # or want your static ships to not always appear.
 *         }
 *     }
 * </pre>
 */
public class HyperLibSubmarket extends BaseSubmarketPlugin {
    public static float DEFAULT_TARIFF = -1f;
    /// Defaults to parent market tariff.
    public static boolean DEFAULT_IN_ECONOMY = false;
    /// Defaults to no participation in sector economy.
    public static String DEFAULT_MESSAGE = "You cannot trade this item with %s";
    public static String DEFAULT_NO_BUY = null;
    /// By default, you can always buy.
    public static String DEFAULT_NO_SELL = null;
    /// By default, you can always sell.
    protected transient boolean loadedJSON = false;
    /// Has the JSON file been loaded this session?

    public static String capFirst(String string) {
        return string.substring(0, 1).toUpperCase() + string.substring(1);
    }

    /**
     * Puts a whitelist or blacklist JSON dictionary into storage.
     *
     * @param limit The name of the relevant limit.
     * @param map   The map to store things in.
     */
    public void loadWhiteOrBlacklist(Limit limit, SensibleHashMap map) {
        if (map == null) return;

        messagesMap.put(limit, map.getString("message"));
        limitMap.put(limit, map.getStringSet("ids"));

        List<String> listDisplayNames = map.getStringListOrDefault("display_names", null);
        if (listDisplayNames != null) {
            List<String> listIds = map.getStringList("ids");

            for (int i = 0; i < listDisplayNames.size(); i++) {
                displayNamesMap.put(listIds.get(i), listDisplayNames.get(i));
            }
        }
    }

    /**
     * Loads the JSON for this submarket.
     *
     * @throws JSONException
     * @throws IOException
     */
    public void loadJSON() throws JSONException, IOException {
        SensibleHashMap categoryMap, subcategoryMap;

        JSONObject json = Global.getSettings().loadJSON("data/campaign/submarkets/" + getId() + ".json", true);
        SensibleHashMap jsonMap = SensibleHashMap.fromJSON(json);

        Set<String> commodityIds = new HashSet<>(Global.getSector().getEconomy().getAllCommodityIds());

        loadedJSON = true;
        messagesMap = new HashMap<>();
        displayNamesMap = new HashMap<>();
        limitMap = new HashMap<>();
        dpPerSize = new HashMap<>();
        dpStatic = new HashMap<>();
        tierPerSize = new HashMap<>();
        itemCountPerSize = new HashMap<>();
        itemCountStatic = new HashMap<>();
        itemsStatic = new HashMap<>();
        itemCullFraction = new HashMap<>();

        messageGeneric = jsonMap.getStringOrDefault("message", DEFAULT_MESSAGE);
        messageNoSell = jsonMap.getStringOrDefault("no_sell_message", DEFAULT_NO_SELL);
        messageNoBuy = jsonMap.getStringOrDefault("no_buy_message", DEFAULT_NO_BUY);
        inEconomy = jsonMap.getBoolOrDefault("in_economy", DEFAULT_IN_ECONOMY);
        tariff = jsonMap.getFloatOrDefault("tariff", DEFAULT_TARIFF);

        Global.getLogger(HyperLibSubmarket.class).info("Dict: " + jsonMap);

        categoryMap = jsonMap.getDictOrDefault("ships", null);
        if (categoryMap != null) {
            shipQualityBonus = categoryMap.getIntOrDefault("quality_bonus", 0);
            itemCullFraction.put(Items.SHIPS, categoryMap.getFloatOrDefault("cull_fraction", 0f));

            subcategoryMap = categoryMap.getDictOrDefault("dp_per_size", null);
            if (subcategoryMap != null) {
                for (Role role : Role.values()) {
                    float value = subcategoryMap.getFloatOrDefault(role.toString().toLowerCase(), 0f);
                    if (value > 1) dpPerSize.put(role, value);
                }
            }
            subcategoryMap = categoryMap.getDictOrDefault("dp_static", null);
            if (subcategoryMap != null) {
                for (Role role : Role.values()) {
                    float value = subcategoryMap.getFloatOrDefault(role.toString().toLowerCase(), 0f);
                    if (value > 1) dpStatic.put(role, value);
                }
            }
            if (categoryMap.containsKey("static")) {
                itemsStatic.put(Items.SHIPS, categoryMap.getStringList("static"));
            }
        }
        categoryMap = jsonMap.getDictOrDefault("weapons", null);
        if (categoryMap != null) {
            itemCullFraction.put(Items.WEAPONS, categoryMap.getFloatOrDefault("cull_fraction", 0f));
            if (categoryMap.containsKey("count_per_size")) {
                itemCountPerSize.put(Items.WEAPONS, new ArrayList<>(categoryMap.getIntList("count_per_size")));
            }
            if (categoryMap.containsKey("count_static")) {
                itemCountStatic.put(Items.WEAPONS, categoryMap.getInt("count_static"));
            }
            if (categoryMap.containsKey("tier_per_size")) {
                tierPerSize.put(Items.WEAPONS, categoryMap.getInt("tier_per_size"));
            }
            if (categoryMap.containsKey("static")) {
                itemsStatic.put(Items.WEAPONS, categoryMap.getStringList("static"));
            }
        }

        Map<String, Map<String, Limit>> limitCategories = Map.of(
                "hulls", Map.of("whitelist", Limit.HULL_ID_WHITELIST, "blacklist", Limit.HULL_ID_BLACKLIST),
                "commodities", Map.of("whitelist", Limit.COMMODITY_ID_WHITELIST, "blacklist", Limit.COMMODITY_ID_BLACKLIST),
                "special_items", Map.of("whitelist", Limit.SPECIAL_ID_WHITELIST, "blacklist", Limit.SPECIAL_ID_BLACKLIST),
                "demand_classes", Map.of("whitelist", Limit.DEMAND_CLASS_WHITELIST, "blacklist", Limit.DEMAND_CLASS_BLACKLIST),
                "tags", Map.of("whitelist", Limit.TAG_WHITELIST, "blacklist", Limit.TAG_BLACKLIST)
        );

        for (Map.Entry<String, Map<String, Limit>> entry : limitCategories.entrySet()) {
            categoryMap = jsonMap.getDictOrDefault(entry.getKey(), new SensibleHashMap());
            if (categoryMap != null) {
                loadWhiteOrBlacklist(entry.getValue().get("whitelist"), categoryMap.getDictOrDefault("whitelist", null));
                loadWhiteOrBlacklist(entry.getValue().get("blacklist"), categoryMap.getDictOrDefault("blacklist", null));
            }
        }
    }

    public @Nullable String getMessageNoBuy() {
        return messageNoBuy;
    }

    public @Nullable String getMessageNoSell() {
        return messageNoSell;
    }

    public String getMessageGeneric() {
        return capFirst(messageGeneric);
    }

    public String getMessageGeneric(Object object) {
        return capFirst(messageGeneric.replace("%s", object.toString()));
    }

    public String getMessage(Limit limit) {
        return capFirst(messagesMap.getOrDefault(limit, messageGeneric));
    }

    public String getMessage(Limit limit, Object object) {
        return capFirst(messagesMap.getOrDefault(limit, messageGeneric).replace("%s", object.toString()));
    }

    public String getDisplayName(String id) {
        return displayNamesMap.getOrDefault(id, id);
    }

    public Set<String> getLimitSet(Limit limit) {
        return limitMap.getOrDefault(limit, Set.of());
    }

    protected transient Map<Items, List<Integer>> itemCountPerSize;
    /// The number of items generated in a category given market size.

    protected transient Map<Items, Integer> itemCountStatic;
    /// The number of items generated in a category.

    protected transient Map<Items, Integer> tierPerSize;
    /// The tier of items generated given market siz.e

    protected transient Map<Role, Float> dpPerSize, dpStatic;
    /// DP scaling for generated fleets.

    protected transient Map<Items, List<String>> itemsStatic;
    /// Items always available for sale.

    protected transient Map<Items, Float> itemCullFraction;
    /// This fraction of items in the category are culled.

    protected transient float tariff;
    /// Tariff the player pays on purchases/sales.

    protected transient int shipQualityBonus;
    /// Bonus to ship quality relative to parent market.

    protected transient boolean inEconomy;
    /// Does this participate in the sector economy?

    protected transient String messageGeneric;
    /// The 'none of the above' message on things the player can't sell.

    protected transient String messageNoBuy;
    /// The message shown when the player tries to buy. If null, buying is fine.

    protected transient String messageNoSell;
    /// The message shown when the player tries to sell. If null, selling is fine.

    protected transient Map<Limit, String> messagesMap;
    /// The map of messages given when a transaction is limited.

    protected transient Map<String, String> displayNamesMap;
    /// The map of display names for Ids with no name themselves (e.g. tags, demand categories).

    protected transient Map<Limit, Set<String>> limitMap;
    /// The map of parameters for each limit category.

    public HyperLibSubmarket() {
        super();
        Global.getLogger(HyperLibSubmarket.class).info(
                "Initialiser: Parent market is " + this.getMarket() + ", submarket ID is " + getId()
        );
    }

    /**
     * The types of restriction by which a transfer can be illegal.
     */
    public enum Limit {
        HULL_ID_BLACKLIST, HULL_ID_WHITELIST,
        COMMODITY_ID_BLACKLIST, COMMODITY_ID_WHITELIST,
        DEMAND_CLASS_BLACKLIST, DEMAND_CLASS_WHITELIST,
        SPECIAL_ID_BLACKLIST, SPECIAL_ID_WHITELIST,
        TAG_WHITELIST, TAG_BLACKLIST,
    }

    /**
     * Types of item
     */
    public enum Items {
        WEAPONS, FIGHTERS, SHIPS
    }

    /**
     * High-level ship roles used by fleet generation.
     */
    public enum Role {
        TANKER, UTILITY, COMBAT, FREIGHTER, LINER, TRANSPORT
    }

    /**
     * Gets the Id of the submarket, for loading from the right JSON.
     *
     * @return The Id of the submarket this was created from.
     */
    public @Nullable String getId() {
        if (getSubmarket() != null) {
            return getSubmarket().getSpecId();
        }
        return null;
    }

    @Override
    public void init(SubmarketAPI submarket) {
        super.init(submarket);
    }

    /**
     * Gets the tariff paid on sales and purchases on this submarket.
     *
     * @return The specified tariff, or parent market tariff if none.
     */
    @Override
    public float getTariff() {
        if (tariff >= 0f) {
            return tariff;
        } else {
            return getSubmarket().getMarket().getTariff().getModifiedValue();
        }
    }

    @Override
    public String getTooltipAppendix(CoreUIAPI ui) {
        RepLevel level = market.getFaction().getRelationshipLevel(Global.getSector().getFaction(Factions.PLAYER));
        return super.getTooltipAppendix(ui);
    }

    @Override
    public boolean isEnabled(CoreUIAPI ui) {
        return true;
    }

    /**
     * Every time the player visits, clears the auto-generated supplies and generates a custom set based on the JSON.
     *
     * @author Shoi (original, from Arma Armatura).
     * @author Toaster (modified).
     */
    @Override
    public void updateCargoPrePlayerInteraction() {
        if (!loadedJSON) {
            // On new game, it'll sometimes try to generate the cargo before the parent submarket is properly initialised.
            if (getId() == null) {
                sinceLastCargoUpdate = 999f;
                return;
            }

            try {
                loadJSON();
            } catch (JSONException e) {
                throw new RuntimeException(e);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            loadedJSON = true;
        }
        ;

        sinceLastCargoUpdate = 0f;

        if (okToUpdateShipsAndWeapons()) {
            int size = this.submarket.getMarket().getSize();
            sinceSWUpdate = 0f;

            // Clear the auto-generated ships
            this.getCargo().getMothballedShips().clear();

            // Add any static ships
            for (String id : itemsStatic.getOrDefault(Items.SHIPS, List.of())) {
                this.getCargo().addMothballedShip(FleetMemberType.SHIP, id, null);
            }

            // Add ships based on the faction doctrine
            addShips(
                    this.submarket.getFaction().getId(),
                    dpStatic.getOrDefault(Role.COMBAT, 0f) + size * dpPerSize.getOrDefault(Role.COMBAT, 0f),
                    dpStatic.getOrDefault(Role.FREIGHTER, 0f) + size * dpPerSize.getOrDefault(Role.FREIGHTER, 0f),
                    dpStatic.getOrDefault(Role.TANKER, 0f) + size * dpPerSize.getOrDefault(Role.TANKER, 0f),
                    dpStatic.getOrDefault(Role.TRANSPORT, 0f) + size * dpPerSize.getOrDefault(Role.TRANSPORT, 0f),
                    dpStatic.getOrDefault(Role.LINER, 0f) + size * dpPerSize.getOrDefault(Role.LINER, 0f),
                    dpStatic.getOrDefault(Role.UTILITY, 0f) + size * dpPerSize.getOrDefault(Role.UTILITY, 0f),
                    null,
                    shipQualityBonus,
                    ShipPickMode.PRIORITY_THEN_ALL,
                    this.submarket.getFaction().getDoctrine().clone()
            );
            // Then delete a random set of the ships
            pruneShips(1f - itemCullFraction.getOrDefault(Items.SHIPS, 0f));

            // Clear the existing weapon stockpile, then add a small number of weapons
            pruneWeapons(0f);
            if (itemCountStatic.containsKey(Items.WEAPONS) || itemCountPerSize.containsKey(Items.WEAPONS)) {
                addWeapons(
                        itemCountStatic.getOrDefault(Items.WEAPONS, 0) + size * itemCountPerSize.getOrDefault(Items.WEAPONS, List.of(0, 0)).get(0),
                        itemCountStatic.getOrDefault(Items.WEAPONS, 0) + size * itemCountPerSize.getOrDefault(Items.WEAPONS, List.of(0, 0)).get(1),
                        size * tierPerSize.getOrDefault(Items.WEAPONS, 0),
                        submarket.getFaction().getId()
                );
            }
            if (itemCountStatic.containsKey(Items.FIGHTERS) || itemCountPerSize.containsKey(Items.FIGHTERS)) {
                addFighters(
                        itemCountStatic.getOrDefault(Items.FIGHTERS, 0) + size * itemCountPerSize.getOrDefault(Items.FIGHTERS, List.of(0, 0)).get(0),
                        itemCountStatic.getOrDefault(Items.FIGHTERS, 0) + size * itemCountPerSize.getOrDefault(Items.FIGHTERS, List.of(0, 0)).get(1),
                        size * tierPerSize.getOrDefault(Items.FIGHTERS, 0),
                        submarket.getFaction().getId()
                );
            }
            pruneWeapons(1f - itemCullFraction.getOrDefault(Items.WEAPONS, 0f));
        }
        getCargo().sort();
    }

    /**
     * Filters out banned special items & weapons.
     *
     * @param stack  Stack of cargo items to check.
     * @param action What's the player trying to do with them?
     * @return True if it's blocked on this submarket.
     */
    @Override
    public boolean isIllegalOnSubmarket(CargoStackAPI stack, TransferAction action) {
        if (action == TransferAction.PLAYER_BUY && getMessageNoBuy() != null) {
            return true;
        } else if (action == TransferAction.PLAYER_SELL && getMessageNoSell() != null) {
            return true;

        } else if (stack.isSpecialStack()) {
            String specialId = stack.getSpecialItemSpecIfSpecial().getId();
            if (getLimitSet(Limit.SPECIAL_ID_BLACKLIST).contains(specialId)) {
                return true;
            } else if (!getLimitSet(Limit.SPECIAL_ID_WHITELIST).isEmpty() && getLimitSet(Limit.SPECIAL_ID_WHITELIST).contains(specialId)) {
                return true;

            } else if (!getLimitSet(Limit.TAG_BLACKLIST).isEmpty() || !getLimitSet(Limit.TAG_WHITELIST).isEmpty()) {
                HashSet<String> tags = new HashSet<>(stack.getSpecialItemSpecIfSpecial().getTags());
                if (new HashSet<>(tags).removeAll(getLimitSet(Limit.TAG_BLACKLIST))) {  // True if any blacklist tags found
                    return true;
                } else if (!new HashSet<>(tags).removeAll(getLimitSet(Limit.TAG_WHITELIST))) {  // True if no whitelist tags found
                    return true;
                }
            }
        }
        return super.isIllegalOnSubmarket(stack, action);
    }

    /**
     * Filters out commodities that are from a banned demand class.
     *
     * @param commodityId The ID of the commodity.
     * @param action      What the player is trying to do.
     * @return True if it's a banned commodity.
     */
    @Override
    public boolean isIllegalOnSubmarket(String commodityId, TransferAction action) {
        String demandClass = Global.getSettings().getCommoditySpec(commodityId).getDemandClass();
        if (action == TransferAction.PLAYER_BUY && getMessageNoBuy() != null) {
            return true;
        } else if (action == TransferAction.PLAYER_SELL && getMessageNoSell() != null) {
            return true;
        } else if (!getLimitSet(Limit.COMMODITY_ID_WHITELIST).isEmpty() && getLimitSet(Limit.COMMODITY_ID_WHITELIST).contains(commodityId)) {
            return true;
        } else if (getLimitSet(Limit.COMMODITY_ID_BLACKLIST).contains(commodityId)) {
            return true;
        } else if (getLimitSet(Limit.DEMAND_CLASS_BLACKLIST).contains(demandClass)) {
            return true;
        } else if (!getLimitSet(Limit.DEMAND_CLASS_WHITELIST).isEmpty() && getLimitSet(Limit.DEMAND_CLASS_WHITELIST).contains(demandClass)) {
            return true;

        } else if (!getLimitSet(Limit.TAG_BLACKLIST).isEmpty() || !getLimitSet(Limit.TAG_WHITELIST).isEmpty()) {
            HashSet<String> tags = new HashSet<>(Global.getSettings().getCommoditySpec(commodityId).getTags());
            if (new HashSet<>(tags).removeAll(getLimitSet(Limit.TAG_BLACKLIST))) {  // True if any blacklist tags found
                return true;
            } else if (!new HashSet<>(tags).removeAll(getLimitSet(Limit.TAG_WHITELIST))) {  // True if no whitelist tags found
                return true;
            }

        }
        return super.isIllegalOnSubmarket(commodityId, action);
    }

    /**
     * Filters out ships of banned hulls or with forbidden tags.
     *
     * @param member The fleet member being transferred.
     * @param action What the player is trying to do.
     * @return True if it's a banned ship.
     */
    @Override
    public boolean isIllegalOnSubmarket(FleetMemberAPI member, TransferAction action) {
        if (action == TransferAction.PLAYER_BUY && getMessageNoBuy() != null) {
            return true;
        } else if (action == TransferAction.PLAYER_SELL && getMessageNoSell() != null) {
            return true;

        } else if (getLimitSet(Limit.HULL_ID_BLACKLIST).contains(member.getHullId())) {
            return true;
        } else if (!getLimitSet(Limit.HULL_ID_WHITELIST).isEmpty() && getLimitSet(Limit.HULL_ID_WHITELIST).contains(member.getHullId())) {
            return true;

        } else if (!getLimitSet(Limit.TAG_BLACKLIST).isEmpty() || !getLimitSet(Limit.TAG_WHITELIST).isEmpty()) {
            HashSet<String> tags = new HashSet<>();
            tags.addAll(member.getVariant().getTags());
            tags.addAll(member.getHullSpec().getTags());
            if (new HashSet<>(tags).removeAll(getLimitSet(Limit.TAG_BLACKLIST))) {  // True if any blacklist tags found
                return true;
            } else if (!new HashSet<>(tags).removeAll(getLimitSet(Limit.TAG_WHITELIST))) {  // True if no whitelist tags found
                return true;
            }
        }
        return super.isIllegalOnSubmarket(member, action);
    }

    /**
     * Tooltip message for illegal ship transfers.
     *
     * @param member The fleet member being transferred.
     * @param action The transfer action.
     * @return String explaining why.
     */
    @Override
    public String getIllegalTransferText(FleetMemberAPI member, TransferAction action) {
        String hullId = member.getHullId();

        if (action == TransferAction.PLAYER_BUY && getMessageNoBuy() != null) {
            return getMessageNoBuy();
        } else if (action == TransferAction.PLAYER_SELL && getMessageNoSell() != null) {
            return getMessageNoSell();

        } else if (!getLimitSet(Limit.HULL_ID_WHITELIST).isEmpty() && !getLimitSet(Limit.HULL_ID_WHITELIST).contains(hullId)) {
            return getMessage(Limit.HULL_ID_BLACKLIST, member.getHullSpec().getHullName());
        } else if (getLimitSet(Limit.HULL_ID_BLACKLIST).contains(hullId)) {
            return getMessage(Limit.HULL_ID_BLACKLIST, member.getHullSpec().getHullName());

        } else if (!getLimitSet(Limit.TAG_BLACKLIST).isEmpty() || !getLimitSet(Limit.TAG_WHITELIST).isEmpty()) {
            HashSet<String> tags = new HashSet<>();
            tags.addAll(member.getVariant().getTags());
            tags.addAll(member.getHullSpec().getTags());
            if (new HashSet<>(tags).removeAll(getLimitSet(Limit.TAG_BLACKLIST))) {  // True if any blacklist tags found
                return getMessage(Limit.TAG_BLACKLIST);
            } else if (!new HashSet<>(tags).removeAll(getLimitSet(Limit.TAG_WHITELIST))) {  // True if no whitelist tags found
                return getMessage(Limit.TAG_WHITELIST);
            }
        }
        return super.getIllegalTransferText(member, action);
    }

    /**
     * Tooltip message for illegal item transfers.
     * <p>
     * Custom response per blacklisted class.
     *
     * @param stack  The illegal stack of items.
     * @param action The action the player is performing.
     * @return String explaining why.
     */
    @Override
    public String getIllegalTransferText(CargoStackAPI stack, TransferAction action) {
        if (action == TransferAction.PLAYER_BUY && getMessageNoBuy() != null) {
            return getMessageNoBuy();
        } else if (action == TransferAction.PLAYER_SELL && getMessageNoSell() != null) {
            return getMessageNoSell();

        } else if (stack.isSpecialStack()) {
            String specialId = stack.getSpecialItemSpecIfSpecial().getId();
            if (getLimitSet(Limit.SPECIAL_ID_BLACKLIST).contains(specialId)) {
                return getMessage(Limit.SPECIAL_ID_BLACKLIST, stack.getSpecialItemSpecIfSpecial().getName());
            } else if (!getLimitSet(Limit.SPECIAL_ID_WHITELIST).isEmpty() && getLimitSet(Limit.SPECIAL_ID_WHITELIST).contains(specialId)) {
                return getMessage(Limit.SPECIAL_ID_WHITELIST, stack.getSpecialItemSpecIfSpecial().getName());

            } else if (!getLimitSet(Limit.TAG_BLACKLIST).isEmpty() || !getLimitSet(Limit.TAG_WHITELIST).isEmpty()) {
                HashSet<String> tags = new HashSet<>(stack.getSpecialItemSpecIfSpecial().getTags());
                if (new HashSet<>(tags).removeAll(getLimitSet(Limit.TAG_BLACKLIST))) {  // True if any blacklist tags found
                    return getMessage(Limit.TAG_BLACKLIST);
                } else if (!new HashSet<>(tags).removeAll(getLimitSet(Limit.TAG_WHITELIST))) {  // True if no whitelist tags found
                    return getMessage(Limit.TAG_WHITELIST);
                }
            }

        } else if (stack.isCommodityStack()) {
            String commodityId = stack.getCommodityId();
            String demandClass = Global.getSettings().getCommoditySpec(stack.getCommodityId()).getDemandClass();

            if (!getLimitSet(Limit.COMMODITY_ID_WHITELIST).isEmpty() && getLimitSet(Limit.COMMODITY_ID_WHITELIST).contains(commodityId)) {
                return getMessage(Limit.COMMODITY_ID_WHITELIST, getDisplayName(demandClass));
            } else if (getLimitSet(Limit.COMMODITY_ID_BLACKLIST).contains(commodityId)) {
                return getMessage(Limit.COMMODITY_ID_BLACKLIST, getDisplayName(demandClass));

            } else if (getLimitSet(Limit.DEMAND_CLASS_BLACKLIST).contains(demandClass)) {
                return getMessage(Limit.DEMAND_CLASS_BLACKLIST, getDisplayName(demandClass));
            } else if (!getLimitSet(Limit.DEMAND_CLASS_WHITELIST).isEmpty() && getLimitSet(Limit.DEMAND_CLASS_WHITELIST).contains(demandClass)) {
                return getMessage(Limit.DEMAND_CLASS_WHITELIST, getDisplayName(demandClass));

            } else if (!getLimitSet(Limit.TAG_BLACKLIST).isEmpty() || !getLimitSet(Limit.TAG_WHITELIST).isEmpty()) {
                HashSet<String> tags = new HashSet<>(Global.getSettings().getCommoditySpec(commodityId).getTags());
                if (new HashSet<>(tags).removeAll(getLimitSet(Limit.TAG_BLACKLIST))) {  // True if any blacklist tags found
                    return getMessage(Limit.TAG_BLACKLIST);
                } else if (!new HashSet<>(tags).removeAll(getLimitSet(Limit.TAG_WHITELIST))) {  // True if no whitelist tags found
                    return getMessage(Limit.TAG_WHITELIST);
                }
            }
        }
        return getMessageGeneric(getSubmarket().getName());
    }

    /**
     * Does this submarket take part in the global economy?
     *
     * @return By default, false.
     */
    @Override
    public boolean isParticipatesInEconomy() {
        return false;
    }
}

