package eu.tango.scamscreener.marketguard.hud;

import eu.tango.scamscreener.marketguard.MarketGuard;
import eu.tango.scamscreener.marketguard.MarketGuardConfig;
import eu.tango.scamscreener.marketguard.auction.AuctionOverbidding;
import eu.tango.scamscreener.marketguard.auction.AuctionReferencePrice;
import eu.tango.scamscreener.marketguard.auction.AuctionUnderbidding;
import eu.tango.scamscreener.marketguard.auction.ReferencePriceBasis;
import eu.tango.scamscreener.marketguard.data.BazaarData;
import eu.tango.scamscreener.marketguard.data.LowestBinData;
import eu.tango.scamscreener.marketguard.data.MarketRiskEvaluator;
import eu.tango.scamscreener.marketguard.util.CoinFormat;
import eu.tango.tangosHudLib.api.HudContent;
import eu.tango.tangosHudLib.api.HudLibrary;
import eu.tango.tangosHudLib.api.HudWidget;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextColor;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public final class AuctionPriceHud {
    private static final double FAIR_ABOVE_MARKET = 0.05;
    private static final long LOWEST_BIN_LOOKUP_INTERVAL_MS = 1_000L;

    private static volatile View view = View.hidden();
    private static volatile long lastLowestBinLookupAt;

    private AuctionPriceHud() {}

    public static void initialize() {
        HudLibrary.registerWidgets(MarketGuard.MOD_ID, Widgets.class);
    }

    public static void update(String itemId, String displayName, @Nullable TextColor nameColor, double auctionPrice, int stackCount) {
        view = new View(itemId, displayName, nameColor, auctionPrice, stackCount, null);
        lastLowestBinLookupAt = 0L;
    }

    public static void clear() {
        view = View.hidden();
        lastLowestBinLookupAt = 0L;
    }

    private static void refreshLowestBinIfDue() {
        View current = view;
        if (!current.visible() || System.currentTimeMillis() - lastLowestBinLookupAt < LOWEST_BIN_LOOKUP_INTERVAL_MS) {
            return;
        }

        lastLowestBinLookupAt = System.currentTimeMillis();
        LowestBinData.refreshAsyncIfNeeded();
        BazaarData.refreshAsyncIfNeeded();
        LowestBinData.LookupResult lowestBin = LowestBinData.lookupLowestBin(current.itemId());
        if (!lowestBin.equals(current.lowestBin())) {
            view = new View(current.itemId(), current.displayName(), current.nameColor(), current.auctionPrice(), current.stackCount(), lowestBin);
        }
    }

    static HudContent content(View current) {
        if (!current.visible()) {
            return HudContent.builder()
                    .line(Component.literal("Auction Price"))
                    .visible(false)
                    .build();
        }

        Map<String, Component> lines = new LinkedHashMap<>();
        // Hypixel colours item names by rarity; keep it so the HUD reads like the tooltip
        TextColor nameColor = current.nameColor() != null ? current.nameColor() : TextColor.fromLegacyFormat(ChatFormatting.WHITE);
        lines.put("item", Component.literal(current.displayName()).withStyle(style -> style.withColor(nameColor)));
        MutableComponent auction = labelled("Auction: ", coins(current.auctionPrice()), ChatFormatting.GOLD);
        appendUnitPrice(auction, current.auctionPrice(), current.stackCount());
        lines.put("auction", auction);

        LowestBinData.LookupResult lowestBin = current.lowestBin();
        if (lowestBin == null) {
            lines.put("lowest_bin", Component.literal("Loading market price...").withStyle(ChatFormatting.GRAY));
            return build(lines);
        }

        Optional<AuctionReferencePrice> selected = AuctionReferencePrice.select(
                lowestBin.value(),
                lowestBin.average7d(),
                lowestBin.average30d()
        );
        if (selected.isEmpty()) {
            String status;
            if (lowestBin.loading()) {
                status = "Loading market price...";
            } else if (lowestBin.refreshFailed()) {
                status = "Market price unavailable";
            } else {
                status = "No market price for this item";
            }
            lines.put("lowest_bin", Component.literal(status).withStyle(ChatFormatting.GRAY));
        } else {
            AuctionReferencePrice reference = selected.orElseThrow();
            ReferencePriceBasis basis = MarketGuardConfig.getMarketPriceBasis();
            // the same price the overbidding warning compares against, scaled to the stack (API prices are per unit)
            int stackCount = current.stackCount();
            double marketPrice = reference.purchaseReference(basis) * stackCount;
            boolean showsLowestBin = basis == ReferencePriceBasis.LOWEST_BIN && reference.lowestBin() != null;
            // a Lowest BIN is a live listing, so it is never "roughly"; the median and the market estimate are
            boolean reliable = showsLowestBin || reference.safeForProtection();
            String marketName = showsLowestBin ? "Lowest BIN" : basis == ReferencePriceBasis.MEDIAN || basis == ReferencePriceBasis.LOWEST_BIN ? "median" : "market";
            MutableComponent market;
            if (showsLowestBin && lowestBin.lowestBinCount() > 1) {
                // the cheapest per-unit listing is a stack: name it, so the per-unit price is traceable
                double listingPrice = reference.lowestBin() * lowestBin.lowestBinCount();
                market = labelled("Lowest BIN: ", lowestBin.lowestBinCount() + "x for " + coins(listingPrice), ChatFormatting.WHITE);
                appendUnitPrice(market, listingPrice, lowestBin.lowestBinCount());
            } else {
                market = showsLowestBin
                        ? labelled("Lowest BIN: ", coins(marketPrice), ChatFormatting.WHITE)
                        : labelled(basis == ReferencePriceBasis.FAVOURABLE ? "Market: " : "Median: ", "~" + coins(marketPrice), ChatFormatting.WHITE);
                appendUnitPrice(market, marketPrice, stackCount);
            }
            if (!reference.safeForProtection()) {
                market.append(Component.literal(" (" + uncertaintyNote(reference, lowestBin, showsLowestBin, stackCount) + ")").withStyle(ChatFormatting.YELLOW));
            }
            lines.put("lowest_bin", market);

            double difference = current.auctionPrice() - marketPrice;
            double differencePercentage = difference / marketPrice;
            boolean cheapest = lowestBin.value() != null && current.auctionPrice() <= lowestBin.value() * stackCount;
            MutableComponent verdict = verdict(differencePercentage, reliable, cheapest, marketName, showsLowestBin);
            if (difference != 0.0) {
                verdict.append(Component.literal(" (" + signedCoins(difference) + ")").withStyle(ChatFormatting.GRAY));
            }
            lines.put("advice", verdict);
        }

        addRiskLines(lines, current.itemId(), lowestBin);
        if (lowestBin.stale()) {
            lines.put("stale", Component.literal("Prices may be outdated").withStyle(ChatFormatting.YELLOW));
        }
        return build(lines);
    }

    /** Why the shown price is uncertain: hardly any sales data, or the other signals disagree with it by more than a third. */
    private static String uncertaintyNote(AuctionReferencePrice reference, LowestBinData.LookupResult lowestBin, boolean showsLowestBin, int stackCount) {
        if (reference.signalCount() < 2) {
            return "few sales";
        }
        if (showsLowestBin) {
            Double average = lowestBin.average7d() != null ? lowestBin.average7d() : lowestBin.average30d();
            return average != null ? "avg " + coins(average * stackCount) : "volatile";
        }
        return reference.lowestBin() != null ? "BIN " + coins(reference.lowestBin() * stackCount) : "volatile";
    }

    private static MutableComponent verdict(double differencePercentage, boolean reliable, boolean cheapest, String market, boolean lowestBinBasis) {
        int overThreshold = AuctionOverbidding.isEnabled() ? AuctionOverbidding.getThreshold() : AuctionOverbidding.DEFAULT_THRESHOLD;
        int underThreshold = AuctionUnderbidding.isEnabled() ? AuctionUnderbidding.getThreshold() : AuctionUnderbidding.DEFAULT_THRESHOLD;
        double over = overThreshold / 100.0 - 1.0;
        double under = 1.0 - underThreshold / 100.0;
        String percent = percentage(Math.abs(differencePercentage));
        if (cheapest) {
            if (reliable && differencePercentage >= over) {
                return Component.literal("Cheapest BIN, but " + percent + " above " + market + " - overpriced").withStyle(ChatFormatting.RED);
            }
            // against the Lowest BIN itself the auction is the reference, so there is nothing to compare it with
            String comparison = lowestBinBasis || Math.abs(differencePercentage) < FAIR_ABOVE_MARKET ? ""
                    : ", " + percent + (differencePercentage < 0.0 ? " below " : " above ") + market;
            return Component.literal("Cheapest BIN right now" + comparison).withStyle(ChatFormatting.GREEN);
        }
        if (differencePercentage <= -under) {
            return reliable
                    ? Component.literal("Good deal: " + percent + " below " + market).withStyle(ChatFormatting.GREEN)
                    : Component.literal("Roughly " + percent + " below " + market).withStyle(ChatFormatting.YELLOW);
        }
        if (differencePercentage < Math.min(FAIR_ABOVE_MARKET, over)) {
            return Component.literal("Fair price").withStyle(ChatFormatting.GREEN);
        }
        if (!reliable) {
            return Component.literal("Roughly " + percent + " above " + market).withStyle(ChatFormatting.YELLOW);
        }
        if (differencePercentage >= over) {
            return Component.literal(percent + " above " + market + " - overpriced").withStyle(ChatFormatting.RED);
        }
        return Component.literal(percent + " above " + market).withStyle(ChatFormatting.YELLOW);
    }

    private static void addRiskLines(Map<String, Component> lines, String itemId, LowestBinData.LookupResult lowestBin) {
        MarketRiskEvaluator.Warning volatility = MarketRiskEvaluator.auctionVolatility(
                lowestBin.average7d(),
                lowestBin.average30d()
        );
        if (volatility != null && volatility.highRisk()) {
            lines.put("volatility", Component.literal(volatility.text()).withStyle(ChatFormatting.YELLOW));
        }

        BazaarData.LookupResult bazaar = BazaarData.lookupProduct(itemId);
        MarketRiskEvaluator.Warning liquidity = MarketRiskEvaluator.bazaarLiquidity(bazaar.value());
        if (liquidity != null && liquidity.highRisk()) {
            String text = liquidity.text() + (bazaar.stale() ? " (Bazaar data may be outdated)" : "");
            lines.put("liquidity", Component.literal(text).withStyle(ChatFormatting.YELLOW));
        }
    }

    private static HudContent build(Map<String, Component> lines) {
        HudContent.Builder content = HudContent.builder();
        boolean added = false;
        for (String id : HudCustomization.rows(HudCustomization.HudId.AUCTION_PRICE)) {
            Component line = lines.get(id);
            if (line != null) { content.line(line); added = true; }
        }
        return added ? content.build() : HudContent.builder().line(Component.literal("Auction Price")).visible(false).build();
    }

    /** Stacks are priced as a whole; the per-item price makes a stack comparable with single listings. */
    private static void appendUnitPrice(MutableComponent line, double stackPrice, int stackCount) {
        if (stackCount > 1) {
            line.append(Component.literal(" (" + CoinFormat.format(stackPrice / stackCount) + " each)").withStyle(ChatFormatting.GRAY));
        }
    }

    private static MutableComponent labelled(String label, String value, ChatFormatting valueColor) {
        return Component.literal(label).withStyle(ChatFormatting.GRAY).append(Component.literal(value).withStyle(valueColor));
    }

    private static String coins(double value) {
        return CoinFormat.coins(value);
    }

    private static String signedCoins(double value) {
        return (value > 0.0 ? "+" : value < 0.0 ? "-" : "") + coins(Math.abs(value));
    }

    private static String percentage(double value) {
        return Math.round(value * 100.0) + "%";
    }

    record View(
            String itemId,
            String displayName,
            @Nullable TextColor nameColor,
            double auctionPrice,
            int stackCount,
            LowestBinData.LookupResult lowestBin
    ) {
        static View hidden() {
            return new View(null, null, null, 0.0, 1, null);
        }

        boolean visible() {
            return itemId != null && !itemId.isBlank() && displayName != null && !displayName.isBlank() && auctionPrice > 0.0;
        }
    }

    public static final class Widgets {
        private Widgets() {}

        @HudWidget(id = "auction_price")
        public static HudContent auctionPrice() {
            refreshLowestBinIfDue();
            if (!view.visible()) {
                return content(view);
            }
            if (!HudCustomization.visibleOnCurrentScreen(HudCustomization.HudId.AUCTION_PRICE)) {
                return HudContent.builder().line(Component.literal("Auction Price")).visible(false).build();
            }
            return content(view);
        }
    }
}
