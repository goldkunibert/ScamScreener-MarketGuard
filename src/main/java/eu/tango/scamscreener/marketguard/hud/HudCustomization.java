package eu.tango.scamscreener.marketguard.hud;

import eu.midnightdust.lib.config.MidnightConfigListWidget;
import eu.midnightdust.lib.config.MidnightConfigScreen;
import eu.tango.scamscreener.marketguard.MarketGuardConfig;
import eu.tango.scamscreener.marketguard.screen.HudScreenGroup;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class HudCustomization {
    private static volatile String currentScreenTitle;
    public enum HudId {
        AUCTION_PRICE("auctionPrice"),
        PLAYER("player"),
        TRADE_GUARD("tradeGuard"),
        MINION_PROFIT("minionProfit"),
        FORGE_PROFIT("forgeProfit"),
        PROFIT_TRACKER("profitTracker");

        private final String key;

        HudId(String key) {
            this.key = key;
        }

        public String key() {
            return key;
        }

    }

    private HudCustomization() {}

    public static void setCurrentScreenTitle(String title) {
        currentScreenTitle = title;
    }

    public static boolean visible(HudId hud, String title) {
        Set<HudScreenGroup> current = HudScreenGroup.classify(title);
        for (HudScreenGroup group : current) {
            if (screens(hud).contains(group.key())) {
                return true;
            }
        }
        return false;
    }

    public static boolean visibleOnCurrentScreen(HudId hud) {
        return visible(hud, currentScreenTitle);
    }

    public static List<String> rows(HudId hud) {
        List<String> configured = rowValues(hud);
        List<String> defaults = availableRows(hud);
        List<String> result = new ArrayList<>();
        for (String value : configured) {
            if (value == null || value.isBlank()) {
                continue;
            }
            String id = value.startsWith("!") ? value.substring(1) : value;
            if (defaults.contains(id) && !value.startsWith("!")) {
                result.add(id);
            }
        }
        for (String id : defaults) {
            if (!configured.contains(id) && !configured.contains("!" + id)) {
                result.add(id);
            }
        }
        return result;
    }

    private static boolean rowEnabled(HudId hud, String row) {
        return rowValues(hud).stream().noneMatch(value -> value.equals("!" + row));
    }

    static boolean screenEnabled(HudId hud, HudScreenGroup group) {
        return screens(hud).contains(group.key());
    }

    static List<String> editableRows(HudId hud, boolean enabled) {
        return rowValues(hud).stream()
                .map(value -> value.startsWith("!") ? value.substring(1) : value)
                .filter(availableRows(hud)::contains)
                .filter(row -> rowEnabled(hud, row) == enabled)
                .toList();
    }

    public static void toggleScreen(HudId hud, HudScreenGroup group) {
        List<String> values = screens(hud);
        String key = group.key();
        if (!values.remove(key)) {
            values.add(key);
        }
        MarketGuardConfig.save();
    }

    static void placeRow(HudId hud, String row, boolean enabled, int target) {
        if (!defaultRows(hud).contains(row)) {
            return;
        }

        List<String> visibleRows = new ArrayList<>(editableRows(hud, true));
        List<String> hiddenRows = new ArrayList<>(editableRows(hud, false));
        visibleRows.remove(row);
        hiddenRows.remove(row);

        List<String> destination = enabled ? visibleRows : hiddenRows;
        destination.add(Math.max(0, Math.min(target, destination.size())), row);

        List<String> values = rowValues(hud);
        List<String> available = availableRows(hud);
        List<String> otherRows = values.stream()
                .filter(value -> !available.contains(value.startsWith("!") ? value.substring(1) : value))
                .toList();
        values.clear();
        values.addAll(visibleRows);
        hiddenRows.forEach(hidden -> values.add("!" + hidden));
        values.addAll(otherRows);
        MarketGuardConfig.save();
    }

    static Component example(HudId hud, String row) {
        return switch (hud) {
            case AUCTION_PRICE -> switch (row) {
                case "item" -> Component.literal("6th Anniversary Barn Skin").withStyle(ChatFormatting.GOLD);
                case "auction" -> Component.literal("Auction: ").withStyle(ChatFormatting.GRAY)
                        .append(Component.literal("15,000,000 coins").withStyle(ChatFormatting.GOLD));
                case "lowest_bin" -> Component.literal("Lowest BIN: ").withStyle(ChatFormatting.GRAY)
                        .append(Component.literal("15,400,000 coins").withStyle(ChatFormatting.WHITE));
                case "advice" -> Component.literal("Fair price").withStyle(ChatFormatting.GREEN)
                        .append(Component.literal(" (-400,000 coins)").withStyle(ChatFormatting.GRAY));
                case "volatility" -> Component.literal("Price trend: rising 35% vs. last month").withStyle(ChatFormatting.YELLOW);
                case "liquidity" -> Component.literal("Hard to resell on the Bazaar").withStyle(ChatFormatting.YELLOW);
                case "stale" -> Component.literal("Prices may be outdated").withStyle(ChatFormatting.YELLOW);
                default -> Component.literal(row);
            };
            case PLAYER -> switch (row) {
                case "name" -> Component.literal("TangoDev").withStyle(ChatFormatting.WHITE);
                case "seen" -> Component.literal("Seen 12 times").withStyle(ChatFormatting.DARK_GRAY);
                case "scamscreener" -> Component.translatable("marketguard.hud.scamscreener.no_entry").withStyle(ChatFormatting.GRAY);
                case "status" -> Component.literal("Player not found").withStyle(ChatFormatting.GRAY);
                case "wealth" -> Component.literal("Bank + purse: 45,200,000").withStyle(ChatFormatting.GOLD);
                case "profile_value" -> Component.literal("Est. net worth: ~231,400,000").withStyle(ChatFormatting.GOLD);
                case "first_join" -> Component.literal("First joined: 14.03.2021").withStyle(ChatFormatting.DARK_GRAY);
                case "profile" -> Component.literal("Profile: Apple").withStyle(ChatFormatting.GRAY);
                case "value_coverage" -> Component.literal("Priced gear: 8/8 items").withStyle(ChatFormatting.GREEN);
                case "value_missing" -> Component.literal("Not included: inventory, pets").withStyle(ChatFormatting.DARK_GRAY);
                case "value_status" -> Component.literal("Some values may be outdated").withStyle(ChatFormatting.YELLOW);
                case "museum" -> Component.literal("Museum: 185,000,000, appraised").withStyle(ChatFormatting.GOLD);
                case "museum_items" -> Component.literal("Museum: 143 exhibits (6 special)").withStyle(ChatFormatting.GRAY);
                case "armor" -> Component.literal("Armor: Necron's Helmet, Necron's Chestplate +2").withStyle(ChatFormatting.GRAY);
                case "equipment" -> Component.literal("Equipment: Lava Shell Necklace, Molten Cloak +2").withStyle(ChatFormatting.GRAY);
                case "pet" -> Component.literal("Active pet: Legendary Ender Dragon").withStyle(ChatFormatting.LIGHT_PURPLE);
                case "skills" -> Component.literal("Skills: Farming 40 | Mining 50 | Combat 45 | Avg 48.7 (9 skills)").withStyle(ChatFormatting.GREEN);
                case "uuid" -> Component.literal("UUID: 466d01f5f2b84b4d9a2c1e8f7d3b5a90").withStyle(ChatFormatting.DARK_GRAY);
                case "data" -> Component.literal("Updated 16:50").withStyle(ChatFormatting.DARK_GRAY);
                default -> Component.literal(row);
            };
            case TRADE_GUARD -> switch (row) {
                case "own_value" -> Component.literal("Your item value: 18.0m coins").withStyle(ChatFormatting.GOLD);
                case "partner_value" -> Component.literal("Their item value: 13.5m coins").withStyle(ChatFormatting.GOLD);
                case "difference" -> Component.literal("You give 4.5m coins more in items.").withStyle(ChatFormatting.YELLOW);
                case "unpriced" -> Component.literal("Unpriced item stacks — yours: 0, theirs: 1").withStyle(ChatFormatting.YELLOW);
                case "data" -> Component.literal("Data: partial item values; coin offers are not included.").withStyle(ChatFormatting.YELLOW);
                case "warning" -> Component.literal("Warning: item-side disadvantage 4.5m coins (25.0%).").withStyle(ChatFormatting.RED);
                default -> Component.literal(row);
            };
            case MINION_PROFIT -> switch (row) {
                case "held_coins" -> Component.literal("Held coins: 125,400").withStyle(ChatFormatting.GOLD);
                case "profit" -> Component.literal("Storage sells for: 2,800,000+").withStyle(ChatFormatting.GOLD);
                case "missing" -> Component.literal("1 stack has no Bazaar price").withStyle(ChatFormatting.GRAY);
                case "loading" -> Component.literal("Loading Bazaar prices...").withStyle(ChatFormatting.GRAY);
                case "unavailable" -> Component.literal("Bazaar prices unavailable").withStyle(ChatFormatting.RED);
                case "stale" -> Component.literal("Prices may be outdated").withStyle(ChatFormatting.YELLOW);
                default -> Component.literal(row);
            };
            case FORGE_PROFIT -> switch (row) {
                case "profit" -> Component.literal("Forge items sell for: 4,600,000 coins").withStyle(ChatFormatting.GOLD);
                case "missing" -> Component.literal("1 stack has no Bazaar price").withStyle(ChatFormatting.GRAY);
                case "loading" -> Component.literal("Loading Bazaar prices...").withStyle(ChatFormatting.GRAY);
                case "unavailable" -> Component.literal("Bazaar prices unavailable").withStyle(ChatFormatting.RED);
                case "stale" -> Component.literal("Prices may be outdated").withStyle(ChatFormatting.YELLOW);
                default -> Component.literal(row);
            };
            case PROFIT_TRACKER -> switch (row) {
                case "bazaar" -> Component.literal("Bazaar: +2,400,000").withStyle(ChatFormatting.GREEN);
                case "auction_house" -> Component.literal("Auction House: +8,100,000").withStyle(ChatFormatting.GREEN);
                case "minion" -> Component.literal("Minion: +1,700,000").withStyle(ChatFormatting.GREEN);
                case "interest" -> Component.literal("Interest: +250,000").withStyle(ChatFormatting.GREEN);
                case "allowance" -> Component.literal("Allowance: +50,000").withStyle(ChatFormatting.GREEN);
                case "total" -> Component.literal("Total: +12,500,000").withStyle(ChatFormatting.GOLD);
                default -> Component.literal(row);
            };
        };
    }

    public static void reset(HudId hud) {
        screens(hud).clear();
        screens(hud).addAll(defaultScreens(hud));
        rowValues(hud).clear();
        rowValues(hud).addAll(defaultRows(hud));
        MarketGuardConfig.save();
    }

    public static void addSettingsButtons(String category, MidnightConfigListWidget list, MidnightConfigScreen screen) {
        if (!"hud".equals(category)) {
            return;
        }

        var entryInfo = MarketGuardConfig.hudEntryInfo();
        int controlsX = list.getWidth() - 185;
        for (HudId hud : HudId.values()) {
            list.addButton(List.of(Button.builder(
                    Component.translatable("marketguard.hud.edit"),
                    button -> HudEditorScreen.open(screen, hud)
            ).bounds(controlsX, 0, 150, 20).build()),
                    Component.translatable("marketguard.hud." + hud.key()), entryInfo);
        }
    }

    private static List<String> screens(HudId hud) {
        return switch (hud) {
            case AUCTION_PRICE -> MarketGuardConfig.auctionPriceHudScreens;
            case PLAYER -> MarketGuardConfig.playerHudScreens;
            case TRADE_GUARD -> MarketGuardConfig.tradeGuardHudScreens;
            case MINION_PROFIT -> MarketGuardConfig.minionProfitHudScreens;
            case FORGE_PROFIT -> MarketGuardConfig.forgeProfitHudScreens;
            case PROFIT_TRACKER -> MarketGuardConfig.profitTrackerHudScreens;
        };
    }

    private static List<String> rowValues(HudId hud) {
        return switch (hud) {
            case AUCTION_PRICE -> MarketGuardConfig.auctionPriceHudRows;
            case PLAYER -> MarketGuardConfig.playerHudRows;
            case TRADE_GUARD -> MarketGuardConfig.tradeGuardHudRows;
            case MINION_PROFIT -> MarketGuardConfig.minionProfitHudRows;
            case FORGE_PROFIT -> MarketGuardConfig.forgeProfitHudRows;
            case PROFIT_TRACKER -> MarketGuardConfig.profitTrackerHudRows;
        };
    }

    private static List<String> defaultScreens(HudId hud) {
        return switch (hud) {
            case AUCTION_PRICE -> MarketGuardConfig.DEFAULT_AUCTION_PRICE_HUD_SCREENS;
            case PLAYER -> MarketGuardConfig.DEFAULT_PLAYER_HUD_SCREENS;
            case TRADE_GUARD -> MarketGuardConfig.DEFAULT_TRADE_GUARD_HUD_SCREENS;
            case MINION_PROFIT -> MarketGuardConfig.DEFAULT_MINION_PROFIT_HUD_SCREENS;
            case FORGE_PROFIT -> MarketGuardConfig.DEFAULT_FORGE_PROFIT_HUD_SCREENS;
            case PROFIT_TRACKER -> MarketGuardConfig.DEFAULT_PROFIT_TRACKER_HUD_SCREENS;
        };
    }

    private static List<String> defaultRows(HudId hud) {
        return switch (hud) {
            case AUCTION_PRICE -> MarketGuardConfig.DEFAULT_AUCTION_PRICE_HUD_ROWS;
            case PLAYER -> MarketGuardConfig.DEFAULT_PLAYER_HUD_ROWS;
            case TRADE_GUARD -> MarketGuardConfig.DEFAULT_TRADE_GUARD_HUD_ROWS;
            case MINION_PROFIT -> MarketGuardConfig.DEFAULT_MINION_PROFIT_HUD_ROWS;
            case FORGE_PROFIT -> MarketGuardConfig.DEFAULT_FORGE_PROFIT_HUD_ROWS;
            case PROFIT_TRACKER -> MarketGuardConfig.DEFAULT_PROFIT_TRACKER_HUD_ROWS;
        };
    }

    private static List<String> availableRows(HudId hud) {
        if (hud != HudId.PLAYER) {
            return defaultRows(hud);
        }

        return switch (MarketGuardConfig.getPlayerHudPreset()) {
            case "trade" -> List.of("name", "seen", "scamscreener", "status");
            case "compact" -> List.of("name", "seen", "scamscreener", "status", "wealth", "profile_value");
            default -> defaultRows(hud);
        };
    }
}
