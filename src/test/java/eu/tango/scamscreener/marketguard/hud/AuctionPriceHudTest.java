package eu.tango.scamscreener.marketguard.hud;

import eu.tango.scamscreener.marketguard.MarketGuardConfig;
import eu.tango.scamscreener.marketguard.auction.AuctionOverbidding;
import eu.tango.scamscreener.marketguard.auction.AuctionUnderbidding;
import eu.tango.scamscreener.marketguard.auction.ReferencePriceBasis;
import eu.tango.scamscreener.marketguard.data.BazaarData;
import eu.tango.scamscreener.marketguard.data.LowestBinData;
import eu.tango.tangosHudLib.api.HudContent;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mockStatic;

class AuctionPriceHudTest {

    @AfterEach
    void resetDefaults() {
        MarketGuardConfig.setUnderbiddingThreshold(AuctionUnderbidding.DEFAULT_THRESHOLD);
        MarketGuardConfig.setOverbiddingThreshold(AuctionOverbidding.DEFAULT_THRESHOLD);
        MarketGuardConfig.setMarketPriceBasis(ReferencePriceBasis.LOWEST_BIN);
        MarketGuardConfig.auctionPriceHudRows = new ArrayList<>(MarketGuardConfig.DEFAULT_AUCTION_PRICE_HUD_ROWS);
    }

    @Test
    void showsMarketPriceAndCallsAPriceNearMarketFair() {
        HudContent content = AuctionPriceHud.content(view(1_020_000.0, 1_000_000.0, 1_000_000.0, 1_000_000.0));

        assertEquals(List.of("Fancy Leggings", "Auction: 1,020,000 coins", "Lowest BIN: 1,000,000 coins", "Fair price (+20,000 coins)"), lines(content));
        assertEquals(color(ChatFormatting.GRAY), color(content, "Auction: "));
        assertEquals(color(ChatFormatting.GOLD), color(content, "1,020,000 coins"));
        assertEquals(color(ChatFormatting.GRAY), color(content, "Lowest BIN: "));
        assertEquals(color(ChatFormatting.WHITE), color(content, "1,000,000 coins"));
        assertEquals(color(ChatFormatting.GREEN), color(content, "Fair price"));
        assertEquals(color(ChatFormatting.GRAY), color(content, " (+20,000 coins)"));
    }

    @Test
    void appendsTheCoinDifferenceToTheVerdictUnlessTheAuctionMatchesTheMarket() {
        assertTrue(lines(AuctionPriceHud.content(view(1_250_000.0, 1_000_000.0, 1_000_000.0, 1_000_000.0))).contains("25% above Lowest BIN - overpriced (+250,000 coins)"));
        assertTrue(lines(AuctionPriceHud.content(view(750_000.0, 1_000_000.0, 1_000_000.0, 1_000_000.0))).contains("Cheapest BIN right now (-250,000 coins)"));
        assertTrue(lines(AuctionPriceHud.content(view(1_000_000.0, 1_000_000.0, 1_000_000.0, 1_000_000.0))).contains("Cheapest BIN right now"));
    }

    @Test
    void callsAnAuctionBelowTheUnderbiddingThresholdAGoodDeal() {
        MarketGuardConfig.setMarketPriceBasis(ReferencePriceBasis.MEDIAN);
        HudContent content = AuctionPriceHud.content(view(750_000.0, 700_000.0, 1_000_000.0, 1_000_000.0));

        assertEquals(color(ChatFormatting.GREEN), color(content, "Good deal: 25% below median"));
    }

    @Test
    void warnsWhenAuctionIsSlightlyAboveMarket() {
        HudContent content = AuctionPriceHud.content(view(1_120_000.0, 1_000_000.0, 1_000_000.0, 1_000_000.0));

        assertEquals(color(ChatFormatting.YELLOW), color(content, "12% above Lowest BIN"));
    }

    @Test
    void flagsAnAuctionAtTheOverbiddingThresholdAsOverpriced() {
        HudContent content = AuctionPriceHud.content(view(1_250_000.0, 1_000_000.0, 1_000_000.0, 1_000_000.0));

        assertEquals(color(ChatFormatting.RED), color(content, "25% above Lowest BIN - overpriced"));
    }

