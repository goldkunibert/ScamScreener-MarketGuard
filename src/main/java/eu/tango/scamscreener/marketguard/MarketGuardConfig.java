package eu.tango.scamscreener.marketguard;

import eu.midnightdust.lib.config.MidnightConfig;
import eu.midnightdust.lib.config.MidnightConfigListWidget;
import eu.midnightdust.lib.config.MidnightConfigScreen;
import eu.midnightdust.lib.config.EntryInfo;
import eu.tango.scamscreener.marketguard.hud.HudCustomization;
import eu.tango.scamscreener.marketguard.auction.AuctionOverbidding;
import eu.tango.scamscreener.marketguard.auction.AuctionUnderbidding;
import eu.tango.scamscreener.marketguard.auction.ReferencePriceBasis;
import eu.tango.scamscreener.marketguard.profittracker.ProfitTrackerResetScreen;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.ArrayList;
import java.util.List;

public final class MarketGuardConfig extends MidnightConfig {
    public static final long DEFAULT_ABSOLUTE_THRESHOLD = 10_000L;
    private static final String PROTECTION = "protection";
    private static final String HUD = "hud";
    private static final String TRACKER = "tracker";
    private static final String GENERAL = "general";
    private static final List<String> SCREEN_KEYS = List.of("ingame", "auction_house", "bin_view", "trade", "profile", "minion", "forge");
    public static final List<String> DEFAULT_AUCTION_PRICE_HUD_SCREENS = List.of("bin_view");
    public static final List<String> DEFAULT_PLAYER_HUD_SCREENS = List.of("trade", "profile", "bin_view");
    public static final List<String> DEFAULT_TRADE_GUARD_HUD_SCREENS = List.of("trade");
    public static final List<String> DEFAULT_MINION_PROFIT_HUD_SCREENS = List.of("minion");
    public static final List<String> DEFAULT_FORGE_PROFIT_HUD_SCREENS = List.of("forge");
    public static final List<String> DEFAULT_PROFIT_TRACKER_HUD_SCREENS = List.of("ingame");
    public static final List<String> DEFAULT_AUCTION_PRICE_HUD_ROWS = List.of("item", "auction", "lowest_bin", "advice", "volatility", "liquidity", "stale");
    public static final List<String> DEFAULT_PLAYER_HUD_ROWS = List.of(
            "name", "seen", "scamscreener", "status", "wealth", "profile_value",
            "first_join", "profile", "value_coverage", "value_missing", "value_status",
            "museum", "museum_items", "armor", "equipment", "pet", "skills", "uuid", "data"
    );
    public static final List<String> DEFAULT_TRADE_GUARD_HUD_ROWS = List.of("own_value", "partner_value", "difference", "unpriced", "data", "warning");
    public static final List<String> DEFAULT_MINION_PROFIT_HUD_ROWS = List.of("held_coins", "profit", "missing", "loading", "unavailable", "stale");
    public static final List<String> DEFAULT_FORGE_PROFIT_HUD_ROWS = List.of("profit", "missing", "loading", "unavailable", "stale");
    public static final List<String> DEFAULT_PROFIT_TRACKER_HUD_ROWS = List.of("bazaar", "auction_house", "minion", "interest", "allowance", "total");
    // Layouts shipped by the 1.5.0 betas (after normalisation); configs still holding one receive the new default.
    @Entry(category = PROTECTION, min = 0, max = 100, isSlider = true)
    public static int underbiddingThreshold = AuctionUnderbidding.DEFAULT_THRESHOLD;
    @Entry(category = PROTECTION, min = 100, max = 200, isSlider = true)
    public static int overbiddingThreshold = AuctionOverbidding.DEFAULT_THRESHOLD;
    @Entry(category = PROTECTION, min = 0, max = Long.MAX_VALUE)
    public static double absoluteThreshold = DEFAULT_ABSOLUTE_THRESHOLD;
    @Entry(category = PROTECTION)
    public static ReferencePriceBasis marketPriceBasis = ReferencePriceBasis.LOWEST_BIN;
    @Entry(category = GENERAL)
    public static boolean debug = false;
    @Entry(category = GENERAL)
    public static boolean updateNotificationsEnabled = true;
    @Entry(category = TRACKER)
    public static boolean warnOnUnmatchedProfitConfirmations = false;
    @Entry(category = HUD)
    public static boolean profitTrackerHudEnabled = false;
    @Entry(category = HUD)
    @Hidden
    public static PlayerHudPreset playerHudPreset = PlayerHudPreset.trade;
    @Entry(category = HUD)
    public static boolean playerHudShowUnavailableRows = false;
    @Entry(category = HUD)
    public static boolean shortNumberFormat = false;

