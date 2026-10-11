package com.tyes.civilisation.economy.tool;

import java.util.OptionalInt;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import com.tyes.civilisation.citizen.CitizenInventory;

/** Stateless tool policy hook. Vanilla recipes provide replacement/repair; no synthetic repair is used. */
public final class ToolService {
    private ToolService() { }
    public static boolean usable(ItemStack stack, TagKey<Item> required) {
        return stack.is(required) && ToolCondition.of(stack) != ToolCondition.BROKEN;
    }
    public static boolean hasUsable(CitizenInventory inventory, TagKey<Item> required) {
        return inventory.has(required);
    }
}
