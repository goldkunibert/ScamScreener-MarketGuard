package eu.tango.scamscreener.marketguard.mixin;

import eu.tango.scamscreener.marketguard.MarketGuard;
import eu.tango.scamscreener.marketguard.auction.AuctionInventory;
import eu.tango.scamscreener.marketguard.auction.AuctionSlots;
import eu.tango.scamscreener.marketguard.data.LowestBinData;
import eu.tango.scamscreener.marketguard.hud.AuctionPriceHud;
import eu.tango.scamscreener.marketguard.hud.ForgeProfitHud;
import eu.tango.scamscreener.marketguard.hud.HudCustomization;
import eu.tango.scamscreener.marketguard.hud.MinionProfitHud;
import eu.tango.scamscreener.marketguard.hud.PlayerHud;
import eu.tango.scamscreener.marketguard.hud.TradeGuardHud;
import eu.tango.scamscreener.marketguard.profittracker.ProfitTracker;
import eu.tango.scamscreener.marketguard.screen.HypixelScreens;
import eu.tango.scamscreener.marketguard.util.SkyBlockItemUtil;
import eu.tango.scamscreener.marketguard.events.AuctionInteractEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.ContainerInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractContainerScreen.class)
public abstract class AuctionHouseMixin {
    // slotClicked and removed() only run on the render thread, so no atomics needed.
    private static int bypassCountdown = 0;
    private static volatile String pendingConfirmPurchaseItemId = null;
    private static volatile String lastSeenBinItemId = null;
    @Unique
    private String marketguard$lastDeferredBlacklistCheckKey = null;
    @Unique
    private boolean marketguard$loggedFilledPurchaseFlowSlots = false;
    @Unique
    private boolean marketguard$playerHudShown = false;
    @Unique
    private String marketguard$auctionPriceWidgetKey = null;
    @Unique
    private ItemStack marketguard$lastItemSlotStack = null;

    @Inject(method = "init", at = @At("TAIL"))
    private void prefetchLowestBinOnAuctionScreens(CallbackInfo ci) {
        AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>)(Object)this;
        String title = screen.getTitle() != null ? screen.getTitle().getString() : null;
        HudCustomization.setCurrentScreenTitle(title);
        showPlayerHudIfAvailable(screen, title);
        if (!isAuctionScreen(title)) {
            MarketGuard.debug("Container screen opened title='{}'", title);
            return;
        }
        if (!isBinPurchaseFlowScreen(title)) {
            clearBinPurchaseFlowState();
        }

