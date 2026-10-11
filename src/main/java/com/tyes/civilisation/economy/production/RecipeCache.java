package com.tyes.civilisation.economy.production;

import com.tyes.civilisation.economy.item.ItemKey;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;

/** Output-indexed recipe cache. It rebuilds when the RecipeManager instance/content changes. */
public final class RecipeCache {
    private final MinecraftServer server;
    private final Map<ItemKey, List<RecipeHolder<?>>> byOutput = new LinkedHashMap<>();
    private int knownRecipeCount = -1;

    public RecipeCache(MinecraftServer server) { this.server = server; }

    public synchronized List<RecipeHolder<?>> candidates(ItemKey output) {
        refreshIfNeeded();
        return byOutput.getOrDefault(new ItemKey(output.itemId(), 0, false), List.of());
    }

    public synchronized void invalidate() { knownRecipeCount = -1; byOutput.clear(); }

    private void refreshIfNeeded() {
        var recipes = server.getRecipeManager().getRecipes();
        if (recipes.size() == knownRecipeCount) return;
        byOutput.clear();
        for (RecipeHolder<?> holder : recipes) {
            ItemStack output = holder.value().getResultItem(server.registryAccess());
            if (output.isEmpty()) continue;
            byOutput.computeIfAbsent(ItemKey.bulk(output), ignored -> new ArrayList<>()).add(holder);
        }
        byOutput.replaceAll((key, value) -> List.copyOf(value));
        knownRecipeCount = recipes.size();
    }
}