    @Test
    void verdictFollowsTheConfiguredProtectionThresholds() {
        MarketGuardConfig.setOverbiddingThreshold(150);
        MarketGuardConfig.setUnderbiddingThreshold(60);

        assertTrue(verdicts(AuctionPriceHud.content(view(1_250_000.0, 1_000_000.0, 1_000_000.0, 1_000_000.0))).contains("25% above Lowest BIN"));
        assertTrue(verdicts(AuctionPriceHud.content(view(1_500_000.0, 1_000_000.0, 1_000_000.0, 1_000_000.0))).contains("50% above Lowest BIN - overpriced"));
        assertTrue(verdicts(AuctionPriceHud.content(view(750_000.0, 740_000.0, 1_000_000.0, 1_000_000.0))).contains("Fair price"));
        assertTrue(verdicts(AuctionPriceHud.content(view(600_000.0, 600_000.0, 1_000_000.0, 1_000_000.0))).contains("Cheapest BIN right now"));
    }

    @Test
    void verdictFallsBackToTheDefaultBandsWhenAProtectionIsDisabled() {
        MarketGuardConfig.setOverbiddingThreshold(100);
        MarketGuardConfig.setUnderbiddingThreshold(100);

        assertEquals(color(ChatFormatting.YELLOW),
                color(AuctionPriceHud.content(view(1_120_000.0, 1_000_000.0, 1_000_000.0, 1_000_000.0)), "12% above Lowest BIN"));
        assertTrue(verdicts(AuctionPriceHud.content(view(1_000_000.0, 990_000.0, 1_000_000.0, 1_000_000.0))).contains("Fair price"));
        assertTrue(verdicts(AuctionPriceHud.content(view(1_250_000.0, 1_000_000.0, 1_000_000.0, 1_000_000.0))).contains("25% above Lowest BIN - overpriced"));

        MarketGuardConfig.setUnderbiddingThreshold(0);
        MarketGuardConfig.setMarketPriceBasis(ReferencePriceBasis.MEDIAN);

        assertTrue(verdicts(AuctionPriceHud.content(view(400_000.0, 400_000.0, 1_000_000.0, 1_000_000.0))).contains("Cheapest BIN right now, 60% below median"));
        assertTrue(verdicts(AuctionPriceHud.content(view(900_000.0, 890_000.0, 1_000_000.0, 1_000_000.0))).contains("Fair price"));
    }

    @Test
    void softensTheVerdictWhenTheMedianIsBasedOnFewSales() {
        MarketGuardConfig.setMarketPriceBasis(ReferencePriceBasis.MEDIAN);
        HudContent above = AuctionPriceHud.content(view(1_250_000.0, 1_000_000.0, null, null));
        HudContent below = AuctionPriceHud.content(view(750_000.0, 700_000.0, 1_200_000.0, null));
        HudContent near = AuctionPriceHud.content(view(1_010_000.0, 1_000_000.0, null, null));

        assertTrue(lines(above).contains("Median: ~1,000,000 coins (few sales)"));
        assertEquals(color(ChatFormatting.WHITE), color(above, "~1,000,000 coins"));
        assertEquals(color(ChatFormatting.YELLOW), color(above, " (few sales)"));
        assertEquals(color(ChatFormatting.YELLOW), color(above, "Roughly 25% above median"));
        assertTrue(lines(below).contains("Median: ~950,000 coins (BIN 700,000 coins)"));
        assertEquals(color(ChatFormatting.YELLOW), color(below, "Roughly 21% below median"));
        assertTrue(verdicts(near).contains("Fair price"));
    }

