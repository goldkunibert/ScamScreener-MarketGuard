package eu.tango.scamscreener.marketguard.data;

import com.google.gson.JsonObject;
import eu.tango.scamscreener.marketguard.compat.ScamScreenerBlacklistCompat;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mockStatic;

class LowestBinDataLookupTest {

    @AfterEach
    void resetState() {
        LowestBinData.resetForTests();
    }

    @Test
    void returnsFreshCachedValue() throws Exception {
        JsonObject snapshot = new JsonObject();
        snapshot.add("FANCY_LEGGINGS", product(123.0, 150.0, 175.0, "57ad19ca639f412daee5765f87874e35"));

        LowestBinData.cache().setSnapshotForTests(snapshot, System.currentTimeMillis() + 60_000L);
        LowestBinData.cache().setLastRefreshAttemptAtMsForTests(System.currentTimeMillis());

        LowestBinData.LookupResult result;
        try (MockedStatic<ScamScreenerBlacklistCompat> blacklist = mockStatic(ScamScreenerBlacklistCompat.class)) {
            blacklist.when(() -> ScamScreenerBlacklistCompat.findBlacklistedPlayerName("57ad19ca639f412daee5765f87874e35"))
                    .thenReturn(null);
            result = LowestBinData.lookupLowestBin("FANCY_LEGGINGS");
        }

        assertTrue(result.hasValue());
        assertEquals(123.0, result.value());
        assertEquals(1, result.lowestBinCount());
        assertEquals(150.0, result.average7d());
        assertEquals(175.0, result.average30d());
        assertFalse(result.stale());
        assertFalse(result.loading());
        assertFalse(result.refreshFailed());
    }

    @Test
    void readsTheStackSizeOfTheCheapestListingWhenTheApiSendsIt() throws Exception {
        JsonObject snapshot = new JsonObject();
        JsonObject product = product(4_687.5, 11_694.0, 9_956.0, "57ad19ca639f412daee5765f87874e35");
        product.addProperty("count", 64);
        snapshot.add("SHINY_ORB", product);

        LowestBinData.cache().setSnapshotForTests(snapshot, System.currentTimeMillis() + 60_000L);
        LowestBinData.cache().setLastRefreshAttemptAtMsForTests(System.currentTimeMillis());

        LowestBinData.LookupResult result = LowestBinData.lookupPriceData("SHINY_ORB");

        assertEquals(4_687.5, result.value());
        assertEquals(64, result.lowestBinCount());
    }

    @Test
    void returnsStaleValueAfterFailedRefresh() throws Exception {
        JsonObject snapshot = new JsonObject();
        snapshot.add("FANCY_LEGGINGS", product(123.0, null, "57ad19ca639f412daee5765f87874e35"));

        LowestBinData.cache().setSnapshotForTests(snapshot, 0L);
        LowestBinData.cache().setLastRefreshAttemptFailedForTests(true);
        LowestBinData.cache().setLastRefreshAttemptAtMsForTests(System.currentTimeMillis());

        LowestBinData.LookupResult result;
        try (MockedStatic<ScamScreenerBlacklistCompat> blacklist = mockStatic(ScamScreenerBlacklistCompat.class)) {
            blacklist.when(() -> ScamScreenerBlacklistCompat.findBlacklistedPlayerName("57ad19ca639f412daee5765f87874e35"))
                    .thenReturn(null);
            result = LowestBinData.lookupLowestBin("FANCY_LEGGINGS");
        }

        assertTrue(result.hasValue());
        assertEquals(123.0, result.value());
        assertTrue(result.stale());
        assertFalse(result.loading());
        assertTrue(result.refreshFailed());
    }

    @Test
    void returnsStaleCachedValueWhileRefreshIsStillPending() throws Exception {
        JsonObject snapshot = new JsonObject();
        snapshot.add("FANCY_LEGGINGS", product(123.0, null, "57ad19ca639f412daee5765f87874e35"));

        LowestBinData.cache().setSnapshotForTests(snapshot, 0L);
        LowestBinData.cache().setRefreshInFlightForTests(new CompletableFuture<JsonObject>());
        LowestBinData.cache().setLastRefreshAttemptFailedForTests(false);
        LowestBinData.cache().setLastRefreshAttemptAtMsForTests(System.currentTimeMillis());

        LowestBinData.LookupResult result;
        try (MockedStatic<ScamScreenerBlacklistCompat> blacklist = mockStatic(ScamScreenerBlacklistCompat.class)) {
            blacklist.when(() -> ScamScreenerBlacklistCompat.findBlacklistedPlayerName("57ad19ca639f412daee5765f87874e35"))
                    .thenReturn(null);
            result = LowestBinData.lookupLowestBin("FANCY_LEGGINGS");
        }

        assertTrue(result.hasValue());
        assertTrue(result.stale());
        assertTrue(result.loading());
        assertFalse(result.refreshFailed());
    }

