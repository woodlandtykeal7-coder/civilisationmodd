package com.tyes.civilisation.citizen;

import java.util.OptionalInt;
import java.util.Optional;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.nbt.ListTag;
import javax.annotation.Nullable;
import net.minecraft.world.Container;
import com.tyes.civilisation.economy.item.ItemKey;
import com.tyes.civilisation.economy.inventory.PhysicalInventoryOps;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.tags.ItemTags;

public final class CitizenInventory {
    public static final int SIZE = 9;
    @Nullable private NonNullList<ItemStack> items;

    private NonNullList<ItemStack> items() {
        if (items == null) items = NonNullList.withSize(SIZE, ItemStack.EMPTY);
        return items;
    }

    public OptionalInt findFood() {
        if (items == null) return OptionalInt.empty();
        for (int i = 0; i < items.size(); i++) {
            if (!items.get(i).isEmpty() && items.get(i).has(DataComponents.FOOD)) return OptionalInt.of(i);
        }
        return OptionalInt.empty();
    }

    public ItemStack get(int slot) { return items().get(slot); }

    public boolean add(ItemStack incoming) {
        if (incoming.isEmpty()) return true;
        NonNullList<ItemStack> contents = items();
        for (ItemStack stack : contents) {
            if (!stack.isEmpty() && ItemStack.isSameItemSameComponents(stack, incoming)
                    && stack.getCount() < stack.getMaxStackSize()) {
                int moved = Math.min(incoming.getCount(), stack.getMaxStackSize() - stack.getCount());
                stack.grow(moved);
                incoming.shrink(moved);
                if (incoming.isEmpty()) return true;
            }
        }
        for (int i = 0; i < contents.size(); i++) {
            if (contents.get(i).isEmpty()) {
                contents.set(i, incoming.copyAndClear());
                return true;
            }
        }
        return false;
    }

    public ItemStack consumeOne(int slot) {
        return ContainerHelper.removeItem(items(), slot, 1);
    }

    public boolean isEmpty() {
        return items == null || items.stream().allMatch(ItemStack::isEmpty);
    }

    public int count(ItemKey key) {
        if (items == null) return 0;
        return items.stream().filter(key::matches).mapToInt(ItemStack::getCount).sum();
    }

    public Optional<ItemStack> first(ItemKey key) {
        if (items == null) return Optional.empty();
        return items.stream().filter(key::matches).findFirst().map(ItemStack::copy);
    }

    public ItemStack takeOne(ItemKey key) {
        if (items == null) return ItemStack.EMPTY;
        for (int i = 0; i < items.size(); i++) if (key.matches(items.get(i))) return consumeOne(i);
        return ItemStack.EMPTY;
    }

    public boolean has(TagKey<Item> tag) { return items != null && items.stream().anyMatch(stack -> stack.is(tag)); }

    /** Damages the actual carried tool stack and returns true when a matching tool was available. */
    public boolean damageFirst(TagKey<Item> tag, int amount, LivingEntity owner) {
        if (items == null) return false;
        for (int i = 0; i < items.size(); i++) {
            ItemStack stack = items.get(i);
            if (!stack.is(tag)) continue;
            stack.hurtAndBreak(amount, owner, EquipmentSlot.MAINHAND);
            if (stack.isEmpty()) items.set(i, ItemStack.EMPTY);
            return true;
        }
        return false;
    }

    public int capacityFor(ItemStack incoming) {
        if (incoming.isEmpty()) return 0;
        int capacity = 0;
        for (ItemStack current : items()) {
            if (current.isEmpty()) capacity += incoming.getMaxStackSize();
            else if (ItemStack.isSameItemSameComponents(current, incoming)) capacity += Math.max(0,
                    incoming.getMaxStackSize() - current.getCount());
        }
        return capacity;
    }

