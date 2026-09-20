package eu.tango.scamscreener.marketguard.auction;

import eu.tango.scamscreener.marketguard.MarketGuardConfig;
import eu.tango.scamscreener.marketguard.data.LowestBinData;
import eu.tango.scamscreener.marketguard.events.AuctionInteractEvent;
import eu.tango.scamscreener.marketguard.util.MessageBuilder;
import eu.tango.scamscreener.marketguard.util.SkyBlockItemUtil;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuctionPricingResolverTest {

    @AfterEach
    void resetBasis() {
        MarketGuardConfig.setMarketPriceBasis(ReferencePriceBasis.LOWEST_BIN);
    }

    @Test
    void missingAuctionItemDoesNotCancelWhenFailureShouldNotBlock() {
        AuctionInteractEvent.Context context = mock(AuctionInteractEvent.Context.class);

        try (MockedStatic<MessageBuilder> messageBuilder = mockStatic(MessageBuilder.class)) {
            assertNull(abortPricing(context, false));

            verify(context, never()).cancel();
            messageBuilder.verify(() -> MessageBuilder.error(any(), isNull()));
        }
    }

    @Test
    void missingAuctionItemCancelsWhenFailureShouldBlock() {
        AuctionInteractEvent.Context context = mock(AuctionInteractEvent.Context.class);

        try (MockedStatic<MessageBuilder> messageBuilder = mockStatic(MessageBuilder.class)) {
            assertNull(abortPricing(context, true));

            verify(context).cancel();
            messageBuilder.verify(() -> MessageBuilder.error(any(), isNull()));
        }
    }

    @Test
    void protectsWithTheMedianWhenTheMarketSignalsAgree() throws Exception {
        MarketGuardConfig.setMarketPriceBasis(ReferencePriceBasis.FAVOURABLE);
        AuctionInteractEvent.Context context = pricedContext("Fancy Leggings", 1_500_000D);

        try (
                MockedStatic<LowestBinData> lowestBinData = mockStatic(LowestBinData.class);
                MockedStatic<SkyBlockItemUtil> itemUtil = mockStatic(SkyBlockItemUtil.class)
        ) {
            stubItem(itemUtil, context, "FANCY_LEGGINGS", "Fancy Leggings");
            lowestBinData.when(() -> LowestBinData.lookupLowestBin("FANCY_LEGGINGS"))
                    .thenReturn(new LowestBinData.LookupResult(1_000_000D, 1_020_000D, 1_050_000D, false, false, false));

            assertEquals(1_020_000D, AuctionPricingResolver.resolve(context, null, AuctionPricingResolver.Check.PURCHASE).referencePrice());
            assertEquals(1_020_000D, AuctionPricingResolver.resolve(context, null, AuctionPricingResolver.Check.LISTING).referencePrice());
        }
    }

    @Test
    void comparesAPurchaseAgainstTheHighestSignalWhenTheMarketSignalsDisagree() throws Exception {
        MarketGuardConfig.setMarketPriceBasis(ReferencePriceBasis.FAVOURABLE);
        AuctionInteractEvent.Context context = pricedContext("2x Lava Rune I", 60_000D);

        try (
                MockedStatic<LowestBinData> lowestBinData = mockStatic(LowestBinData.class);
                MockedStatic<SkyBlockItemUtil> itemUtil = mockStatic(SkyBlockItemUtil.class)
        ) {
            stubItem(itemUtil, context, "LAVA_RUNE;1", "2x Lava Rune I");
            lowestBinData.when(() -> LowestBinData.lookupLowestBin("LAVA_RUNE;1"))
                    .thenReturn(new LowestBinData.LookupResult(4_998D, 3_030D, 8_307D, false, false, false));

            AuctionPricingResolver.PricingData pricing = AuctionPricingResolver.resolve(context, null, AuctionPricingResolver.Check.PURCHASE);

            assertEquals("LAVA_RUNE;1", pricing.itemId());
            assertEquals(8_307D, pricing.referencePrice());
            assertEquals(60_000D, pricing.playerPrice());
        }
    }

    @Test
    void comparesAListingAgainstTheLowestSignalWhenTheMarketSignalsDisagree() throws Exception {
        MarketGuardConfig.setMarketPriceBasis(ReferencePriceBasis.FAVOURABLE);
        AuctionInteractEvent.Context context = pricedContext("Prismarine Sinker", 170_000D);

        try (
                MockedStatic<LowestBinData> lowestBinData = mockStatic(LowestBinData.class);
                MockedStatic<SkyBlockItemUtil> itemUtil = mockStatic(SkyBlockItemUtil.class)
        ) {
            stubItem(itemUtil, context, "PRISMARINE_SINKER", "Prismarine Sinker");
            lowestBinData.when(() -> LowestBinData.lookupLowestBin("PRISMARINE_SINKER"))
                    .thenReturn(new LowestBinData.LookupResult(170_000D, 1_423_907D, 2_123_171D, false, false, false));

            AuctionPricingResolver.PricingData pricing = AuctionPricingResolver.resolve(context, null, AuctionPricingResolver.Check.LISTING);

            assertEquals(170_000D, pricing.referencePrice());
            assertEquals(170_000D, pricing.playerPrice());
        }
    }

    @Test
    void followsTheConfiguredMarketPriceBasis() throws Exception {
        AuctionInteractEvent.Context context = pricedContext("Prismarine Sinker", 170_000D);
        MarketGuardConfig.setMarketPriceBasis(ReferencePriceBasis.MEDIAN);

        try (
                MockedStatic<LowestBinData> lowestBinData = mockStatic(LowestBinData.class);
                MockedStatic<SkyBlockItemUtil> itemUtil = mockStatic(SkyBlockItemUtil.class)
        ) {
            stubItem(itemUtil, context, "PRISMARINE_SINKER", "Prismarine Sinker");
            lowestBinData.when(() -> LowestBinData.lookupLowestBin("PRISMARINE_SINKER"))
                    .thenReturn(new LowestBinData.LookupResult(170_000D, 1_423_907D, 2_123_171D, false, false, false));

            assertEquals(1_423_907D, AuctionPricingResolver.resolve(context, null, AuctionPricingResolver.Check.LISTING).referencePrice());

            MarketGuardConfig.setMarketPriceBasis(ReferencePriceBasis.LOWEST_BIN);
            assertEquals(170_000D, AuctionPricingResolver.resolve(context, null, AuctionPricingResolver.Check.PURCHASE).referencePrice());
            assertEquals(170_000D, AuctionPricingResolver.resolve(context, null, AuctionPricingResolver.Check.LISTING).referencePrice());
        }
    }

    @Test
    void scalesTheReferenceToTheAuctionStack() throws Exception {
        MarketGuardConfig.setMarketPriceBasis(ReferencePriceBasis.FAVOURABLE);
        AuctionInteractEvent.Context context = pricedContext("47x Shiny Orb", 400_000D);

        try (
                MockedStatic<LowestBinData> lowestBinData = mockStatic(LowestBinData.class);
                MockedStatic<SkyBlockItemUtil> itemUtil = mockStatic(SkyBlockItemUtil.class)
        ) {
            stubItem(itemUtil, context, "SHINY_ORB", "47x Shiny Orb");
            itemUtil.when(() -> SkyBlockItemUtil.getStackCount(context.getAuctionItemStack())).thenReturn(47);
            lowestBinData.when(() -> LowestBinData.lookupLowestBin("SHINY_ORB"))
                    .thenReturn(new LowestBinData.LookupResult(1_851.85, 11_694D, 9_956D, false, false, false));

            AuctionPricingResolver.PricingData pricing = AuctionPricingResolver.resolve(context, null, AuctionPricingResolver.Check.PURCHASE);

            assertEquals(11_694D * 47, pricing.referencePrice());
            assertEquals(400_000D, pricing.playerPrice());
        }
    }

    @Test
    void skipsProtectionWhenNoReferencePriceExists() throws Exception {
        AuctionInteractEvent.Context context = mock(AuctionInteractEvent.Context.class);
        ItemStack auctionItem = mock(ItemStack.class);
        when(context.getAuctionItemStack()).thenReturn(auctionItem);
        when(auctionItem.getHoverName()).thenReturn(Component.literal("Lava Rune I"));

        try (
                MockedStatic<LowestBinData> lowestBinData = mockStatic(LowestBinData.class);
                MockedStatic<SkyBlockItemUtil> itemUtil = mockStatic(SkyBlockItemUtil.class)
        ) {
            itemUtil.when(() -> SkyBlockItemUtil.getSkyblockId(auctionItem)).thenReturn("LAVA_RUNE;1");
            itemUtil.when(() -> SkyBlockItemUtil.getDisplayName(auctionItem)).thenReturn("Lava Rune I");
            lowestBinData.when(() -> LowestBinData.lookupLowestBin("LAVA_RUNE;1"))
                    .thenReturn(new LowestBinData.LookupResult(null, null, null, false, false, false));

            assertNull(AuctionPricingResolver.resolve(context, null, AuctionPricingResolver.Check.PURCHASE));
            verify(context, never()).getPlayerPrice();
        }
    }

    private static AuctionInteractEvent.Context pricedContext(String displayName, double playerPrice) throws Exception {
        AuctionInteractEvent.Context context = mock(AuctionInteractEvent.Context.class);
        ItemStack auctionItem = mock(ItemStack.class);
        when(context.getAuctionItemStack()).thenReturn(auctionItem);
        when(context.getPlayerPrice()).thenReturn(playerPrice);
        when(auctionItem.getHoverName()).thenReturn(Component.literal(displayName));
        return context;
    }

    private static void stubItem(MockedStatic<SkyBlockItemUtil> itemUtil, AuctionInteractEvent.Context context, String itemId, String displayName) {
        ItemStack auctionItem = context.getAuctionItemStack();
        itemUtil.when(() -> SkyBlockItemUtil.getSkyblockId(auctionItem)).thenReturn(itemId);
        itemUtil.when(() -> SkyBlockItemUtil.getDisplayName(auctionItem)).thenReturn(displayName);
        itemUtil.when(() -> SkyBlockItemUtil.getStackCount(auctionItem)).thenReturn(1);
    }

    private static AuctionPricingResolver.PricingData abortPricing(
            AuctionInteractEvent.Context context,
            boolean cancelOnFailure
    ) {
        try {
            Method method = AuctionPricingResolver.class.getDeclaredMethod(
                    "abortPricing",
                    AuctionInteractEvent.Context.class,
                    LocalPlayer.class,
                    Component.class,
                    boolean.class
            );
            method.setAccessible(true);
            return (AuctionPricingResolver.PricingData) method.invoke(
                    null,
                    context,
                    null,
                    Component.literal("Could not find Auction Item"),
                    cancelOnFailure
            );
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }
}