    @Test
    void reportsFailedRefreshWithoutCache() throws Exception {
        LowestBinData.cache().setLastRefreshAttemptFailedForTests(true);
        LowestBinData.cache().setLastRefreshAttemptAtMsForTests(System.currentTimeMillis());

        LowestBinData.LookupResult result = LowestBinData.lookupLowestBin("FANCY_LEGGINGS");

        assertFalse(result.hasValue());
        assertFalse(result.stale());
        assertFalse(result.loading());
        assertTrue(result.refreshFailed());
    }

    @Test
    void stillReturnsLowestBinWhenAuctioneerIsBlacklisted() throws Exception {
        JsonObject snapshot = new JsonObject();
        snapshot.add("FANCY_LEGGINGS", product(123.0, null, "57ad19ca639f412daee5765f87874e35"));

        LowestBinData.cache().setSnapshotForTests(snapshot, System.currentTimeMillis() + 60_000L);
        LowestBinData.cache().setLastRefreshAttemptAtMsForTests(System.currentTimeMillis());

        LowestBinData.LookupResult result;
        try (MockedStatic<ScamScreenerBlacklistCompat> blacklist = mockStatic(ScamScreenerBlacklistCompat.class)) {
            blacklist.when(() -> ScamScreenerBlacklistCompat.findBlacklistedPlayerName("57ad19ca639f412daee5765f87874e35"))
                    .thenReturn("Scammer");
            result = LowestBinData.lookupLowestBin("FANCY_LEGGINGS");
        }

        assertTrue(result.hasValue());
        assertEquals(123.0, result.value());
        assertFalse(result.stale());
        assertFalse(result.loading());
        assertFalse(result.refreshFailed());
    }

    @Test
    void priceDataLookupDoesNotTreatVisibleGearAsTheViewedAuctioneer() {
        JsonObject snapshot = new JsonObject();
        snapshot.add("FANCY_LEGGINGS", product(123.0, 125.0, "57ad19ca639f412daee5765f87874e35"));
        LowestBinData.cache().setSnapshotForTests(snapshot, System.currentTimeMillis() + 60_000L);

        try (MockedStatic<ScamScreenerBlacklistCompat> blacklist = mockStatic(ScamScreenerBlacklistCompat.class)) {
            LowestBinData.LookupResult result = LowestBinData.lookupPriceData("FANCY_LEGGINGS");

            assertEquals(123.0, result.value());
            blacklist.verifyNoInteractions();
        }
    }

    @Test
    void findItemIdByNameUsesNormalizedDisplayName() {
        JsonObject snapshot = new JsonObject();
        JsonObject product = product(123.0, null, "57ad19ca639f412daee5765f87874e35");
        product.addProperty("item_name", "Fancy Leggings");
        snapshot.add("FANCY_LEGGINGS", product);

        LowestBinData.cache().setSnapshotForTests(snapshot, System.currentTimeMillis() + 60_000L);

        assertEquals("FANCY_LEGGINGS", LowestBinData.findItemIdByName(" fancy   leggings "));
    }

    @Test
    void findItemIdByNameFollowsReplacedSnapshot() {
        JsonObject snapshot = new JsonObject();
        JsonObject leggings = product(123.0, null, "57ad19ca639f412daee5765f87874e35");
        leggings.addProperty("item_name", "Fancy Leggings");
        snapshot.add("FANCY_LEGGINGS", leggings);
        LowestBinData.cache().setSnapshotForTests(snapshot, System.currentTimeMillis() + 60_000L);
        assertEquals("FANCY_LEGGINGS", LowestBinData.findItemIdByName("Fancy Leggings"));

        JsonObject replaced = new JsonObject();
        JsonObject boots = product(45.0, null, "57ad19ca639f412daee5765f87874e35");
        boots.addProperty("item_name", "Fancy Boots");
        replaced.add("FANCY_BOOTS", boots);
        LowestBinData.cache().setSnapshotForTests(replaced, System.currentTimeMillis() + 60_000L);

        assertEquals("FANCY_BOOTS", LowestBinData.findItemIdByName("fancy boots"));
        assertNull(LowestBinData.findItemIdByName("Fancy Leggings"));
    }

    private static JsonObject product(double price, Double average7d, String auctioneerUuid) {
        return product(price, average7d, null, auctioneerUuid);
    }

    private static JsonObject product(double price, Double average7d, Double average30d, String auctioneerUuid) {
        JsonObject product = new JsonObject();
        product.addProperty("price", price);
        if (average7d != null) {
            product.addProperty("avg7d", average7d);
        }
        if (average30d != null) {
            product.addProperty("avg30d", average30d);
        }
        product.addProperty("auctioneerUuid", auctioneerUuid);
        return product;
    }
}