    @Test
    void neverSoftensAVerdictAgainstTheLowestBin() {
        HudContent above = AuctionPriceHud.content(view(1_250_000.0, 1_000_000.0, null, null));
        HudContent volatile_ = AuctionPriceHud.content(view(1_250_000.0, 1_000_000.0, 2_000_000.0, 2_500_000.0));

        assertTrue(lines(above).contains("Lowest BIN: 1,000,000 coins (few sales)"));
        assertEquals(color(ChatFormatting.RED), color(above, "25% above Lowest BIN - overpriced"));
        assertTrue(lines(volatile_).contains("Lowest BIN: 1,000,000 coins (avg 2,000,000 coins)"));
        assertEquals(color(ChatFormatting.RED), color(volatile_, "25% above Lowest BIN - overpriced"));
    }

    @Test
    void callsTheCheapestListingGreenEvenWhenTheMarketPriceIsUncertain() {
        MarketGuardConfig.setMarketPriceBasis(ReferencePriceBasis.MEDIAN);
        HudContent isLowestBin = AuctionPriceHud.content(view(149_000.0, 149_000.0, 230_000.0, null));
        HudContent belowLowestBin = AuctionPriceHud.content(view(140_000.0, 149_000.0, 230_000.0, null));
        HudContent atMarket = AuctionPriceHud.content(view(1_000_000.0, 1_000_000.0, 1_000_000.0, 1_020_000.0));
        HudContent notCheapest = AuctionPriceHud.content(view(150_000.0, 149_000.0, 230_000.0, null));

        assertEquals(color(ChatFormatting.GREEN), color(isLowestBin, "Cheapest BIN right now, 21% below median"));
        assertEquals(color(ChatFormatting.GREEN), color(belowLowestBin, "Cheapest BIN right now, 26% below median"));
        assertEquals(color(ChatFormatting.GREEN), color(atMarket, "Cheapest BIN right now"));
        assertEquals(color(ChatFormatting.YELLOW), color(notCheapest, "Roughly 21% below median"));
    }

    @Test
    void cheapestListingStaysRedWhenTheWholeMarketIsOverpriced() {
        MarketGuardConfig.setMarketPriceBasis(ReferencePriceBasis.MEDIAN);
        HudContent content = AuctionPriceHud.content(view(1_250_000.0, 1_250_000.0, 1_000_000.0, 1_000_000.0));

        assertEquals(color(ChatFormatting.RED), color(content, "Cheapest BIN, but 25% above median - overpriced"));
    }

    @Test
    void comparesAStackAgainstTheMarketPriceOfTheWholeStack() {
        HudContent content = AuctionPriceHud.content(new AuctionPriceHud.View(
                "SHINY_ORB",
                "47x Shiny Orb",
                null,
                400_000.0,
                47,
                new LowestBinData.LookupResult(1_851.85, 11_694.0, 9_956.0, false, false, false)
        ));

        assertEquals(List.of("47x Shiny Orb", "Auction: 400,000 coins (8,511 each)", "Lowest BIN: 87,037 coins (1,852 each) (avg 549,618 coins)", "360% above Lowest BIN - overpriced (+312,963 coins)"), lines(content));
        assertEquals(color(ChatFormatting.GRAY), color(content, " (8,511 each)"));
    }

    @Test
    void namesTheStackListingBehindThePerUnitLowestBin() {
        HudContent content = AuctionPriceHud.content(new AuctionPriceHud.View(
                "SHINY_ORB",
                "32x Shiny Orb",
                null,
                1_000_000.0,
                32,
                new LowestBinData.LookupResult(4_687.5, 64, 11_694.0, 9_956.0, false, false, false)
        ));

        assertEquals(List.of(
                "32x Shiny Orb",
                "Auction: 1,000,000 coins (31,250 each)",
                "Lowest BIN: 64x for 300,000 coins (4,688 each) (avg 374,208 coins)",
                "567% above Lowest BIN - overpriced (+850,000 coins)"
        ), lines(content));
        assertEquals(color(ChatFormatting.WHITE), color(content, "64x for 300,000 coins"));
        assertEquals(color(ChatFormatting.GRAY), color(content, " (4,688 each)"));
    }

