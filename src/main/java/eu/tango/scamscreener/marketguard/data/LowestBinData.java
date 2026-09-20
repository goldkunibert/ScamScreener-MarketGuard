package eu.tango.scamscreener.marketguard.data;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import eu.tango.scamscreener.marketguard.ApiEndpoint;
import eu.tango.scamscreener.marketguard.MarketGuard;
import eu.tango.scamscreener.marketguard.compat.ScamScreenerBlacklistCompat;
import eu.tango.scamscreener.marketguard.util.MessageBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

public final class LowestBinData {
    private static final String URL = ApiEndpoint.url("/api/v2/lowestbin");
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(8);
    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(CONNECT_TIMEOUT)
            .build();
    private static final SnapshotCache CACHE = new SnapshotCache(60_000, 10_000);
    private static volatile boolean refreshFailureNoticeShown = false;
    private static volatile String lastBlacklistNoticeKey;

    private LowestBinData() {}

    public record LookupResult(
            Double value,
            int lowestBinCount,
            Double average7d,
            Double average30d,
            boolean stale,
            boolean loading,
            boolean refreshFailed
    ) {
        /** A result whose Lowest BIN comes from a single-item listing (or from an API without the {@code count} field). */
        public LookupResult(Double value, Double average7d, Double average30d, boolean stale, boolean loading, boolean refreshFailed) {
            this(value, 1, average7d, average30d, stale, loading, refreshFailed);
        }

        public boolean hasValue() {
            return value != null;
        }
    }

    public static LookupResult lookupLowestBin(String itemId) {
        return lookupPriceData(itemId, true);
    }

    public static LookupResult lookupPriceData(String itemId) {
        return lookupPriceData(itemId, false);
    }

    private static LookupResult lookupPriceData(String itemId, boolean notifyAuctioneer) {
        SnapshotCache.View cacheView = CACHE.view();
        JsonObject product = readProduct(cacheView.snapshot(), itemId);
        if (notifyAuctioneer) {
            notifyBlacklistedAuctioneerIfPresent(product, itemId);
        }
        Double value = product != null && product.has("price") ? product.get("price").getAsDouble() : null;
        // stack size of the listing behind the per-unit price; older API builds do not send it
        int lowestBinCount = product != null && product.has("count") ? Math.max(1, product.get("count").getAsInt()) : 1;
        Double average7d = readPositiveAverage(product, "avg7d");
        Double average30d = readPositiveAverage(product, "avg30d");

        MarketGuard.debug(
                "Lowest BIN lookup itemId='{}' hasSnapshot={} lowestBin={} lowestBinCount={} average7d={} average30d={} stale={} loading={} refreshFailed={}",
                itemId,
                cacheView.snapshot() != null,
                value,
                lowestBinCount,
                average7d,
                average30d,
                cacheView.stale(),
                cacheView.loading(),
                cacheView.refreshFailed()
        );
        return new LookupResult(value, lowestBinCount, average7d, average30d, cacheView.stale(), cacheView.loading(), cacheView.refreshFailed());
    }

    public static String findItemIdByName(String displayName) {
        return CACHE.findItemIdByName(displayName, LowestBinData::readItemName);
    }

    public static CompletableFuture<Void> refreshAsyncIfNeeded() {
        CACHE.refreshAsyncIfNeeded(
                "Lowest BIN",
                LowestBinData::fetchLowestBinSnapshotAsync,
                LowestBinData::resetRefreshFailureNotice,
                cause -> notifyRefreshFailureOnce()
        );
        CompletableFuture<JsonObject> refresh = CACHE.refreshInFlight();
        return refresh == null ? CompletableFuture.completedFuture(null) : refresh.handle((snapshot, throwable) -> null);
    }

    public static void checkBlacklistedAuctioneerAsyncIfNeeded(String itemId) {
        if (itemId == null || itemId.isBlank()) {
            MarketGuard.debug("Skipping blacklisted auctioneer check because itemId was blank");
            return;
        }

        JsonObject snapshot = CACHE.cachedSnapshot();
        if (snapshot != null) {
            notifyBlacklistedAuctioneerIfPresent(readProduct(snapshot, itemId), itemId);
            if (CACHE.hasFreshSnapshotNow()) {
                return;
            }
        }

        refreshAsyncIfNeeded();

        CompletableFuture<JsonObject> refreshFuture = CACHE.refreshInFlight();
        if (refreshFuture == null) {
            return;
        }

        refreshFuture.thenAccept(refreshedSnapshot -> notifyBlacklistedAuctioneerIfPresent(readProduct(refreshedSnapshot, itemId), itemId))
                .exceptionally(throwable -> null);
    }

