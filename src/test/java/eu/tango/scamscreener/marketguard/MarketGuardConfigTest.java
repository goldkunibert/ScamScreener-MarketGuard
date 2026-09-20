package eu.tango.scamscreener.marketguard;

import eu.midnightdust.lib.config.MidnightConfig;
import eu.tango.scamscreener.marketguard.auction.AuctionOverbidding;
import eu.tango.scamscreener.marketguard.auction.AuctionUnderbidding;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarketGuardConfigTest {

    @AfterEach
    void resetDefaults() {
        MarketGuardConfig.setUnderbiddingThreshold(AuctionUnderbidding.DEFAULT_THRESHOLD);
        MarketGuardConfig.setOverbiddingThreshold(AuctionOverbidding.DEFAULT_THRESHOLD);
        MarketGuardConfig.setAbsoluteThreshold(MarketGuardConfig.DEFAULT_ABSOLUTE_THRESHOLD);
        MarketGuardConfig.setDebugEnabled(false);
        MarketGuardConfig.setUpdateNotificationsEnabled(true);
        MarketGuardConfig.setWarnOnUnmatchedProfitConfirmations(false);
        MarketGuardConfig.setProfitTrackerHudEnabled(false);
        MarketGuardConfig.setPlayerHudPreset("trade");
        MarketGuardConfig.setPlayerHudShowUnavailableRows(false);
        MarketGuardConfig.setShortNumberFormat(false);
    }

    @Test
    void exposesEveryMarketGuardSettingToMidnightLib() throws Exception {
        for (String name : List.of(
                "underbiddingThreshold",
                "overbiddingThreshold",
                "absoluteThreshold",
                "debug",
                "updateNotificationsEnabled",
                "warnOnUnmatchedProfitConfirmations",
                "profitTrackerHudEnabled",
                "playerHudPreset",
                "playerHudShowUnavailableRows",
                "shortNumberFormat"
        )) {
            Field field = MarketGuardConfig.class.getField(name);
            assertTrue(field.isAnnotationPresent(MidnightConfig.Entry.class), name + " must be configurable");
        }
    }

    @Test
    void protectionChecksUseTheMidnightLibBackedSettings() {
        MarketGuardConfig.setUnderbiddingThreshold(70);
        MarketGuardConfig.setOverbiddingThreshold(140);
        MarketGuardConfig.setAbsoluteThreshold(25_000L);

        assertEquals(70, AuctionUnderbidding.getThreshold());
        assertEquals(140, AuctionOverbidding.getThreshold());
        assertEquals(25_000L, MarketGuardConfig.getAbsoluteThreshold());
    }

    @Test
    void playerHudPresetSupportsAllAndRejectsUnknownViews() {
        MarketGuardConfig.setPlayerHudPreset("all");
        assertEquals("all", MarketGuardConfig.getPlayerHudPreset());
        assertThrows(IllegalArgumentException.class, () -> MarketGuardConfig.setPlayerHudPreset("unknown"));
    }

    @Test
    void customHudButtonsUseNonNullMidnightMetadata() {
        var entryInfo = MarketGuardConfig.hudEntryInfo();

        assertNotNull(entryInfo);
        assertNull(entryInfo.field);
    }

    @Test
    void customHudListsArePersistedButHiddenFromTheAutomaticMidnightUi() throws Exception {
        for (String name : List.of(
                "auctionPriceHudScreens", "playerHudScreens", "minionProfitHudScreens",
                "forgeProfitHudScreens", "profitTrackerHudScreens", "auctionPriceHudRows",
                "playerHudRows", "minionProfitHudRows", "forgeProfitHudRows", "profitTrackerHudRows"
        )) {
            Field field = MarketGuardConfig.class.getField(name);
            assertTrue(field.isAnnotationPresent(MidnightConfig.Entry.class), name + " must be persisted");
            assertTrue(field.isAnnotationPresent(MidnightConfig.Hidden.class), name + " must use the custom HUD editor only");
        }
    }

    @Test
    void emptyScreenListsStayEmptyAndUnknownScreensAreDropped() {
        List<String> screens = new ArrayList<>();
        assertFalse(MarketGuardConfig.normalizeScreens(screens));
        assertTrue(screens.isEmpty());

        screens = new ArrayList<>(Arrays.asList("bin_view", "unknown", null, "bin_view"));
        assertTrue(MarketGuardConfig.normalizeScreens(screens));
        assertEquals(List.of("bin_view"), screens);
    }

    @Test
    void auctionPriceHudShipsWithoutASeparateDifferenceRow() {
        assertEquals(List.of("item", "auction", "lowest_bin", "advice", "volatility", "liquidity", "stale"),
                MarketGuardConfig.auctionPriceHudRows);
    }

    @Test
    void removedAuctionPriceHudRowsAreDroppedFromSavedLayouts() {
        MarketGuardConfig.auctionPriceHudRows = new ArrayList<>(List.of("auction", "item", "lowest_bin", "difference", "advice", "volatility", "liquidity", "stale"));

        assertTrue(MarketGuardConfig.normalizeValues());
        assertEquals(List.of("auction", "item", "lowest_bin", "advice", "volatility", "liquidity", "stale"),
                MarketGuardConfig.auctionPriceHudRows);

        MarketGuardConfig.auctionPriceHudRows = new ArrayList<>(List.of("title", "item", "auction", "lowest_bin", "!difference", "advice", "stale"));
        assertTrue(MarketGuardConfig.normalizeValues());
        assertEquals(List.of("item", "auction", "lowest_bin", "advice", "stale", "volatility", "liquidity"),
                MarketGuardConfig.auctionPriceHudRows);
        MarketGuardConfig.auctionPriceHudRows = new ArrayList<>(MarketGuardConfig.DEFAULT_AUCTION_PRICE_HUD_ROWS);
    }

    @Test
    void playerHudPresetIsManagedByTheCustomPlayerHudEditor() throws Exception {
        Field field = MarketGuardConfig.class.getField("playerHudPreset");

        assertTrue(field.isAnnotationPresent(MidnightConfig.Hidden.class));
    }

    @Test
    void noSettingLinksToAnUrl() {
        // The bundled MidnightLib 1.9.3+26.2 opens @Comment urls through ConfirmLinkScreen.confirmLinkNow(Screen, String,
        // boolean), which Minecraft 26.3 removed, so such an entry would throw NoSuchMethodError when clicked on 26.3.
        for (Field field : MarketGuardConfig.class.getFields()) {
            MidnightConfig.Comment comment = field.getAnnotation(MidnightConfig.Comment.class);
            assertTrue(comment == null || comment.url().isBlank(), field.getName() + " must not link to an URL");
        }
    }
}
