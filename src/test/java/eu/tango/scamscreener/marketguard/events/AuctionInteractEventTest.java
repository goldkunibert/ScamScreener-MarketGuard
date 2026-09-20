package eu.tango.scamscreener.marketguard.events;

import eu.tango.scamscreener.marketguard.auction.AuctionSlots;
import eu.tango.scamscreener.marketguard.util.SkyBlockItemUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class AuctionInteractEventTest {

    @Test
    void eventInvokesAllRegisteredListeners() {
        AtomicInteger calls = new AtomicInteger();

        AuctionInteractEvent.EVENT.register(context -> calls.incrementAndGet());
        AuctionInteractEvent.EVENT.register(context -> calls.incrementAndGet());

        AuctionInteractEvent.EVENT.invoker().onInteract(null);

        assertEquals(2, calls.get());
    }

    @Test
    void cancelMarksContextAsCancelled() {
        AuctionInteractEvent.Context context = new AuctionInteractEvent.Context(
                null,
                null,
                null,
                null,
                0,
                null,
                null,
                clicks -> { },
                () -> 0
        );

        assertFalse(context.isCancelled());
        context.cancel();
        assertTrue(context.isCancelled());
    }

    @Test
    void buyBinSlotPatternMatchesExpectedButtonName() {
        assertTrue(AuctionSlots.BUY_BIN_ITEM.matchesSlot(31));
        assertTrue(AuctionSlots.BUY_BIN_ITEM.matchesName("Buy Item Right Now"));
    }

    @Test
    void buyBinSlotPatternRejectsWrongSlotOrButtonName() {
        assertFalse(AuctionSlots.BUY_BIN_ITEM.matchesSlot(13));
        assertFalse(AuctionSlots.BUY_BIN_ITEM.matchesName("Confirm Purchase"));
    }

    @Test
    void binPurchaseReadsThePriceFromTheAuctionItemInsteadOfTheBuyButton() throws Exception {
        AbstractContainerMenu menu = mock(AbstractContainerMenu.class);
        Slot buyButtonSlot = mock(Slot.class);
        Slot auctionItemSlot = mock(Slot.class);
        ItemStack buyButton = mock(ItemStack.class);
        ItemStack auctionItem = mock(ItemStack.class);

        when(buyButtonSlot.getItem()).thenReturn(buyButton);
        when(buyButton.isEmpty()).thenReturn(false);
        when(buyButton.getHoverName()).thenReturn(Component.literal("Buy Item Right Now"));
        when(menu.getSlot(AuctionSlots.ITEM.getSlot())).thenReturn(auctionItemSlot);
        when(auctionItemSlot.getItem()).thenReturn(auctionItem);

        AuctionInteractEvent.Context context = new AuctionInteractEvent.Context(
                null, null, menu, buyButtonSlot, AuctionSlots.BUY_BIN_ITEM.getSlot(), null, null, clicks -> { }, () -> 0
        );

        try (MockedStatic<SkyBlockItemUtil> itemUtil = mockStatic(SkyBlockItemUtil.class)) {
            itemUtil.when(() -> SkyBlockItemUtil.getPriceFromNBT(auctionItem)).thenReturn(1_220_000D);

            assertEquals(1_220_000D, context.getPlayerPrice());
            itemUtil.verify(() -> SkyBlockItemUtil.getPriceFromNBT(auctionItem));
        }
    }
}