    public static void resetBlacklistNoticeState() {
        lastBlacklistNoticeKey = null;
    }

    private static CompletableFuture<JsonObject> fetchLowestBinSnapshotAsync() {
        long startedAt = System.currentTimeMillis();
        MarketGuard.debug("Fetching Lowest BIN snapshot asynchronously from {}", URL);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(URL))
                .header("User-Agent", MarketGuard.userAgent())
                .timeout(REQUEST_TIMEOUT)
                .GET()
                .build();

        return CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    long durationMs = System.currentTimeMillis() - startedAt;
                    MarketGuard.debug(
                            "Lowest BIN async response status={} durationMs={} bodyLength={}",
                            response.statusCode(),
                            durationMs,
                            response.body().length()
                    );
                    return parseSnapshot(response);
                });
    }

    private static Double readPositiveAverage(JsonObject product, String field) {
        if (product == null || !product.has(field)) {
            return null;
        }

        double average = product.get(field).getAsDouble();
        return Double.isFinite(average) && average > 0.0 ? average : null;
    }

    private static JsonObject readProduct(JsonObject snapshot, String itemId) {
        if (snapshot == null || !snapshot.has(itemId) || !snapshot.get(itemId).isJsonObject()) {
            return null;
        }

        return snapshot.getAsJsonObject(itemId);
    }

    private static String readItemName(JsonObject product) {
        if (product == null || !product.has("item_name")) {
            return null;
        }

        return product.get("item_name").getAsString();
    }

    private static String readAuctioneerUuid(JsonObject product) {
        if (product == null || !product.has("auctioneerUuid")) {
            return null;
        }

        return product.get("auctioneerUuid").getAsString();
    }

    static JsonObject parseSnapshot(HttpResponse<String> response) {
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("Lowest BIN request failed with status " + response.statusCode());
        }

        JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();
        if (!root.has("products") || !root.get("products").isJsonObject()) {
            throw new IllegalStateException("Lowest BIN response did not contain a products object");
        }

        return root.getAsJsonObject("products");
    }

    private static void notifyBlacklistedAuctioneerIfPresent(JsonObject product, String itemId) {
        if (product == null) {
            return;
        }

        String auctioneerUuid = readAuctioneerUuid(product);
        MarketGuard.debug(
                "Checking Lowest BIN auctioneer against ScamScreener blacklist itemId='{}' auctioneerUuid='{}'",
                itemId,
                auctioneerUuid
        );
        String blacklistedPlayerName = ScamScreenerBlacklistCompat.findBlacklistedPlayerName(auctioneerUuid);
        if (blacklistedPlayerName != null) {
            MarketGuard.debug(
                    "Lowest BIN auctioneer matched ScamScreener blacklist itemId='{}' player='{}'",
                    itemId,
                    blacklistedPlayerName
            );
            notifyBlacklistedAuctioneer(itemId, blacklistedPlayerName);
        } else {
            MarketGuard.debug("Lowest BIN auctioneer was not present in ScamScreener blacklist itemId='{}'", itemId);
        }
    }

    private static void notifyRefreshFailureOnce() {
        Minecraft client = Minecraft.getInstance();
        if (client == null) {
            return;
        }

        if (refreshFailureNoticeShown) {
            return;
        }

        refreshFailureNoticeShown = true;
        client.execute(() -> {
            if (client.player == null) {
                return;
            }

            MessageBuilder.error(
                    Component.literal("Lowest BIN prices could not be refreshed. MarketGuard will not block AH actions because of missing API data.")
                            .withStyle(ChatFormatting.YELLOW),
                    client.player
            );
        });
    }

    private static void notifyBlacklistedAuctioneer(String itemId, String playerName) {
        Minecraft client = Minecraft.getInstance();
        if (client == null) {
            return;
        }

        client.execute(() -> {
            if (client.player == null) {
                return;
            }

            String noticeKey = itemId + "|" + playerName;
            if (noticeKey.equals(lastBlacklistNoticeKey)) {
                MarketGuard.debug(
                        "Skipping duplicate ScamScreener blacklist notice itemId='{}' player='{}'",
                        itemId,
                        playerName
                );
                return;
            }

            lastBlacklistNoticeKey = noticeKey;
            MessageBuilder.blacklistedPlayer(playerName, client.player);
        });
    }

    private static void resetRefreshFailureNotice() {
        refreshFailureNoticeShown = false;
    }

    static SnapshotCache cache() {
        return CACHE;
    }

    static void resetForTests() {
        CACHE.reset();
        refreshFailureNoticeShown = false;
        lastBlacklistNoticeKey = null;
    }
}
