package eu.tango.scamscreener.marketguard.auction;

import eu.tango.scamscreener.marketguard.MarketGuard;
import eu.tango.scamscreener.marketguard.MarketGuardConfig;
import eu.tango.scamscreener.marketguard.events.AuctionInteractEvent;
import net.minecraft.client.player.LocalPlayer;

import static eu.tango.scamscreener.marketguard.util.MessageBuilder.underbidding;

public final class AuctionUnderbidding {
    public static final int DEFAULT_THRESHOLD = 80;

    private AuctionUnderbidding() {}

    public static int getThreshold() {
        return MarketGuardConfig.getUnderbiddingThreshold();
    }

    public static void setThreshold(int threshold) {
        MarketGuardConfig.setUnderbiddingThreshold(threshold);
    }

    public static boolean isEnabled() {
        return getThreshold() > 0 && getThreshold() < 100;
    }

    public static double getMinimumAllowedPercentage() {
        return getThreshold() / 100.0;
    }

    public static void onInteract(AuctionInteractEvent.Context context) {
        if (!context.isCreateBinClick()) return;
        if (context.getMc() == null || context.getMc().player == null) return;
        if (!isEnabled()) return;

        check(context, context.getMc().player);
    }

    static void check(AuctionInteractEvent.Context context, LocalPlayer player) {
        AuctionPricingResolver.PricingData pricing = AuctionPricingResolver.resolve(context, player, AuctionPricingResolver.Check.LISTING);
        if (pricing == null) return;

        double minimumAllowedPrice = pricing.referencePrice() * getMinimumAllowedPercentage();
        double absoluteDifference = pricing.referencePrice() - pricing.playerPrice();
        MarketGuard.debug(
                "Underbidding check itemId='{}' playerPrice={} referencePrice={} threshold={} minimumAllowedPrice={} absoluteDifference={} absoluteThreshold={}",
                pricing.itemId(),
                pricing.playerPrice(),
                pricing.referencePrice(),
                getThreshold(),
                minimumAllowedPrice,
                absoluteDifference,
                eu.tango.scamscreener.marketguard.MarketGuardConfig.getAbsoluteThreshold()
        );
        if (pricing.playerPrice() < minimumAllowedPrice && AuctionProtectionChecks.exceedsAbsoluteThreshold(absoluteDifference)) {
            double underbidPercent = ((pricing.referencePrice() - pricing.playerPrice()) / pricing.referencePrice()) * 100.0;
            AuctionProtectionChecks.trigger(context, "Underbidding", "underbidPercent", pricing.itemId(), underbidPercent);
            underbidding(
                    pricing.itemId(),
                    pricing.displayName(),
                    underbidPercent,
                    minimumAllowedPrice,
                    context.getRemainingBypassClicks(),
                    player
            );
            return;
        }

        MarketGuard.debug("Underbidding check passed itemId='{}'", pricing.itemId());
    }
}