    @Entry(category = HUD)
    @Hidden
    public static List<String> auctionPriceHudScreens = new ArrayList<>(DEFAULT_AUCTION_PRICE_HUD_SCREENS);
    @Entry(category = HUD)
    @Hidden
    public static List<String> playerHudScreens = new ArrayList<>(DEFAULT_PLAYER_HUD_SCREENS);
    @Entry(category = HUD)
    @Hidden
    public static List<String> tradeGuardHudScreens = new ArrayList<>(DEFAULT_TRADE_GUARD_HUD_SCREENS);
    @Entry(category = HUD)
    @Hidden
    public static List<String> minionProfitHudScreens = new ArrayList<>(DEFAULT_MINION_PROFIT_HUD_SCREENS);
    @Entry(category = HUD)
    @Hidden
    public static List<String> forgeProfitHudScreens = new ArrayList<>(DEFAULT_FORGE_PROFIT_HUD_SCREENS);
    @Entry(category = HUD)
    @Hidden
    public static List<String> profitTrackerHudScreens = new ArrayList<>(DEFAULT_PROFIT_TRACKER_HUD_SCREENS);
    @Entry(category = HUD)
    @Hidden
    public static List<String> auctionPriceHudRows = new ArrayList<>(DEFAULT_AUCTION_PRICE_HUD_ROWS);
    @Entry(category = HUD)
    @Hidden
    public static List<String> playerHudRows = new ArrayList<>(DEFAULT_PLAYER_HUD_ROWS);
    @Entry(category = HUD)
    @Hidden
    public static List<String> tradeGuardHudRows = new ArrayList<>(DEFAULT_TRADE_GUARD_HUD_ROWS);
    @Entry(category = HUD)
    @Hidden
    public static List<String> minionProfitHudRows = new ArrayList<>(DEFAULT_MINION_PROFIT_HUD_ROWS);
    @Entry(category = HUD)
    @Hidden
    public static List<String> forgeProfitHudRows = new ArrayList<>(DEFAULT_FORGE_PROFIT_HUD_ROWS);
    @Entry(category = HUD)
    @Hidden
    public static List<String> profitTrackerHudRows = new ArrayList<>(DEFAULT_PROFIT_TRACKER_HUD_ROWS);

    public MarketGuardConfig() {}

    public static void load() {
        MidnightConfig.init(MarketGuard.MOD_ID, MarketGuardConfig.class);
        if (normalizeValues()) {
            save();
        }
    }

    public static void save() {
        if (MidnightConfig.configInstances.containsKey(MarketGuard.MOD_ID)) {
            MidnightConfig.write(MarketGuard.MOD_ID);
        }
    }

    public static int getUnderbiddingThreshold() {
        return underbiddingThreshold;
    }

    public static void setUnderbiddingThreshold(int threshold) {
        if (threshold < 0 || threshold > 100) {
            throw new IllegalArgumentException("underbidding threshold must be between 0 and 100");
        }
        underbiddingThreshold = threshold;
    }

    public static int getOverbiddingThreshold() {
        return overbiddingThreshold;
    }

    public static ReferencePriceBasis getMarketPriceBasis() {
        return marketPriceBasis == null ? ReferencePriceBasis.LOWEST_BIN : marketPriceBasis;
    }

    public static void setMarketPriceBasis(ReferencePriceBasis basis) {
        marketPriceBasis = basis == null ? ReferencePriceBasis.LOWEST_BIN : basis;
    }

    public static void setOverbiddingThreshold(int threshold) {
        if (threshold < 100) {
            throw new IllegalArgumentException("overbidding threshold must be at least 100");
        }
        overbiddingThreshold = threshold;
    }

