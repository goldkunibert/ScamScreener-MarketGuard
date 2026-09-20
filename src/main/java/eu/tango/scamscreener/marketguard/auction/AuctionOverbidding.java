package eu.tango.scamscreener.marketguard.auction;

import eu.tango.scamscreener.marketguard.MarketGuard;
import eu.tango.scamscreener.marketguard.MarketGuardConfig;
import eu.tango.scamscreener.marketguard.data.LowestBinData;
import eu.tango.scamscreener.marketguard.events.AuctionInteractEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

import static eu.tango.scamscreener.marketguard.util.MessageBuilder.overbidding;

public final class AuctionOverbidding {
    public static final int DEFAULT_THRESHOLD = 120;

    private AuctionOverbidding() {}

    public static int getThreshold() {
        return MarketGuardConfig.getOverbiddingThreshold();
    }

    public static void setThreshold(int threshold) {
        MarketGuardConfig.setOverbiddingThreshold(threshold);
    }

    public static boolean isEnabled() {
        return getThreshold() > 100;
    }

    public static double getMaximumAllowedPercentage() {
        return getThreshold() / 100.0;
    }

    public static void onInteract(AuctionInteractEvent.Context context) {
        if (!context.isBinView()) return;

        String itemId = context.getAuctionItemId();
        if (itemId != null) {
            LowestBinData.checkBlacklistedAuctioneerAsyncIfNeeded(itemId);
        }

        Minecraft mc = context.getMc();
        if (mc == null || mc.player == null) return;
        if (!isEnabled()) return;

        check(context, mc.player);
    }

    static void check(AuctionInteractEvent.Context context, LocalPlayer player) {
        AuctionPricingResolver.PricingData pricing = AuctionPricingResolver.resolve(context, player, AuctionPricingResolver.Check.PURCHASE);
        if (pricing == null) return;

        double maximumAllowedPrice = pricing.referencePrice() * getMaximumAllowedPercentage();
        double absoluteDifference = pricing.playerPrice() - pricing.referencePrice();
        MarketGuard.debug(
                "Overbidding check itemId='{}' playerPrice={} referencePrice={} threshold={} maximumAllowedPrice={} absoluteDifference={} absoluteThreshold={}",
                pricing.itemId(),
                pricing.playerPrice(),
                pricing.referencePrice(),
                getThreshold(),
                maximumAllowedPrice,
                absoluteDifference,
                eu.tango.scamscreener.marketguard.MarketGuardConfig.getAbsoluteThreshold()
        );
        if (pricing.playerPrice() > maximumAllowedPrice && AuctionProtectionChecks.exceedsAbsoluteThreshold(absoluteDifference)) {
            double overbidPercent = ((pricing.playerPrice() - pricing.referencePrice()) / pricing.referencePrice()) * 100.0;
            AuctionProtectionChecks.trigger(context, "Overbidding", "overbidPercent", pricing.itemId(), overbidPercent);
            overbidding(
                    pricing.itemId(),
                    pricing.displayName(),
                    overbidPercent,
                    maximumAllowedPrice,
                    context.getRemainingBypassClicks(),
                    player
            );
            return;
        }

        MarketGuard.debug("Overbidding check passed itemId='{}'", pricing.itemId());
    }
}
