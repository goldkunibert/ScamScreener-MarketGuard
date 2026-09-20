package eu.tango.scamscreener.marketguard.auction;

import eu.tango.scamscreener.marketguard.MarketGuardConfig;
import eu.tango.scamscreener.marketguard.data.LowestBinData;
import eu.tango.scamscreener.marketguard.events.AuctionInteractEvent;
import eu.tango.scamscreener.marketguard.util.MessageBuilder;
import eu.tango.scamscreener.marketguard.util.SkyBlockItemUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuctionOverbiddingTest {

    @AfterEach
    void resetThreshold() {
        AuctionOverbidding.setThreshold(120);
        MarketGuardConfig.setMarketPriceBasis(ReferencePriceBasis.LOWEST_BIN);
    }

    @Test
    void onInteractTriggersBlacklistCheckForBinBuyClick() {
        AuctionInteractEvent.Context context = mock(AuctionInteractEvent.Context.class);
        when(context.isBinView()).thenReturn(true);
        when(context.getAuctionItemId()).thenReturn("FANCY_LEGGINGS");
        when(context.getMc()).thenReturn(null);

        try (MockedStatic<LowestBinData> lowestBin = mockStatic(LowestBinData.class)) {
            AuctionOverbidding.onInteract(context);

            lowestBin.verify(() -> LowestBinData.checkBlacklistedAuctioneerAsyncIfNeeded("FANCY_LEGGINGS"));
        }
    }

    @Test
    void onInteractSkipsBlacklistCheckWhenClickWasNotBinBuyClick() {
        AuctionInteractEvent.Context context = mock(AuctionInteractEvent.Context.class);
        when(context.isBinView()).thenReturn(false);

        try (MockedStatic<LowestBinData> lowestBin = mockStatic(LowestBinData.class)) {
            AuctionOverbidding.onInteract(context);

            lowestBin.verifyNoInteractions();
            verify(context, never()).cancel();
        }
    }

    @Test
    void cancelsABinPurchaseAboveEveryPriceSignal() throws Exception {
        AuctionInteractEvent.Context context = pricedClick("LAVA_RUNE;1", "2x Lava Rune I", 60_000D);

        try (
                MockedStatic<LowestBinData> lowestBin = mockStatic(LowestBinData.class);
                MockedStatic<SkyBlockItemUtil> itemUtil = mockStatic(SkyBlockItemUtil.class);
                MockedStatic<MessageBuilder> messages = mockStatic(MessageBuilder.class)
        ) {
            stubMarket(lowestBin, itemUtil, context, "LAVA_RUNE;1", 4_998D, 3_030D, 8_307D);

            AuctionOverbidding.check(context, null);

            verify(context).cancel();
            verify(context).bypass(4);
            messages.verify(() -> MessageBuilder.overbidding(eq("LAVA_RUNE;1"), eq("2x Lava Rune I"), anyDouble(), anyDouble(), anyInt(), any()));
        }
    }

    @Test
    void letsABinPurchaseThroughWhenAnyPriceSignalSupportsIt() throws Exception {
        MarketGuardConfig.setMarketPriceBasis(ReferencePriceBasis.FAVOURABLE);
        AuctionInteractEvent.Context context = pricedClick("PRISMARINE_BOW", "Prismarine Bow", 1_300_000D);

        try (
                MockedStatic<LowestBinData> lowestBin = mockStatic(LowestBinData.class);
                MockedStatic<SkyBlockItemUtil> itemUtil = mockStatic(SkyBlockItemUtil.class);
                MockedStatic<MessageBuilder> messages = mockStatic(MessageBuilder.class)
        ) {
            stubMarket(lowestBin, itemUtil, context, "PRISMARINE_BOW", 250_000D, 1_372_657D, 2_708_457D);

            AuctionOverbidding.check(context, null);

            verify(context, never()).cancel();
            messages.verifyNoInteractions();
        }
    }

    /** A click context whose auction item resolves to {@code itemId} at the given player price. */
    static AuctionInteractEvent.Context pricedClick(String itemId, String displayName, double playerPrice) throws Exception {
        AuctionInteractEvent.Context context = mock(AuctionInteractEvent.Context.class);
        ItemStack auctionItem = mock(ItemStack.class);
        when(context.getAuctionItemId()).thenReturn(itemId);
        when(context.getAuctionItemStack()).thenReturn(auctionItem);
        when(context.getPlayerPrice()).thenReturn(playerPrice);
        when(auctionItem.getHoverName()).thenReturn(Component.literal(displayName));
        return context;
    }

    static void stubMarket(
            MockedStatic<LowestBinData> lowestBin,
            MockedStatic<SkyBlockItemUtil> itemUtil,
            AuctionInteractEvent.Context context,
            String itemId,
            Double lowest,
            Double average7d,
            Double average30d
    ) {
        ItemStack auctionItem = context.getAuctionItemStack();
        String displayName = auctionItem.getHoverName().getString();
        itemUtil.when(() -> SkyBlockItemUtil.getSkyblockId(auctionItem)).thenReturn(itemId);
        itemUtil.when(() -> SkyBlockItemUtil.getDisplayName(auctionItem)).thenReturn(displayName);
        itemUtil.when(() -> SkyBlockItemUtil.getStackCount(auctionItem)).thenReturn(1);
        lowestBin.when(() -> LowestBinData.lookupLowestBin(itemId))
                .thenReturn(new LowestBinData.LookupResult(lowest, average7d, average30d, false, false, false));
    }
}