    public static boolean isDebugEnabled() {
        return debug;
    }

    public static void setDebugEnabled(boolean enabled) {
        debug = enabled;
    }

    public static boolean isUpdateNotificationsEnabled() {
        return updateNotificationsEnabled;
    }

    public static void setUpdateNotificationsEnabled(boolean enabled) {
        updateNotificationsEnabled = enabled;
    }

    public static long getAbsoluteThreshold() {
        return Math.max(0L, Math.round(absoluteThreshold));
    }

    public static void setAbsoluteThreshold(long threshold) {
        if (threshold < 0L) {
            throw new IllegalArgumentException("absolute threshold must not be negative");
        }
        absoluteThreshold = threshold;
    }

    public static boolean isWarnOnUnmatchedProfitConfirmations() {
        return warnOnUnmatchedProfitConfirmations;
    }

    public static void setWarnOnUnmatchedProfitConfirmations(boolean enabled) {
        warnOnUnmatchedProfitConfirmations = enabled;
    }

    public static boolean isProfitTrackerHudEnabled() {
        return profitTrackerHudEnabled;
    }

    public static void setProfitTrackerHudEnabled(boolean enabled) {
        profitTrackerHudEnabled = enabled;
    }

    public static String getPlayerHudPreset() {
        return playerHudPreset.name();
    }

    public static void setPlayerHudPreset(String preset) {
        String normalized = preset == null ? "" : preset.trim().toLowerCase(Locale.ROOT);
        try {
            playerHudPreset = PlayerHudPreset.valueOf(normalized);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("unknown player HUD preset", exception);
        }
    }

    public static boolean isShortNumberFormat() {
        return shortNumberFormat;
    }

    public static void setShortNumberFormat(boolean enabled) {
        shortNumberFormat = enabled;
    }

    public static boolean isPlayerHudShowUnavailableRows() {
        return playerHudShowUnavailableRows;
    }

    public static void setPlayerHudShowUnavailableRows(boolean show) {
        playerHudShowUnavailableRows = show;
    }

    @Override
    public Path getJsonFilePath() {
        return FabricLoader.getInstance().getConfigDir()
                .resolve("scamscreener_marketguard")
                .resolve("config.json");
    }

    @Override
    @SuppressWarnings("deprecation")
    public void writeChanges(String modid) {
        try {
            Files.createDirectories(getJsonFilePath().getParent());
        } catch (IOException exception) {
            MarketGuard.LOGGER.warn("Failed to create MarketGuard config directory", exception);
            return;
        }

        super.writeChanges(modid);
    }

    @Override
    public void onTabInit(String category, MidnightConfigListWidget list, MidnightConfigScreen screen) {
        super.onTabInit(category, list, screen);
        HudCustomization.addSettingsButtons(category, list, screen);
        if (HUD.equals(category)) {
            int controlsX = list.getWidth() - 185;
            list.addButton(List.of(Button.builder(
                            Component.translatable("marketguard.profit_tracker.reset").withStyle(ChatFormatting.RED),
                            button -> ProfitTrackerResetScreen.open(screen)
                    ).bounds(controlsX, 0, 150, 20).build()),
                    Component.translatable("marketguard.profit_tracker.data"), hudEntryInfo());
        }
    }

    public static EntryInfo hudEntryInfo() {
        return new EntryInfo(null, MarketGuard.MOD_ID);
    }

