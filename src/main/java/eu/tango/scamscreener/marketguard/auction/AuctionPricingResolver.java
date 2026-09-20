package eu.tango.scamscreener.marketguard.auction;

import eu.tango.scamscreener.marketguard.MarketGuard;
import eu.tango.scamscreener.marketguard.MarketGuardConfig;
import eu.tango.scamscreener.marketguard.data.LowestBinData;
import eu.tango.scamscreener.marketguard.events.AuctionInteractEvent;
import eu.tango.scamscreener.marketguard.util.SkyBlockItemUtil;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;

import static eu.tango.scamscreener.marketguard.util.MessageBuilder.error;

final class AuctionPricingResolver {
    private AuctionPricingResolver() {}

    record PricingData(String itemId, String displayName, double referencePrice, double playerPrice) {}

    enum Check {
        /** Buying a BIN: the click goes through when the price cannot be read. */
        PURCHASE(false),
        /** Creating a BIN: the click is cancelled when the price cannot be read. */
        LISTING(true);

        private final boolean cancelOnFailure;

        Check(boolean cancelOnFailure) {
            this.cancelOnFailure = cancelOnFailure;
        }
    }

    static PricingData resolve(AuctionInteractEvent.Context context, LocalPlayer player, Check check) {
        boolean cancelOnFailure = check.cancelOnFailure;
        ItemStack itemStack = context.getAuctionItemStack();
        MarketGuard.debug(
                "Resolving pricing title='{}' clickedSlot={} actionType={} auctionItem='{}'",
                context.getInventoryName(),
                context.getSlotId(),
                context.getActionType(),
                itemStack.isEmpty() ? "<empty>" : itemStack.getHoverName().getString()
        );
        if (itemStack.isEmpty()) {
            MarketGuard.debug("Pricing resolution aborted: auction item stack was empty");
            return abortPricing(context, player, Component.literal("Could not find Auction Item").withStyle(ChatFormatting.RED), cancelOnFailure);
        }

        String itemId = SkyBlockItemUtil.getSkyblockId(itemStack);
        if (itemId == null) {
            MarketGuard.debug("Pricing resolution aborted: no SkyBlock ID found for '{}'", itemStack.getHoverName().getString());
            return abortPricing(
                    context,
                    player,
                    Component.literal("Could not read Skyblock ID for ").append(itemStack.getHoverName()).withStyle(ChatFormatting.RED),
                    cancelOnFailure
            );
        }
        MarketGuard.debug("Resolved SkyBlock item id='{}'", itemId);
        String displayName = SkyBlockItemUtil.getDisplayName(itemStack);

        LowestBinData.LookupResult lookupResult = LowestBinData.lookupLowestBin(itemId);
        AuctionReferencePrice reference = AuctionReferencePrice.select(
                lookupResult.value(),
                lookupResult.average7d(),
                lookupResult.average30d()
        ).orElse(null);
        if (reference == null) {
            MarketGuard.debug(
                    "Pricing resolution skipped: no cached reference price is available for '{}' stale={} loading={} refreshFailed={}",
                    itemId,
                    lookupResult.stale(),
                    lookupResult.loading(),
                    lookupResult.refreshFailed()
            );
            return null;
        }

        // API prices are per unit; the auction price covers the whole stack
        int stackCount = SkyBlockItemUtil.getStackCount(itemStack);
        ReferencePriceBasis basis = MarketGuardConfig.getMarketPriceBasis();
        double referencePrice = (check == Check.PURCHASE ? reference.purchaseReference(basis) : reference.listingReference(basis)) * stackCount;
        if (!reference.safeForProtection()) {
            MarketGuard.debug(
                    "Pricing resolution with low-quality reference itemId='{}' basis={} signals={} spread={}",
                    itemId,
                    basis,
                    reference.signalCount(),
                    reference.relativeSpread()
            );
        }

        if (lookupResult.stale()) {
            MarketGuard.debug("Pricing resolution continues with stale reference price cache for '{}'", itemId);
        }

        MarketGuard.debug(
                "Resolved reference price itemId='{}' check={} basis={} value={} stackCount={} unitMedian={} quality={} signals={} spread={}",
                itemId,
                check,
                basis,
                referencePrice,
                stackCount,
                reference.value(),
                reference.quality(),
                reference.signalCount(),
                reference.relativeSpread()
        );

        try {
            double playerPrice = context.getPlayerPrice();
            MarketGuard.debug("Resolved player price itemId='{}' value={}", itemId, playerPrice);
            return new PricingData(itemId, displayName, referencePrice, playerPrice);
        } catch (Exception e) {
            MarketGuard.debug("Pricing resolution failed while reading player price for '{}' error='{}'", itemId, e.getMessage());
            return abortPricing(
                    context,
                    player,
                    Component.literal("Failed to catch item price: " + e.getMessage()).withStyle(ChatFormatting.RED),
                    cancelOnFailure
            );
        }
    }

    private static PricingData abortPricing(
            AuctionInteractEvent.Context context,
            LocalPlayer player,
            Component message,
            boolean cancelOnFailure
    ) {
        if (cancelOnFailure) {
            context.cancel();
        }

        error(message, player);
        return null;
    }
}
