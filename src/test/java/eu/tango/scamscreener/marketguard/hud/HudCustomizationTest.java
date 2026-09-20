package eu.tango.scamscreener.marketguard.hud;

import eu.tango.scamscreener.marketguard.MarketGuardConfig;
import eu.tango.scamscreener.marketguard.screen.HudScreenGroup;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HudCustomizationTest {
    @AfterEach
    void reset() {
        MarketGuardConfig.setPlayerHudPreset("trade");
        MarketGuardConfig.auctionPriceHudScreens = new ArrayList<>(List.of("bin_view"));
        MarketGuardConfig.auctionPriceHudRows = new ArrayList<>(MarketGuardConfig.DEFAULT_AUCTION_PRICE_HUD_ROWS);
        MarketGuardConfig.playerHudScreens = new ArrayList<>(List.of("trade", "profile", "bin_view"));
        MarketGuardConfig.playerHudRows = new ArrayList<>(List.of(
                "name", "seen", "scamscreener", "status", "wealth", "profile_value",
                "first_join", "profile", "value_coverage", "value_missing", "value_status",
                "museum", "museum_items", "armor", "equipment", "pet", "skills", "uuid", "data"
        ));
        MarketGuardConfig.tradeGuardHudScreens = new ArrayList<>(List.of("trade"));
        MarketGuardConfig.tradeGuardHudRows = new ArrayList<>(List.of("own_value", "partner_value", "difference", "unpriced", "data", "warning"));
    }

    @Test
    void defaultMappingsOnlyShowHudOnTheirIntendedScreens() {
        assertTrue(HudCustomization.visible(HudCustomization.HudId.AUCTION_PRICE, "Bin Auction View"));
        assertFalse(HudCustomization.visible(HudCustomization.HudId.AUCTION_PRICE, "Auction House"));
        assertTrue(HudCustomization.visible(HudCustomization.HudId.PLAYER, "Pankraz01's Profile"));
        assertTrue(HudCustomization.visible(HudCustomization.HudId.PLAYER, "You Pankraz01"));
        assertTrue(HudCustomization.visible(HudCustomization.HudId.PLAYER, "Bin Auction View"));
        assertTrue(HudCustomization.visible(HudCustomization.HudId.TRADE_GUARD, "You Pankraz01"));
        assertFalse(HudCustomization.visible(HudCustomization.HudId.TRADE_GUARD, "Pankraz01's Profile"));
    }

    @Test
    void resetRestoresTheShippedAuctionPriceLayout() {
        HudCustomization.placeRow(HudCustomization.HudId.AUCTION_PRICE, "stale", false, 0);
        HudCustomization.placeRow(HudCustomization.HudId.AUCTION_PRICE, "advice", true, 0);

        HudCustomization.reset(HudCustomization.HudId.AUCTION_PRICE);

        assertEquals(List.of("item", "auction", "lowest_bin", "advice", "volatility", "liquidity", "stale"),
                MarketGuardConfig.auctionPriceHudRows);
        assertEquals(List.of("item", "auction", "lowest_bin", "advice", "volatility", "liquidity", "stale"),
                HudCustomization.rows(HudCustomization.HudId.AUCTION_PRICE));
    }

    @Test
    void rowsCanBeDroppedBetweenVisibleAndHiddenColumns() {
        HudCustomization.placeRow(HudCustomization.HudId.AUCTION_PRICE, "stale", false, 0);
        HudCustomization.placeRow(HudCustomization.HudId.AUCTION_PRICE, "item", false, 0);

        assertEquals(List.of("auction", "lowest_bin", "advice", "volatility", "liquidity", "!item", "!stale"),
                MarketGuardConfig.auctionPriceHudRows);
        assertEquals(List.of("auction", "lowest_bin", "advice", "volatility", "liquidity"),
                HudCustomization.editableRows(HudCustomization.HudId.AUCTION_PRICE, true));
        assertEquals(List.of("item", "stale"),
                HudCustomization.editableRows(HudCustomization.HudId.AUCTION_PRICE, false));

        HudCustomization.placeRow(HudCustomization.HudId.AUCTION_PRICE, "stale", true, 1);

        assertEquals(List.of("auction", "stale", "lowest_bin", "advice", "volatility", "liquidity", "!item"),
                MarketGuardConfig.auctionPriceHudRows);
    }

    @Test
    void droppingPlayerRowsKeepsTheRowsOfOtherPresets() {
        MarketGuardConfig.setPlayerHudPreset("profile");
        HudCustomization.placeRow(HudCustomization.HudId.PLAYER, "uuid", false, 0);
        HudCustomization.placeRow(HudCustomization.HudId.PLAYER, "museum_items", false, 0);

        MarketGuardConfig.setPlayerHudPreset("trade");
        HudCustomization.placeRow(HudCustomization.HudId.PLAYER, "seen", true, 2);

        assertEquals(List.of("name", "scamscreener", "seen", "status"), MarketGuardConfig.playerHudRows.subList(0, 4));
        assertEquals(19, MarketGuardConfig.playerHudRows.size());
        assertTrue(MarketGuardConfig.playerHudRows.contains("!uuid"));
        assertTrue(MarketGuardConfig.playerHudRows.contains("!museum_items"));

        MarketGuardConfig.setPlayerHudPreset("profile");
        assertFalse(HudCustomization.rows(HudCustomization.HudId.PLAYER).contains("uuid"));
        assertTrue(HudCustomization.rows(HudCustomization.HudId.PLAYER).contains("armor"));
    }

    @Test
    void editorRowsUseHudExamplesInsteadOfConfigurationLabels() {
        assertEquals("Auction: 15,000,000 coins",
                HudCustomization.example(HudCustomization.HudId.AUCTION_PRICE, "auction").getString());
        assertEquals("Lowest BIN: 15,400,000 coins",
                HudCustomization.example(HudCustomization.HudId.AUCTION_PRICE, "lowest_bin").getString());
        assertEquals("Fair price (-400,000 coins)",
                HudCustomization.example(HudCustomization.HudId.AUCTION_PRICE, "advice").getString());
        assertEquals("Bank + purse: 45,200,000",
                HudCustomization.example(HudCustomization.HudId.PLAYER, "wealth").getString());
        assertEquals("Total: +12,500,000",
                HudCustomization.example(HudCustomization.HudId.PROFIT_TRACKER, "total").getString());
        assertEquals("Storage sells for: 2,800,000+",
                HudCustomization.example(HudCustomization.HudId.MINION_PROFIT, "profit").getString());
    }

    @Test
    void minionProfitRowsNoLongerContainTheForecast() {
        assertEquals(List.of("held_coins", "profit", "missing", "loading", "unavailable", "stale"),
                HudCustomization.rows(HudCustomization.HudId.MINION_PROFIT));
    }

    @Test
    void playerPresetChangesTheAvailableRowsWithoutDiscardingConfiguration() {
        MarketGuardConfig.setPlayerHudPreset("compact");
        assertEquals(List.of("name", "seen", "scamscreener", "status", "wealth", "profile_value"),
                HudCustomization.rows(HudCustomization.HudId.PLAYER));

        MarketGuardConfig.setPlayerHudPreset("trade");
        assertEquals(List.of("name", "seen", "scamscreener", "status"),
                HudCustomization.rows(HudCustomization.HudId.PLAYER));

        MarketGuardConfig.setPlayerHudPreset("all");
        assertTrue(HudCustomization.rows(HudCustomization.HudId.PLAYER).contains("armor"));
        assertFalse(HudCustomization.rows(HudCustomization.HudId.PLAYER).contains("finance_history"));
    }

    @Test
    void removedPlayerRowsInExistingConfigurationsAreIgnored() {
        MarketGuardConfig.setPlayerHudPreset("all");
        MarketGuardConfig.playerHudRows.addAll(0, List.of("finance_status", "finance_history", "!unavailable"));

        List<String> rows = HudCustomization.rows(HudCustomization.HudId.PLAYER);

        assertEquals("name", rows.getFirst());
        assertTrue(rows.stream().noneMatch(row -> row.startsWith("finance_") || row.equals("unavailable")));
    }

    @Test
    void editorTableStaysAboveFooterAtTheTestedGuiScale() {
        int rowsTop = HudEditorScreen.calculateRowsTop(428);
        int listHeight = HudEditorScreen.calculateListHeight(240, rowsTop);

        assertEquals(4, HudEditorScreen.calculateScreenColumns(428));
        assertEquals(128, rowsTop);
        assertEquals(72, listHeight);
        assertEquals(12, (240 - 28) - (rowsTop + listHeight));
    }

    @Test
    void screenTogglesChangeTheConfiguredMapping() {
        HudCustomization.toggleScreen(HudCustomization.HudId.AUCTION_PRICE, HudScreenGroup.AUCTION_HOUSE);

        assertTrue(HudCustomization.visible(HudCustomization.HudId.AUCTION_PRICE, "Auction House"));
    }

    @Test
    void screenMappingsWorkOnLocalesWithADottedCapitalI() {
        Locale previous = Locale.getDefault();
        Locale.setDefault(Locale.of("tr"));
        try {
            assertTrue(HudCustomization.visible(HudCustomization.HudId.PLAYER, "Bin Auction View"));

            HudCustomization.toggleScreen(HudCustomization.HudId.PLAYER, HudScreenGroup.INGAME);

            assertTrue(MarketGuardConfig.playerHudScreens.contains("ingame"));
            assertTrue(HudCustomization.visible(HudCustomization.HudId.PLAYER, null));
        } finally {
            Locale.setDefault(previous);
        }
    }
}
