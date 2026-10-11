package com.tyes.civilisation.economy.production;

import net.minecraft.world.item.crafting.RecipeHolder;

/** Phase 3 hook. Common non-special datapack recipes are currently known. */
public final class RecipeKnowledgeService {
    public boolean knows(RecipeHolder<?> recipe) {
        return !recipe.value().isSpecial();
    }
}
