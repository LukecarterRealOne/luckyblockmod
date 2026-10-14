package com.lucky.mod;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public class LuckyBlockMod implements ModInitializer {
    public static final String MOD_ID = "luckyblockmod";

    public static final Item LUCKY_BLOCK = Registry.register(
            Registries.ITEM,
            Identifier.of(MOD_ID, "lucky_block"),
            new LuckyBlockItem(new Item.Settings().maxCount(1)));

    @Override
    public void onInitialize() {
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.TOOLS).register(e -> e.add(LUCKY_BLOCK));
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.COMBAT).register(e -> e.add(LUCKY_BLOCK));
    }
}