        LowestBinData.resetBlacklistNoticeState();
        MarketGuard.debug("Auction screen opened title='{}', requesting Lowest BIN refresh if needed", title);
        LowestBinData.refreshAsyncIfNeeded();
        debugPurchaseFlowSlots(screen, title);
        triggerBlacklistCheck(screen, title);
    }

    @Inject(
            method = "slotClicked(Lnet/minecraft/world/inventory/Slot;IILnet/minecraft/world/inventory/ContainerInput;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void cancelClicksOnCustomInventories(Slot slot, int slotId, int button, ContainerInput actionType, CallbackInfo ci) {
        if (slot == null) return;

        Minecraft mc = Minecraft.getInstance();
        AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>)(Object)this;
        String currentTitle = screen.getTitle() != null ? screen.getTitle().getString() : null;
        AbstractContainerMenu sh = mc.player != null ? mc.player.containerMenu : null;
        AuctionInteractEvent.Context context = null;
        if (mc.player != null && currentTitle != null && sh != null) {
            context = new AuctionInteractEvent.Context(
                    mc,
                    screen,
                    sh,
                    slot,
                    slotId,
                    button,
                    actionType,
                    AuctionHouseMixin::scheduleBypass,
                    () -> bypassCountdown
            );
        }
        if (isAuctionScreen(currentTitle)) {
            MarketGuard.debug(
                    "AbstractContainerScreen click title='{}' slotId={} button={} actionType={} slotItem='{}' bypassRemaining={}",
                    currentTitle,
                    slotId,
                    button,
                    actionType,
                    slot.getItem().isEmpty() ? "<empty>" : slot.getItem().getHoverName().getString(),
                    bypassCountdown
            );
            if (context != null && context.isBinView()) {
                rememberPendingConfirmPurchaseItemId(context.getAuctionItemId());
            }
        }
        if (consumeBypass()) return;

        if (context == null) return;
        AuctionInteractEvent.EVENT.invoker().onInteract(context);

        if (context.isCancelled()) {
            MarketGuard.debug("Click cancelled for title='{}' slotId={}", currentTitle, slotId);
            ci.cancel();
            return;
        }

        ProfitTracker.onHandledScreenClick(mc, currentTitle, sh, slot, slotId, actionType);
    }

    @Inject(method = "removed()V", at = @At("HEAD"))
    private void resetBypassOnScreenClose(CallbackInfo ci) {
        HudCustomization.setCurrentScreenTitle(null);
        resetBypass();
        LowestBinData.resetBlacklistNoticeState();
        if (marketguard$playerHudShown) {
            PlayerHud.clear();
            marketguard$playerHudShown = false;
        }
        AuctionPriceHud.clear();
        marketguard$auctionPriceWidgetKey = null;
        TradeGuardHud.clear();
        MinionProfitHud.clear();
        ForgeProfitHud.clear();
    }

    // Once per client tick, not per rendered frame: the HUD widgets only refresh per tick anyway.
    @Inject(method = "tick()V", at = @At("HEAD"))
    private void runDeferredAuctionBlacklistCheck(CallbackInfo ci) {
        AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>)(Object)this;
        String title = screen.getTitle() != null ? screen.getTitle().getString() : null;
        AbstractContainerMenu menu = screen.getMenu();
        HudCustomization.setCurrentScreenTitle(title);
        showPlayerHudIfAvailable(screen, title);
        TradeGuardHud.update(menu, title);
        MinionProfitHud.update(menu, title);
        ForgeProfitHud.update(menu, title);

        int itemSlot = AuctionSlots.ITEM.getSlot();
        ItemStack itemSlotStack = menu != null && menu.slots.size() > itemSlot ? menu.getSlot(itemSlot).getItem() : null;
        if (!itemSlotStackChanged(itemSlotStack)) {
            return;
        }

        updateAuctionPriceWidget(screen, title);
        if (!isAuctionScreen(title)) {
            return;
        }

        if (isBinPurchaseFlowScreen(title) && !marketguard$loggedFilledPurchaseFlowSlots && hasAnyNonEmptyAuctionTopSlot(screen)) {
            marketguard$loggedFilledPurchaseFlowSlots = true;
            debugPurchaseFlowSlots(screen, title);
        }

        String itemId = resolveAuctionItemId(screen, title);
        if (itemId == null) {
            return;
        }

        String checkKey = title + "|" + itemId;
        if (checkKey.equals(marketguard$lastDeferredBlacklistCheckKey)) {
            return;
        }

        marketguard$lastDeferredBlacklistCheckKey = checkKey;
        MarketGuard.debug("Deferred auction blacklist check requested title='{}' itemId='{}'", title, itemId);
        LowestBinData.checkBlacklistedAuctioneerAsyncIfNeeded(itemId);
    }

    private static void scheduleBypass(int clicks) {
        // `clicks` includes the current blocked click.
        // Example: bypass(4) => block current + next 2, then bypass on the 4th click.
        if (clicks <= 0 || bypassCountdown > 0) return;
        bypassCountdown = clicks - 1;
        MarketGuard.debug("Scheduled bypass clicks={} countdown={}", clicks, bypassCountdown);
    }

    private static boolean consumeBypass() {
        if (bypassCountdown <= 0) return false;
        bypassCountdown--;
        MarketGuard.debug("Consuming bypass remaining={}", bypassCountdown);
        return bypassCountdown == 0;
    }

    private static void resetBypass() {
        if (bypassCountdown > 0) {
            MarketGuard.debug("Resetting bypass state countdown={}", bypassCountdown);
        }
        bypassCountdown = 0;
    }

    private static boolean isAuctionScreen(String title) {
        return AuctionInventory.matchesAny(title);
    }

    private void showPlayerHudIfAvailable(AbstractContainerScreen<?> screen, String title) {
        if (marketguard$playerHudShown) {
            return;
        }

        String player = HypixelScreens.profilePlayer(title);
        String context = "profile";
        if (player == null && HypixelScreens.isTrade(title)) {
            player = HypixelScreens.tradePartner(title);
            context = "trade";
        }
        if (player == null && AuctionInventory.BIN_VIEW.matches(title)) {
            player = HypixelScreens.binSeller(screen.getMenu());
            context = "bin";
        }
        if (player == null) {
            return;
        }

        MarketGuard.debug("Player HUD opened context='{}' player='{}'", context, player);
        marketguard$playerHudShown = true;
        PlayerHud.show(player, null);
    }

    // Slot.set replaces the stack instance on every container update, so identity is enough to detect a change.
    private boolean itemSlotStackChanged(ItemStack itemSlotStack) {
        if (itemSlotStack == marketguard$lastItemSlotStack) {
            return false;
        }
        marketguard$lastItemSlotStack = itemSlotStack;
        return true;
    }

    private void updateAuctionPriceWidget(AbstractContainerScreen<?> screen, String title) {
        if (!AuctionInventory.BIN_VIEW.matches(title) || screen.getMenu() == null) {
            AuctionPriceHud.clear();
            marketguard$auctionPriceWidgetKey = null;
            return;
        }

        int itemSlot = AuctionSlots.ITEM.getSlot();
        if (screen.getMenu().slots.size() <= itemSlot) {
            AuctionPriceHud.clear();
            marketguard$auctionPriceWidgetKey = null;
            return;
        }

        ItemStack auctionItem = screen.getMenu().getSlot(itemSlot).getItem();
        String itemId = SkyBlockItemUtil.getSkyblockId(auctionItem);
        if (itemId == null) {
            AuctionPriceHud.clear();
            marketguard$auctionPriceWidgetKey = null;
            return;
        }

        String displayName = SkyBlockItemUtil.getDisplayName(auctionItem);
        String widgetKey = itemId + "|" + displayName;
        if (widgetKey.equals(marketguard$auctionPriceWidgetKey)) {
            return;
        }

        try {
            AuctionPriceHud.update(
                    itemId,
                    displayName,
                    SkyBlockItemUtil.getNameColor(auctionItem),
                    SkyBlockItemUtil.getPriceFromNBT(auctionItem),
                    SkyBlockItemUtil.getStackCount(auctionItem)
            );
            marketguard$auctionPriceWidgetKey = widgetKey;
        } catch (Exception ignored) {
            AuctionPriceHud.clear();
            marketguard$auctionPriceWidgetKey = null;
        }
    }

    private static void triggerBlacklistCheck(AbstractContainerScreen<?> screen, String title) {
        String itemId = resolveAuctionItemId(screen, title);
        if (itemId == null) {
            MarketGuard.debug("Auction blacklist check skipped title='{}' because no SkyBlock item id was available", title);
            return;
        }

        MarketGuard.debug("Auction blacklist check requested title='{}' itemId='{}'", title, itemId);
        LowestBinData.checkBlacklistedAuctioneerAsyncIfNeeded(itemId);
    }

    private static String resolveAuctionItemId(AbstractContainerScreen<?> screen, String title) {
        String itemId = resolveAuctionItemId(screen);
        if (itemId != null) {
            if (isBinPurchaseFlowScreen(title)) {
                rememberLastSeenBinItemId(itemId);
            }
            if (isConfirmPurchaseScreen(title)) {
                clearPendingConfirmPurchaseItemId();
            }
            return itemId;
        }

        if (!isConfirmPurchaseScreen(title)) {
            return null;
        }

        String pendingItemId = consumePendingConfirmPurchaseItemId();
        if (pendingItemId != null) {
            MarketGuard.debug("Using pending BIN item id for confirm purchase title='{}' itemId='{}'", title, pendingItemId);
            return pendingItemId;
        }

        if (lastSeenBinItemId != null) {
            MarketGuard.debug("Using last seen BIN item id for confirm purchase title='{}' itemId='{}'", title, lastSeenBinItemId);
        }
        return lastSeenBinItemId;
    }

    private static String resolveAuctionItemId(AbstractContainerScreen<?> screen) {
        if (screen == null || screen.getMenu() == null) {
            return null;
        }

        int itemSlot = AuctionSlots.ITEM.getSlot();
        if (screen.getMenu().slots.size() <= itemSlot) {
            return null;
        }

        ItemStack itemStack = screen.getMenu().getSlot(itemSlot).getItem();
        if (itemStack == null || itemStack.isEmpty()) {
            return null;
        }

        return SkyBlockItemUtil.getSkyblockId(itemStack);
    }

    private static void debugPurchaseFlowSlots(AbstractContainerScreen<?> screen, String title) {
        if (!isBinPurchaseFlowScreen(title)) {
            return;
        }
        if (screen == null || screen.getMenu() == null) {
            MarketGuard.debug("Auction slot dump skipped title='{}' because screen handler was missing", title);
            return;
        }

        int expectedItemSlot = AuctionSlots.ITEM.getSlot();
        MarketGuard.debug(
                "Auction slot dump title='{}' expectedBinViewItemSlot={} slotCount={}",
                title,
                expectedItemSlot,
                screen.getMenu().slots.size()
        );

        int upperBound = Math.min(53, screen.getMenu().slots.size() - 1);
        for (int slotIndex = 0; slotIndex <= upperBound; slotIndex++) {
            String marker = slotIndex == expectedItemSlot ? " expectedItemSlot" : "";
            String itemName = readSlotItemName(screen, slotIndex);
            String itemId = readSlotItemId(screen, slotIndex);
            MarketGuard.debug(
                    "Auction slot dump title='{}' slot={}{} item='{}' skyblockId='{}'",
                    title,
                    slotIndex,
                    marker,
                    itemName,
                    itemId == null ? "<none>" : itemId
            );
        }
    }

    private static boolean hasAnyNonEmptyAuctionTopSlot(AbstractContainerScreen<?> screen) {
        if (screen == null || screen.getMenu() == null) {
            return false;
        }

        int upperBound = Math.min(26, screen.getMenu().slots.size() - 1);
        for (int slotIndex = 0; slotIndex <= upperBound; slotIndex++) {
            ItemStack stack = screen.getMenu().getSlot(slotIndex).getItem();
            if (stack != null && !stack.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private static String readSlotItemName(AbstractContainerScreen<?> screen, int slotIndex) {
        if (screen == null || screen.getMenu() == null || screen.getMenu().slots.size() <= slotIndex) {
            return "<missing>";
        }

        ItemStack stack = screen.getMenu().getSlot(slotIndex).getItem();
        if (stack == null || stack.isEmpty()) {
            return "<empty>";
        }

        return stack.getHoverName().getString();
    }

    private static String readSlotItemId(AbstractContainerScreen<?> screen, int slotIndex) {
        if (screen == null || screen.getMenu() == null || screen.getMenu().slots.size() <= slotIndex) {
            return null;
        }

        ItemStack stack = screen.getMenu().getSlot(slotIndex).getItem();
        if (stack == null || stack.isEmpty()) {
            return null;
        }

        return SkyBlockItemUtil.getSkyblockId(stack);
    }

    private static boolean isBinPurchaseFlowScreen(String title) {
        return title != null
                && (AuctionInventory.BIN_VIEW.matches(title)
                || AuctionInventory.CONFIRM_PURCHASE.matches(title));
    }

    private static boolean isConfirmPurchaseScreen(String title) {
        return AuctionInventory.CONFIRM_PURCHASE.matches(title);
    }

    private static void rememberPendingConfirmPurchaseItemId(String itemId) {
        pendingConfirmPurchaseItemId = itemId;
        if (itemId != null) {
            MarketGuard.debug("Remembered BIN item id for confirm purchase fallback itemId='{}'", itemId);
        }
    }

    private static void rememberLastSeenBinItemId(String itemId) {
        lastSeenBinItemId = itemId;
    }

    private static String consumePendingConfirmPurchaseItemId() {
        String itemId = pendingConfirmPurchaseItemId;
        pendingConfirmPurchaseItemId = null;
        return itemId;
    }

    private static void clearPendingConfirmPurchaseItemId() {
        pendingConfirmPurchaseItemId = null;
    }

    private static void clearBinPurchaseFlowState() {
        pendingConfirmPurchaseItemId = null;
        lastSeenBinItemId = null;
    }

}
