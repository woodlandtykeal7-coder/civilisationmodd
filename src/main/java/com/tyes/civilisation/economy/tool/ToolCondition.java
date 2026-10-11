package com.tyes.civilisation.economy.tool;

import net.minecraft.world.item.ItemStack;

/** Planning classification only; ItemStack damage remains authoritative. */
public enum ToolCondition {
    BROKEN, POOR, WORN, SERVICEABLE, GOOD;
    public static ToolCondition of(ItemStack stack) {
        if (stack.isEmpty()) return BROKEN;
        if (!stack.isDamageableItem()) return GOOD;
        int remaining = stack.getMaxDamage() - stack.getDamageValue();
        if (remaining <= 0) return BROKEN;
        double ratio = (double) remaining / stack.getMaxDamage();
        if (ratio < .15D) return POOR;
        if (ratio < .40D) return WORN;
        if (ratio < .75D) return SERVICEABLE;
        return GOOD;
    }
}