    static boolean normalizeValues() {
        boolean changed = false;
        if (underbiddingThreshold < 0 || underbiddingThreshold > 100) {
            underbiddingThreshold = AuctionUnderbidding.DEFAULT_THRESHOLD;
            changed = true;
        }
        if (overbiddingThreshold < 100) {
            overbiddingThreshold = AuctionOverbidding.DEFAULT_THRESHOLD;
            changed = true;
        }
        if (!Double.isFinite(absoluteThreshold) || absoluteThreshold < 0.0D) {
            absoluteThreshold = DEFAULT_ABSOLUTE_THRESHOLD;
            changed = true;
        }
        if (playerHudPreset == null) {
            playerHudPreset = PlayerHudPreset.trade;
            changed = true;
        }
        if (marketPriceBasis == null) {
            marketPriceBasis = ReferencePriceBasis.LOWEST_BIN;
            changed = true;
        }
        if (auctionPriceHudScreens == null) { auctionPriceHudScreens = new ArrayList<>(DEFAULT_AUCTION_PRICE_HUD_SCREENS); changed = true; }
        if (playerHudScreens == null) { playerHudScreens = new ArrayList<>(DEFAULT_PLAYER_HUD_SCREENS); changed = true; }
        if (tradeGuardHudScreens == null) { tradeGuardHudScreens = new ArrayList<>(DEFAULT_TRADE_GUARD_HUD_SCREENS); changed = true; }
        if (minionProfitHudScreens == null) { minionProfitHudScreens = new ArrayList<>(DEFAULT_MINION_PROFIT_HUD_SCREENS); changed = true; }
        if (forgeProfitHudScreens == null) { forgeProfitHudScreens = new ArrayList<>(DEFAULT_FORGE_PROFIT_HUD_SCREENS); changed = true; }
        if (profitTrackerHudScreens == null) { profitTrackerHudScreens = new ArrayList<>(DEFAULT_PROFIT_TRACKER_HUD_SCREENS); changed = true; }
        if (auctionPriceHudRows == null) { auctionPriceHudRows = new ArrayList<>(DEFAULT_AUCTION_PRICE_HUD_ROWS); changed = true; }
        if (playerHudRows == null) { playerHudRows = new ArrayList<>(); changed = true; }
        if (tradeGuardHudRows == null) { tradeGuardHudRows = new ArrayList<>(); changed = true; }
        if (minionProfitHudRows == null) { minionProfitHudRows = new ArrayList<>(); changed = true; }
        if (forgeProfitHudRows == null) { forgeProfitHudRows = new ArrayList<>(); changed = true; }
        if (profitTrackerHudRows == null) { profitTrackerHudRows = new ArrayList<>(); changed = true; }
        changed |= normalizeScreens(auctionPriceHudScreens);
        changed |= normalizeScreens(playerHudScreens);
        changed |= normalizeScreens(tradeGuardHudScreens);
        changed |= normalizeScreens(minionProfitHudScreens);
        changed |= normalizeScreens(forgeProfitHudScreens);
        changed |= normalizeScreens(profitTrackerHudScreens);
        changed |= normalizeRows(auctionPriceHudRows, DEFAULT_AUCTION_PRICE_HUD_ROWS);
        changed |= normalizeRows(playerHudRows, DEFAULT_PLAYER_HUD_ROWS);
        changed |= normalizeRows(tradeGuardHudRows, DEFAULT_TRADE_GUARD_HUD_ROWS);
        changed |= normalizeRows(minionProfitHudRows, DEFAULT_MINION_PROFIT_HUD_ROWS);
        changed |= normalizeRows(forgeProfitHudRows, DEFAULT_FORGE_PROFIT_HUD_ROWS);
        changed |= normalizeRows(profitTrackerHudRows, DEFAULT_PROFIT_TRACKER_HUD_ROWS);
        return changed;
    }

    static boolean normalizeScreens(List<String> values) {
        List<String> normalized = values.stream()
                .filter(value -> value != null && SCREEN_KEYS.contains(value))
                .distinct()
                .toList();
        if (!values.equals(normalized)) {
            values.clear();
            values.addAll(normalized);
            return true;
        }
        return false;
    }

    private static boolean normalizeRows(List<String> values, List<String> defaults) {
        if (values == null) {
            return false;
        }
        List<String> normalized = new ArrayList<>();
        for (String value : values) {
            if (value == null) continue;
            boolean disabled = value.startsWith("!");
            String id = disabled ? value.substring(1) : value;
            if (defaults.contains(id) && !normalized.contains(id) && !normalized.contains("!" + id)) {
                normalized.add(disabled ? "!" + id : id);
            }
        }
        for (String id : defaults) {
            if (!normalized.contains(id) && !normalized.contains("!" + id)) {
                normalized.add(id);
            }
        }
        if (!values.equals(normalized)) {
            values.clear();
            values.addAll(normalized);
            return true;
        }
        return false;
    }

    public enum PlayerHudPreset {
        trade,
        compact,
        profile,
        all
    }
}
