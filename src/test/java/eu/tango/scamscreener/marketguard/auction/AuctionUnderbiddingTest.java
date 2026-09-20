package eu.tango.scamscreener.marketguard.auction;

import eu.tango.scamscreener.marketguard.data.LowestBinData;
import eu.tango.scamscreener.marketguard.events.AuctionInteractEvent;
import eu.tango.scamscreener.marketguard.util.MessageBuilder;
import eu.tango.scamscreener.marketguard.util.SkyBlockItemUtil;
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

class AuctionUnderbiddingTest {

    @AfterEach
    void resetThreshold() {
        AuctionUnderbidding.setThreshold(AuctionUnderbidding.DEFAULT_THRESHOLD);
    }

    @Test
    void onInteractReturnsImmediatelyWhenNotCreateBinClick() {
        AuctionInteractEvent.Context context = mock(AuctionInteractEvent.Context.class);
        when(context.isCreateBinClick()).thenReturn(false);

        AuctionUnderbidding.onInteract(context);

        verify(context, never()).cancel();
        verify(context, never()).getAuctionItemStack();
    }

    @Test
    void letsAListingAtTheLowestBinThroughWhenTheAveragesDisagree() throws Exception {
        AuctionInteractEvent.Context context = AuctionOverbiddingTest.pricedClick("PRISMARINE_SINKER", "Prismarine Sinker", 170_000D);

        try (
                MockedStatic<LowestBinData> lowestBin = mockStatic(LowestBinData.class);
                MockedStatic<SkyBlockItemUtil> itemUtil = mockStatic(SkyBlockItemUtil.class);
                MockedStatic<MessageBuilder> messages = mockStatic(MessageBuilder.class)
        ) {
            AuctionOverbiddingTest.stubMarket(lowestBin, itemUtil, context, "PRISMARINE_SINKER", 170_000D, 1_423_907D, 2_123_171D);

            AuctionUnderbidding.check(context, null);

            verify(context, never()).cancel();
            messages.verifyNoInteractions();
        }
    }

    @Test
    void cancelsAListingBelowEveryPriceSignal() throws Exception {
        AuctionInteractEvent.Context context = AuctionOverbiddingTest.pricedClick("PRISMARINE_SINKER", "Prismarine Sinker", 100_000D);

        try (
                MockedStatic<LowestBinData> lowestBin = mockStatic(LowestBinData.class);
                MockedStatic<SkyBlockItemUtil> itemUtil = mockStatic(SkyBlockItemUtil.class);
                MockedStatic<MessageBuilder> messages = mockStatic(MessageBuilder.class)
        ) {
            AuctionOverbiddingTest.stubMarket(lowestBin, itemUtil, context, "PRISMARINE_SINKER", 170_000D, 1_423_907D, 2_123_171D);

            AuctionUnderbidding.check(context, null);

            verify(context).cancel();
            messages.verify(() -> MessageBuilder.underbidding(eq("PRISMARINE_SINKER"), eq("Prismarine Sinker"), anyDouble(), anyDouble(), anyInt(), any()));
        }
    }
}