    @Test
    void marketPriceFollowsTheConfiguredBasisLikeTheWarnings() {
        AuctionPriceHud.View stack = new AuctionPriceHud.View(
                "SHINY_ORB",
                "47x Shiny Orb",
                null,
                400_000.0,
                47,
                new LowestBinData.LookupResult(1_851.85, 11_694.0, 9_956.0, false, false, false)
        );

        MarketGuardConfig.setMarketPriceBasis(ReferencePriceBasis.MEDIAN);
        assertTrue(lines(AuctionPriceHud.content(stack)).contains("Median: ~467,932 coins (9,956 each) (BIN 87,037 coins)"));
        assertTrue(lines(AuctionPriceHud.content(stack)).contains("Fair price (-67,932 coins)"));

        MarketGuardConfig.setMarketPriceBasis(ReferencePriceBasis.FAVOURABLE);
        assertTrue(lines(AuctionPriceHud.content(stack)).contains("Market: ~549,618 coins (11,694 each) (BIN 87,037 coins)"));
        assertTrue(lines(AuctionPriceHud.content(stack)).contains("Roughly 27% below market (-149,618 coins)"));
    }

    @Test
    void showsTheItemNameInItsRarityColourOrWhite() {
        HudContent legendary = AuctionPriceHud.content(new AuctionPriceHud.View(
                "FANCY_LEGGINGS",
                "Fancy Leggings",
                TextColor.fromLegacyFormat(ChatFormatting.GOLD),
                1_000_000.0,
                1,
                null
        ));
        HudContent plain = AuctionPriceHud.content(view(1_000_000.0, 1_000_000.0, 1_000_000.0, 1_000_000.0));

        assertEquals(color(ChatFormatting.GOLD), color(legendary, "Fancy Leggings"));
        assertEquals(color(ChatFormatting.WHITE), color(plain, "Fancy Leggings"));
    }

    @Test
    void showsLoadingStateUntilLowestBinIsAvailable() {
        HudContent content = AuctionPriceHud.content(new AuctionPriceHud.View("FANCY_LEGGINGS", "Fancy Leggings", null, 1_000_000.0, 1, null));

        assertTrue(lines(content).contains("Loading market price..."));
    }

    @Test
    void explainsWhenNoMarketPriceExistsForTheItem() {
        HudContent missing = AuctionPriceHud.content(new AuctionPriceHud.View(
                "FANCY_LEGGINGS",
                "Fancy Leggings",
                null,
                1_000_000.0,
                1,
                new LowestBinData.LookupResult(null, null, null, false, false, false)
        ));
        HudContent failed = AuctionPriceHud.content(new AuctionPriceHud.View(
                "FANCY_LEGGINGS",
                "Fancy Leggings",
                null,
                1_000_000.0,
                1,
                new LowestBinData.LookupResult(null, null, null, false, false, true)
        ));

        assertTrue(lines(missing).contains("No market price for this item"));
        assertTrue(lines(failed).contains("Market price unavailable"));
    }

    @Test
    void manipulatedLowestBinDoesNotControlTheMedian() {
        MarketGuardConfig.setMarketPriceBasis(ReferencePriceBasis.MEDIAN);
        HudContent content = AuctionPriceHud.content(view(1_000_000.0, 100_000.0, 1_000_000.0, 1_050_000.0));

        assertTrue(lines(content).contains("Median: ~1,000,000 coins (BIN 100,000 coins)"));
        assertTrue(verdicts(content).contains("Fair price"));
    }

    @Test
    void showsPriceTrendOnlyWhenAveragesDivergeStrongly() {
        HudContent falling = AuctionPriceHud.content(view(650_000.0, 650_000.0, 650_000.0, 1_000_000.0));
        HudContent mild = AuctionPriceHud.content(view(1_100_000.0, 1_100_000.0, 1_200_000.0, 1_000_000.0));

        assertEquals(color(ChatFormatting.YELLOW), color(falling, "Price trend: falling 35% vs. last month"));
        assertFalse(lines(mild).stream().anyMatch(line -> line.startsWith("Price trend")));
    }

