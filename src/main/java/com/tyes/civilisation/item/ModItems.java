package com.tyes.civilisation.item;

import com.tyes.civilisation.CivilisationMod;
import com.tyes.civilisation.entity.ModEntities;
import com.tyes.civilisation.block.ModBlocks;
import net.minecraft.world.item.BlockItem;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(BuiltInRegistries.ITEM, CivilisationMod.MOD_ID);
    public static final DeferredHolder<Item, Item> CIVILIAN_SPAWN_EGG = ITEMS.register("civilian_spawn_egg",
            () -> new DeferredSpawnEggItem(ModEntities.CIVILIAN, 0x8B6A45, 0xD8B88A, new Item.Properties()));
    public static final DeferredHolder<Item, Item> BUILDER_HUT_FLOOR = ITEMS.register("builder_hut_floor",
            () -> new BlockItem(ModBlocks.BUILDER_HUT_FLOOR.get(), new Item.Properties()));

    private ModItems() {}
}
