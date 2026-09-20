package eu.tango.scamscreener.marketguard.events;

import eu.tango.scamscreener.marketguard.auction.AuctionInventory;
import eu.tango.scamscreener.marketguard.auction.AuctionSlots;
import eu.tango.scamscreener.marketguard.util.SkyBlockItemUtil;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.ContainerInput;

import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

public final class AuctionInteractEvent {

    private AuctionInteractEvent() {}

    /**
     * Listener Interface
     *
     * All information that will be passed on later
     */
    @FunctionalInterface
    public interface Listener{
        void onInteract(Context context);
    }

    // Fabric Event Instance
    public static final Event<Listener> EVENT =
            EventFactory.createArrayBacked(
                    Listener.class,
                    listeners -> context -> {
                        for (Listener listener : listeners) {
                            listener.onInteract(context);
                        }
                    }
            );

    @Getter @RequiredArgsConstructor
    public static class Context {

        private final Minecraft mc;
        private final AbstractContainerScreen<?> screen;
        private final AbstractContainerMenu screenHandler;
        private final Slot slot;
        private final int slotId;
        private final Integer button;
        private final ContainerInput actionType;

        private boolean cancelled = false;
        private final IntConsumer bypassScheduler;
        private final IntSupplier bypassRemainingSupplier;

        public void cancel() {
            this.cancelled = true;
        }

        public void bypass(int clicks) {
            bypassScheduler.accept(clicks);
        }

        public int getRemainingBypassClicks() {
            return Math.max(0, bypassRemainingSupplier.getAsInt());
        }

        public ItemStack getItem() {
            return slot == null ? ItemStack.EMPTY : slot.getItem();
        }

        public String getInventoryName() {
            return screen.getTitle().getString();
        }

        public ItemStack getAuctionItemStack() {
            int itemSlot = AuctionSlots.ITEM.getSlot();
            return screenHandler.getSlot(itemSlot).getItem();
        }

        public String getAuctionItemId() {
            return SkyBlockItemUtil.getSkyblockId(getAuctionItemStack());
        }

        public double getPlayerPrice() throws Exception {
            if (isBinView()) {
                return SkyBlockItemUtil.getPriceFromNBT(getAuctionItemStack());
            }

            int priceSlot = AuctionSlots.ITEM_PRICE.getSlot();
            return SkyBlockItemUtil.getPriceFromNBT(screenHandler.getSlot(priceSlot).getItem());
        }

        public boolean isCreateBinClick() {
            return AuctionInventory.CREATE_BIN.matches(getInventoryName())
                    && AuctionSlots.CREATE_BIN.matchesSlot(getSlotId());
        }

        public boolean isBinView() {
            return AuctionSlots.BUY_BIN_ITEM.matchesSlot(getSlotId())
                    && AuctionSlots.BUY_BIN_ITEM.matchesStack(getItem());
        }

    }

}

