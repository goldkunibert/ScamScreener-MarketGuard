package eu.tango.scamscreener.marketguard.util;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class SkyBlockItemUtilTest {

    @Test
    void getSkyblockIdFromCompoundBuildsRuneApiKey() throws Exception {
        CompoundTag runes = new CompoundTag();
        runes.putInt("snow", 1);

        CompoundTag extraAttributes = new CompoundTag();
        extraAttributes.putString("id", "RUNE");
        extraAttributes.put("runes", runes);

        assertEquals("SNOW_RUNE;1", getSkyblockIdFromCompound(extraAttributes));
    }

    @Test
    void getSkyblockIdFromCompoundUsesZeroBasedPetTier() throws Exception {
        assertEquals("ENDERMAN;0", getSkyblockIdFromCompound(petExtraAttributes("ENDERMAN", "COMMON")));
        assertEquals("ENDERMAN;1", getSkyblockIdFromCompound(petExtraAttributes("ENDERMAN", "UNCOMMON")));
        assertEquals("ENDERMAN;4", getSkyblockIdFromCompound(petExtraAttributes("ENDERMAN", "LEGENDARY")));
    }

    @Test
    void getSkyblockIdAppendsLevel100SuffixForMaxLevelPets() {
        CompoundTag nbt = new CompoundTag();
        nbt.put("ExtraAttributes", petExtraAttributes("BEE", "LEGENDARY"));

        assertEquals("BEE;4+100", SkyBlockItemUtil.getSkyblockId(nbt, "[Lvl 100] Bee"));
        assertEquals("BEE;4", SkyBlockItemUtil.getSkyblockId(nbt, "[Lvl 1] Bee"));
        assertEquals("BEE;4", SkyBlockItemUtil.getSkyblockId(nbt, null));
    }

    @Test
    void getDisplayNameUsesThirdTooltipLineForAuctionPlaceholder() {
        assertEquals(
                "Egg Pile",
                SkyBlockItemUtil.resolveDisplayName("AUCTION FOR ITEM:", List.of(
                Component.literal(""),
                Component.literal("Egg Pile"),
                Component.literal("Furniture")
        ))
        );
    }

    @Test
    void nameColourComesFromTheFirstColouredPartOfTheName() {
        Component gold = Component.literal("Fancy Leggings").withStyle(ChatFormatting.GOLD);
        Component nested = Component.empty().append(Component.literal("[Lvl 100] ").withStyle(ChatFormatting.GRAY)).append(Component.literal("Bee").withStyle(ChatFormatting.LIGHT_PURPLE));
        Component plain = Component.literal("Rock");

        assertEquals(TextColor.fromLegacyFormat(ChatFormatting.GOLD), SkyBlockItemUtil.nameColor(gold));
        assertEquals(TextColor.fromLegacyFormat(ChatFormatting.LIGHT_PURPLE), SkyBlockItemUtil.nameColor(nested));
        assertNull(SkyBlockItemUtil.nameColor(plain));
    }

    @Test
    void getDisplayNameReturnsNormalItemNameWhenNotPlaceholder() {
        assertEquals("Fancy Leggings", SkyBlockItemUtil.resolveDisplayName("Fancy Leggings", List.of()));
    }

    @Test
    void parsesBuyItNowPriceFromBinAuctionLore() {
        assertEquals(4_242_911_000D, SkyBlockItemUtil.parsePrice("Buy it now: 4,242,911,000 coins"));
    }

    @Test
    void readsTheStackCountFromTheStackOrTheHypixelNamePrefix() {
        assertEquals(47, SkyBlockItemUtil.stackCount("47x Shiny Orb", 47));
        assertEquals(47, SkyBlockItemUtil.stackCount("47x Shiny Orb", 1));
        assertEquals(64, SkyBlockItemUtil.stackCount("Enchanted Diamond", 64));
        assertEquals(1, SkyBlockItemUtil.stackCount("Shiny Orb", 0));
        assertEquals(1, SkyBlockItemUtil.stackCount("[Lvl 100] Bee", 1));
        assertEquals(1, SkyBlockItemUtil.stackCount(null, 1));
    }

    private static CompoundTag petExtraAttributes(String type, String tier) {
        CompoundTag petInfo = new CompoundTag();
        petInfo.putString("type", type);
        petInfo.putString("tier", tier);

        CompoundTag extraAttributes = new CompoundTag();
        extraAttributes.putString("id", "PET");
        extraAttributes.put("petInfo", petInfo);
        return extraAttributes;
    }

    private static String getSkyblockIdFromCompound(CompoundTag compound) throws Exception {
        Method method = SkyBlockItemUtil.class.getDeclaredMethod("getSkyblockIdFromCompound", CompoundTag.class);
        method.setAccessible(true);
        return (String) method.invoke(null, compound);
    }
}
