package com.tyes.civilisation.economy.inventory;

import com.tyes.civilisation.economy.item.ItemKey;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

/** Small transaction primitives that always operate on actual container stacks. */
public final class PhysicalInventoryOps {
    private PhysicalInventoryOps() { }

    public static Map<ItemKey, Integer> snapshot(Container container) {
        Map<ItemKey, Integer> result = new LinkedHashMap<>();
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            ItemStack stack = container.getItem(slot);
            if (!stack.isEmpty()) result.merge(ItemKey.bulk(stack), stack.getCount(), Integer::sum);
        }
        return Map.copyOf(result);
    }

    public static int count(Container container, ItemKey item) {
        int count = 0;
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            ItemStack stack = container.getItem(slot); if (item.matches(stack)) count += stack.getCount();
        }
        return count;
    }

    public static int capacity(Container container, ItemStack stack) {
        int capacity = 0;
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            if (!container.canPlaceItem(slot, stack)) continue;
            ItemStack current = container.getItem(slot);
            if (current.isEmpty()) capacity += Math.min(container.getMaxStackSize(), stack.getMaxStackSize());
            else if (ItemStack.isSameItemSameComponents(current, stack)) capacity += Math.max(0,
                    Math.min(container.getMaxStackSize(), stack.getMaxStackSize()) - current.getCount());
        }
        return capacity;
    }

    public static ItemStack extract(Container container, ItemKey item, int amount) {
        if (amount <= 0) return ItemStack.EMPTY;
        ItemStack extracted = ItemStack.EMPTY;
        for (int slot = 0; slot < container.getContainerSize() && amount > 0; slot++) {
            ItemStack current = container.getItem(slot);
            if (!item.matches(current)) continue;
            int moved = Math.min(amount, current.getCount());
            if (extracted.isEmpty()) extracted = current.copyWithCount(moved);
            else {
                if (!ItemStack.isSameItemSameComponents(extracted, current)) continue;
                extracted.grow(moved);
            }
            current.shrink(moved); amount -= moved;
            if (current.isEmpty()) container.setItem(slot, ItemStack.EMPTY);
        }
        if (!extracted.isEmpty()) container.setChanged();
        return extracted;
    }

    /** Mutates incoming; returns inserted count. */
    public static int insert(Container container, ItemStack incoming) {
        int before = incoming.getCount();
        for (int slot = 0; slot < container.getContainerSize() && !incoming.isEmpty(); slot++) {
            if (!container.canPlaceItem(slot, incoming)) continue;
            ItemStack current = container.getItem(slot);
            if (!current.isEmpty() && !ItemStack.isSameItemSameComponents(current, incoming)) continue;
            int free = Math.min(container.getMaxStackSize(), incoming.getMaxStackSize()) - current.getCount();
            int moved = Math.min(free, incoming.getCount());
            if (moved <= 0) continue;
            if (current.isEmpty()) container.setItem(slot, incoming.copyWithCount(moved)); else current.grow(moved);
            incoming.shrink(moved);
        }
        int inserted = before - incoming.getCount(); if (inserted > 0) container.setChanged(); return inserted;
    }
}
