package com.tyes.civilisation.economy.item;

import java.util.Objects;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** Stable item identity. Component hash is retained when exact stack identity matters. */
public record ItemKey(ResourceLocation itemId, int componentHash, boolean componentSensitive) {
    public ItemKey {
        Objects.requireNonNull(itemId, "itemId");
    }

    public static ItemKey bulk(ItemStack stack) {
        return new ItemKey(BuiltInRegistries.ITEM.getKey(stack.getItem()), 0, false);
    }

    public static ItemKey exact(ItemStack stack) {
        return new ItemKey(BuiltInRegistries.ITEM.getKey(stack.getItem()), stack.getComponentsPatch().hashCode(), true);
    }

    public static ItemKey parse(String id) {
        ResourceLocation location = ResourceLocation.tryParse(id);
        if (location == null || !BuiltInRegistries.ITEM.containsKey(location)) {
            throw new IllegalArgumentException("Unknown item " + id);
        }
        return new ItemKey(location, 0, false);
    }

    public boolean matches(ItemStack stack) {
        if (stack.isEmpty() || !BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(itemId)) return false;
        return !componentSensitive || stack.getComponentsPatch().hashCode() == componentHash;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("item", itemId.toString());
        tag.putInt("components", componentHash);
        tag.putBoolean("componentSensitive", componentSensitive);
        return tag;
    }

    public static ItemKey load(CompoundTag tag) {
        ResourceLocation id = ResourceLocation.tryParse(tag.getString("item"));
        if (id == null) throw new IllegalArgumentException("Invalid persisted item key");
        return new ItemKey(id, tag.getInt("components"), tag.getBoolean("componentSensitive"));
    }

    @Override public String toString() {
        return itemId + (componentSensitive ? "#" + Integer.toUnsignedString(componentHash, 16) : "");
    }
}
