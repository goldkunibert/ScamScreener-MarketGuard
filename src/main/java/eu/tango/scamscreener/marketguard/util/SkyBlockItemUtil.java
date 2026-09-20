package eu.tango.scamscreener.marketguard.util;

import eu.tango.scamscreener.marketguard.MarketGuard;
import eu.tango.scamscreener.marketguard.auction.AuctionSlots;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.ItemStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SkyBlockItemUtil {
    private static final Pattern PET_TYPE_PATTERN = Pattern.compile("\"?type\"?\\s*:\\s*\"?([A-Za-z0-9_]+)\"?");
    private static final Pattern PET_TIER_PATTERN = Pattern.compile("\"?tier\"?\\s*:\\s*\"?([A-Za-z_]+)\"?");
    private static final Pattern ITEM_PRICE_PATTERN = Pattern.compile(
            "(?:Item price|Buy it now): (?:[0-9]+|[0-9]{1,3}(?:,[0-9]{3})+) coins",
            Pattern.CASE_INSENSITIVE
    );
    // Hypixel names auction stacks "47x Shiny Orb"; the API prices are per unit (starting_bid / count)
    private static final Pattern STACK_COUNT_PREFIX = Pattern.compile("^(\\d{1,6})x ");
    private static final String AUCTION_FOR_ITEM_PLACEHOLDER = "AUCTION FOR ITEM:";
    private static final String LEVEL_100_PET_PREFIX = "[Lvl 100] ";
    private static final Pattern PET_LEVEL_PREFIX = Pattern.compile("\\[Lvl \\d+]");

    @Nullable
    public static String getSkyblockId(ItemStack itemStack) {
        if (itemStack == null || itemStack.isEmpty()) return null;

        CustomData customData = itemStack.get(DataComponents.CUSTOM_DATA);
        if (customData == null) return null;

        return getSkyblockId(customData.copyTag(), getDisplayName(itemStack));
    }

    @Nullable
    static String getSkyblockId(CompoundTag nbt, @Nullable String displayName) {
        String id = getSkyblockIdFromCompound(nbt.getCompound("minecraft:custom_data").orElse(null));
        if (!isSkyBlockId(id)) id = getSkyblockIdFromCompound(nbt.getCompound("ExtraAttributes").orElse(null));
        if (!isSkyBlockId(id)) id = getSkyblockIdFromCompound(nbt);
        if (!isSkyBlockId(id)) return null;

        // The API keys max-level pets separately, e.g. BEE;4+100
        if (displayName != null && displayName.startsWith(LEVEL_100_PET_PREFIX)) return id + "+100";
        return id;
    }

    /** Units in an auction stack: the stack size or the {@code 47x} name prefix, whichever is larger; at least 1. */
    public static int getStackCount(ItemStack itemStack) {
        if (itemStack == null || itemStack.isEmpty()) {
            return 1;
        }
        return stackCount(itemStack.getHoverName().getString(), itemStack.getCount());
    }

    static int stackCount(@Nullable String displayName, int count) {
        int named = 0;
        if (displayName != null) {
            Matcher matcher = STACK_COUNT_PREFIX.matcher(displayName);
            if (matcher.find()) {
                named = Integer.parseInt(matcher.group(1));
            }
        }
        return Math.max(1, Math.max(count, named));
    }

    public static double getPriceFromNBT(ItemStack item) throws Exception {
        if (item == null || item.isEmpty()) throw new Exception("Item cannot be empty");

        String raw = item.getHoverName().getString();
        MarketGuard.debug("Reading player price from slot item name='{}'", raw);
        Double price = priceFromText(raw);
        if (price != null) {
            return price;
        }

        ItemLore lore = item.get(DataComponents.LORE);
        if (lore != null) {
            for (net.minecraft.network.chat.Component line : lore.lines()) {
                price = priceFromText(line.getString());
                if (price != null) {
                    return price;
                }
            }
        }

        throw new Exception("Cannot read item price: " + raw);
    }

    static double parsePrice(String raw) {
        Double price = priceFromText(raw);
        if (price == null) {
            throw new IllegalArgumentException("Cannot read item price: " + raw);
        }
        return price;
    }

    private static Double priceFromText(String raw) {
        Matcher matcher = ITEM_PRICE_PATTERN.matcher(raw);
        if (!matcher.find()) {
            return null;
        }

        String matchedPrice = matcher.group();
        double parsedPrice = Double.parseDouble(matchedPrice.replaceAll("[^0-9,]", "").replace(",", ""));
        MarketGuard.debug("Parsed player price rawMatch='{}' parsed={}", matchedPrice, parsedPrice);
        return parsedPrice;
    }

    public static String getDisplayName(ItemStack itemStack) {
        if (itemStack == null || itemStack.isEmpty()) {
            return null;
        }

        String displayName = itemStack.getHoverName().getString();
        ItemLore lore = itemStack.get(DataComponents.LORE);
        return resolveDisplayName(displayName, lore == null ? List.of() : lore.lines());
    }

    /** Hypixel colours item names by rarity; null when the name carries no colour. */
    @Nullable
    public static TextColor getNameColor(ItemStack itemStack) {
        if (itemStack == null || itemStack.isEmpty()) {
            return null;
        }

        Component name = itemStack.getHoverName();
        ItemLore lore = itemStack.get(DataComponents.LORE);
        return nameColor(resolveDisplayNameComponent(name, lore == null ? List.of() : lore.lines()));
    }

    @Nullable
    static TextColor nameColor(Component name) {
        // pets carry a grey "[Lvl N]" prefix in front of the rarity-coloured name
        return name.visit(
                (style, text) -> style.getColor() != null && !text.isBlank() && !PET_LEVEL_PREFIX.matcher(text.trim()).matches()
                        ? Optional.of(style.getColor())
                        : Optional.<TextColor>empty(),
                Style.EMPTY
        ).orElse(null);
    }

    static String resolveDisplayName(String displayName, List<Component> loreLines) {
        if (displayName == null) {
            return null;
        }

        Component name = Component.literal(displayName);
        Component resolved = resolveDisplayNameComponent(name, loreLines);
        return resolved == name ? displayName : resolved.getString().trim();
    }

    /** The name itself, or for the {@code AUCTION FOR ITEM:} placeholder the lore line that carries the real name. */
    private static Component resolveDisplayNameComponent(Component name, List<Component> loreLines) {
        if (!AUCTION_FOR_ITEM_PLACEHOLDER.equalsIgnoreCase(name.getString().trim())) {
            return name;
        }

        if (loreLines.size() > 1 && loreLines.getFirst().getString().isBlank()) {
            Component thirdTooltipLine = loreLines.get(1);
            if (!thirdTooltipLine.getString().isBlank()) {
                return thirdTooltipLine;
            }
        }

        for (Component line : loreLines) {
            if (!line.getString().isBlank()) {
                return line;
            }
        }

        return name;
    }

    @Nullable
    private static String getSkyblockIdFromCompound(@Nullable CompoundTag compound) {
        if (compound == null) return null;
        String id = compound.getString("id").orElse(null);
        if (id == null || id.isBlank()) return null;
        if ("PET".equals(id)) {
            String petSkyBlockId = getPetSkyblockId(compound);
            return (petSkyBlockId == null || petSkyBlockId.isBlank()) ? id : petSkyBlockId;
        }
        if ("RUNE".equals(id)) {
            String runeSkyBlockId = getRuneSkyblockId(compound);
            return (runeSkyBlockId == null || runeSkyBlockId.isBlank()) ? id : runeSkyBlockId;
        }
        return id;
    }

    @Nullable
    private static String getRuneSkyblockId(CompoundTag compound) {
        CompoundTag runesCompound = compound.getCompound("runes").orElse(null);
        if (runesCompound == null) return null;

        for (String runeType : runesCompound.keySet()) {
            if (runeType == null || runeType.isBlank()) continue;

            int runeLevel = runesCompound.getInt(runeType).orElse(0);
            if (runeLevel <= 0) continue;

            String normalizedRuneType = runeType.toUpperCase(Locale.ROOT);
            if (!normalizedRuneType.endsWith("_RUNE")) {
                normalizedRuneType += "_RUNE";
            }
            return normalizedRuneType + ";" + runeLevel;
        }

        return null;
    }

    @Nullable
    private static String getPetSkyblockId(CompoundTag compound) {
        String petType = getPetType(compound);
        if (petType == null || petType.isBlank()) return null;

        String petTierName = getPetTierName(compound);
        ItemTier petTier = ItemTier.fromName(petTierName);
        if (petTier == null) return petType;

        return petType + ";" + petTier.getTier();
    }

    @Nullable
    private static String getPetType(CompoundTag compound) {
        CompoundTag petInfoCompound = compound.getCompound("petInfo").orElse(null);
        if (petInfoCompound != null) {
            String type = petInfoCompound.getString("type").orElse(null);
            if (type != null && !type.isBlank()) return type.toUpperCase(Locale.ROOT);
        }

        String petInfoRaw = compound.getString("petInfo").orElse(null);
        if (petInfoRaw == null || petInfoRaw.isBlank()) return null;

        Matcher matcher = PET_TYPE_PATTERN.matcher(petInfoRaw);
        if (!matcher.find()) return null;

        String type = matcher.group(1);
        if (type == null || type.isBlank()) return null;
        return type.toUpperCase(Locale.ROOT);
    }

    @Nullable
    private static String getPetTierName(CompoundTag compound) {
        CompoundTag petInfoCompound = compound.getCompound("petInfo").orElse(null);
        if (petInfoCompound != null) {
            String tier = petInfoCompound.getString("tier").orElse(null);
            if (tier != null && !tier.isBlank()) return tier;
        }

        String petInfoRaw = compound.getString("petInfo").orElse(null);
        if (petInfoRaw == null || petInfoRaw.isBlank()) return null;

        Matcher matcher = PET_TIER_PATTERN.matcher(petInfoRaw);
        if (!matcher.find()) return null;

        String tier = matcher.group(1);
        if (tier == null || tier.isBlank()) return null;
        return tier;
    }

    private static boolean isSkyBlockId(@Nullable String id) {
        return id != null && !id.isBlank() && !id.contains(":");
    }

}