    @Test
    void warnsOnlyWhenTheBazaarMarketForTheItemIsHardToResell() {
        BazaarData.Product illiquid = new BazaarData.Product("Fancy Leggings", 100.0, 80.0, 400L, 800L, 3_000L, 8_000L);
        BazaarData.Product liquid = new BazaarData.Product("Fancy Leggings", 100.0, 92.0, 50_000L, 60_000L, 500_000L, 600_000L);
        HudContent fresh;
        HudContent stale;
        HudContent fine;
        try (MockedStatic<BazaarData> bazaar = mockStatic(BazaarData.class)) {
            bazaar.when(() -> BazaarData.lookupProduct("FANCY_LEGGINGS"))
                    .thenReturn(new BazaarData.LookupResult(illiquid, false, false, false));
            fresh = AuctionPriceHud.content(view(1_000_000.0, 1_000_000.0, 1_000_000.0, 1_000_000.0));
            bazaar.when(() -> BazaarData.lookupProduct("FANCY_LEGGINGS"))
                    .thenReturn(new BazaarData.LookupResult(illiquid, true, false, false));
            stale = AuctionPriceHud.content(view(1_000_000.0, 1_000_000.0, 1_000_000.0, 1_000_000.0));
            bazaar.when(() -> BazaarData.lookupProduct("FANCY_LEGGINGS"))
                    .thenReturn(new BazaarData.LookupResult(liquid, false, false, false));
            fine = AuctionPriceHud.content(view(1_000_000.0, 1_000_000.0, 1_000_000.0, 1_000_000.0));
        }

        assertEquals(color(ChatFormatting.YELLOW), color(fresh, "Hard to resell on the Bazaar"));
        assertTrue(lines(stale).contains("Hard to resell on the Bazaar (Bazaar data may be outdated)"));
        assertFalse(lines(fine).stream().anyMatch(line -> line.startsWith("Hard to resell")));
    }

    @Test
    void warnsWhenAuctionPricesMayBeOutdated() {
        HudContent content = AuctionPriceHud.content(new AuctionPriceHud.View(
                "FANCY_LEGGINGS",
                "Fancy Leggings",
                null,
                1_000_000.0,
                1,
                new LowestBinData.LookupResult(1_000_000.0, 1_000_000.0, 1_000_000.0, true, false, false)
        ));

        assertEquals(color(ChatFormatting.YELLOW), color(content, "Prices may be outdated"));
    }

    @Test
    void staysHiddenOutsideAnIndividualAuctionView() {
        HudContent content = AuctionPriceHud.content(AuctionPriceHud.View.hidden());

        assertFalse(content.visible());
        assertEquals(List.of("Auction Price"), lines(content));
    }

    @Test
    void hidesWhenTheBinAuctionViewCloses() {
        AuctionPriceHud.update("ROCK", "Rock", null, 4_242_911_000D, 1);

        AuctionPriceHud.clear();

        assertFalse(AuctionPriceHud.Widgets.auctionPrice().visible());
    }

    private static AuctionPriceHud.View view(double auctionPrice, double lowestBin, Double average7d, Double average30d) {
        return new AuctionPriceHud.View(
                "FANCY_LEGGINGS",
                "Fancy Leggings",
                null,
                auctionPrice,
                1,
                new LowestBinData.LookupResult(lowestBin, average7d, average30d, false, false, false)
        );
    }

    private static List<String> lines(HudContent content) {
        return content.lines().stream().map(Component::getString).toList();
    }

    /** The lines without their appended segments, i.e. a verdict without the coin difference. */
    private static List<String> verdicts(HudContent content) {
        return content.lines().stream().map(AuctionPriceHudTest::ownText).toList();
    }

    /** Colour of the line or line segment whose own text is exactly {@code text}. */
    private static int color(HudContent content, String text) {
        return content.lines().stream()
                .flatMap(line -> Stream.concat(Stream.of(line), line.getSiblings().stream()))
                .filter(segment -> ownText(segment).equals(text))
                .findFirst()
                .map(segment -> segment.getStyle().getColor().getValue())
                .orElseThrow(() -> new AssertionError("missing segment: " + text + " in " + lines(content)));
    }

    private static String ownText(Component component) {
        return component.getContents().visit(Optional::of).orElse("");
    }

    private static int color(ChatFormatting formatting) {
        return TextColor.fromLegacyFormat(formatting).getValue();
    }
}