    /** Transfers only the requested cargo; unrelated personal/tool items remain with the citizen. */
    public int transferMatchingTo(Container destination, ItemKey key, int maximum) {
        if (items == null || maximum <= 0) return 0;
        int moved = 0;
        for (int slot = 0; slot < items.size() && moved < maximum; slot++) {
            ItemStack stack = items.get(slot);
            if (!key.matches(stack)) continue;
            ItemStack cargo = stack.copyWithCount(Math.min(stack.getCount(), maximum - moved));
            int inserted = PhysicalInventoryOps.insert(destination, cargo);
            stack.shrink(inserted); moved += inserted;
            if (stack.isEmpty()) items.set(slot, ItemStack.EMPTY);
            if (!cargo.isEmpty()) break;
        }
        return moved;
    }

    public void dropAll(ServerLevel level, Vec3 position) {
        if (items == null) return;
        for (int slot = 0; slot < items.size(); slot++) {
            ItemStack stack = items.get(slot);
            if (!stack.isEmpty()) level.addFreshEntity(new ItemEntity(level, position.x, position.y, position.z, stack.copy()));
            items.set(slot, ItemStack.EMPTY);
        }
    }

    /** Moves as much as possible into a physical container; leftovers remain on the citizen. */
    public int transferTo(Container destination) {
        return transferTo(destination, stack -> true);
    }

    /** Deposits gathered cargo while retaining personally carried durable tools. */
    public int transferResourcesTo(Container destination) {
        return transferTo(destination, stack -> !stack.is(ItemTags.AXES) && !stack.is(ItemTags.PICKAXES)
                && !stack.is(ItemTags.HOES) && !stack.is(ItemTags.SHOVELS));
    }

    private int transferTo(Container destination, java.util.function.Predicate<ItemStack> allowed) {
        if (items == null) return 0;
        int movedTotal = 0;
        for (int sourceSlot = 0; sourceSlot < items.size(); sourceSlot++) {
            ItemStack source = items.get(sourceSlot);
            if (source.isEmpty() || !allowed.test(source)) continue;
            for (int targetSlot = 0; targetSlot < destination.getContainerSize() && !source.isEmpty(); targetSlot++) {
                if (!destination.canPlaceItem(targetSlot, source)) continue;
                ItemStack target = destination.getItem(targetSlot);
                if (!target.isEmpty() && !ItemStack.isSameItemSameComponents(target, source)) continue;
                int capacity = Math.min(destination.getMaxStackSize(), source.getMaxStackSize())
                        - (target.isEmpty() ? 0 : target.getCount());
                if (capacity <= 0) continue;
                int moved = Math.min(capacity, source.getCount());
                if (target.isEmpty()) destination.setItem(targetSlot, source.copyWithCount(moved));
                else target.grow(moved);
                source.shrink(moved);
                movedTotal += moved;
            }
            if (source.isEmpty()) items.set(sourceSlot, ItemStack.EMPTY);
        }
        if (movedTotal > 0) destination.setChanged();
        return movedTotal;
    }

    public CompoundTag save(HolderLookup.Provider registries) {
        if (items == null) {
            CompoundTag tag = new CompoundTag();
            tag.put("Items", new ListTag());
            return tag;
        }
        return ContainerHelper.saveAllItems(new CompoundTag(), items, registries);
    }

    public static CitizenInventory load(CompoundTag tag, HolderLookup.Provider registries) {
        CitizenInventory result = new CitizenInventory();
        if (!tag.getList("Items", 10).isEmpty()) ContainerHelper.loadAllItems(tag, result.items(), registries);
        return result;
    }

    public String summary() {
        int occupied = 0;
        int food = 0;
        if (items == null) return "0/" + SIZE + " slots, 0 food item(s)";
        for (ItemStack stack : items) {
            if (!stack.isEmpty()) occupied++;
            if (!stack.isEmpty() && stack.has(DataComponents.FOOD)) food += stack.getCount();
        }
        return occupied + "/" + SIZE + " slots, " + food + " food item(s)";
    }
}
