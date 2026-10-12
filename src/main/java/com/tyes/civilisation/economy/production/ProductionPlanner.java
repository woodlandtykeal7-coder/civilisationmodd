package com.tyes.civilisation.economy.production;

import com.tyes.civilisation.economy.SettlementEconomyState;
import com.tyes.civilisation.economy.inventory.InventoryEndpoint;
import com.tyes.civilisation.economy.item.ItemKey;
import com.tyes.civilisation.economy.workshop.WorkshopType;
import com.tyes.civilisation.persistence.EconomySavedData;
import com.tyes.civilisation.work.SettlementResourceIndex;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;

/** Bounded physical dependency planner. It creates requests, never item ownership. */
public final class ProductionPlanner {
    private static final int MAX_DEPTH = 8;
    private final MinecraftServer server;
    private final EconomySavedData data;
    private final SettlementResourceIndex index;
    private final RecipeCache recipes;
    private final RecipeKnowledgeService knowledge = new RecipeKnowledgeService();

    public ProductionPlanner(MinecraftServer server, EconomySavedData data, SettlementResourceIndex index, RecipeCache recipes) {
        this.server = server; this.data = data; this.index = index; this.recipes = recipes;
    }

    public void plan(ProductionRequest request) { plan(request, 0, new HashSet<>()); }

    /**
     * Older saves may contain a multi-batch crafting request stuck waiting for
     * all ingredients. Shrink its next batch only while idle; never disrupt a
     * worker who has already claimed the production request.
     */
    public boolean replanOversizedCraftBatch(ProductionRequest request) {
        if (request.method() != ProductionMethod.CRAFT || request.claimedBy().isPresent()
                || (request.state() != ProductionState.WAITING_FOR_DELIVERY
                    && request.state() != ProductionState.READY)) return false;
        var recipeId = request.recipeId().orElse(null);
        if (recipeId == null) return false;
        RecipeHolder<?> recipe = server.getRecipeManager().byKey(recipeId).orElse(null);
        if (recipe == null) return false;
        if (!ProductionBatching.hasOversizedCraftInputs(request, ingredientNeeds(request.settlementId(), recipe, 1)))
            return false;
        plan(request);
        return true;
    }

    private void plan(ProductionRequest request, int depth, Set<ItemKey> visiting) {
        if (request.state() == ProductionState.COMPLETE || request.state() == ProductionState.CANCELLED) return;
        if (depth > MAX_DEPTH || !visiting.add(request.output())) { request.block(ProductionBlocker.DEPENDENCY_CYCLE); data.setDirty(); return; }
        SettlementEconomyState state = data.forSettlement(request.settlementId());
        int alreadyFree = Math.max(0, index.observed(request.settlementId(), request.output())
                - state.protectedReserves().getOrDefault(request.output(), 0));
        // Existing stock may satisfy a not-yet-started request. Once this request has produced
        // anything, its own output must not be counted again as progress.
        if (request.reason() != ProductionReason.TOOL_SHORTAGE && request.completedQuantity() == 0
                && alreadyFree >= request.desiredQuantity()) {
            request.complete(request.desiredQuantity()); data.setDirty(); return;
        }

        List<RecipeHolder<?>> candidates = recipes.candidates(request.output()).stream().filter(knowledge::knows).toList();
        if (candidates.isEmpty()) { request.block(ProductionBlocker.MISSING_RECIPE); data.setDirty(); return; }
        RecipeHolder<?> selected = candidates.stream().min(java.util.Comparator
                .comparingInt((RecipeHolder<?> r) -> viabilityScore(request.settlementId(), r))
                .thenComparingInt(r -> method(r) == ProductionMethod.SMELT || method(r) == ProductionMethod.CRAFT ? 0 : 10)).orElseThrow();
        ProductionMethod method = method(selected);
        WorkshopType requiredWorkshop = method.usesFurnace() ? WorkshopType.SMELTING : WorkshopType.GENERAL_CRAFTING;
        if (state.workshops().stream().noneMatch(w -> w.type() == requiredWorkshop)) { request.block(ProductionBlocker.MISSING_WORKSTATION); data.setDirty(); return; }
        ItemStack recipeOutput = selected.value().getResultItem(server.registryAccess());
        int batches = ProductionBatching.batchesForNextStep(method, request.remainingQuantity(), recipeOutput.getCount());
        Map<ItemKey, Integer> needs = ingredientNeeds(request.settlementId(), selected, batches);
        if (method == ProductionMethod.SMELT || method == ProductionMethod.SMOKE || method == ProductionMethod.BLAST) {
            ItemKey fuel = selectFuel(request.settlementId());
            if (fuel == null) { request.block(ProductionBlocker.MISSING_FUEL); data.setDirty(); return; }
            needs.merge(fuel, Math.max(1, (batches + 7) / 8), Integer::sum);
        }
        request.plan(method, selected.id(), needs);

        boolean allAvailable = true;
        for (Map.Entry<ItemKey, Integer> need : needs.entrySet()) {
            int free = Math.max(0, index.observed(request.settlementId(), need.getKey())
                    - state.protectedReserves().getOrDefault(need.getKey(), 0));
            if (free >= need.getValue()) continue;
            allAvailable = false;
            ItemKey missing = need.getKey(); int missingAmount = need.getValue() - free;
            ProductionRequest child = state.productionRequests().stream().filter(r -> r.output().equals(missing)
                    && r.state() != ProductionState.COMPLETE && r.state() != ProductionState.CANCELLED)
                    .findFirst().orElse(null);
            if (child == null) {
                ProductionRequest created = new ProductionRequest(java.util.UUID.randomUUID(), request.settlementId(), missing,
                        missingAmount, request.priority(), ProductionReason.WORKSHOP_INPUT);
                state.putProduction(created); plan(created, depth + 1, new HashSet<>(visiting));
            } else child.promote(request.priority());
        }
        request.setState(allAvailable ? ProductionState.WAITING_FOR_DELIVERY : ProductionState.WAITING_FOR_INPUT);
        data.setDirty();
    }

    private int missingScore(java.util.UUID settlementId, RecipeHolder<?> recipe) {
        return ingredientNeeds(settlementId, recipe, 1).entrySet().stream()
                .mapToInt(e -> Math.max(0, e.getValue() - index.observed(settlementId, e.getKey()))).sum();
    }

    /** Prefer missing inputs that the settlement can recursively make over unavailable raw alternatives. */
    private int viabilityScore(java.util.UUID settlementId, RecipeHolder<?> recipe) {
        int score = missingScore(settlementId, recipe);
        for (Map.Entry<ItemKey, Integer> need : ingredientNeeds(settlementId, recipe, 1).entrySet()) {
            if (index.observed(settlementId, need.getKey()) >= need.getValue()) continue;
            if (recipes.candidates(need.getKey()).stream().noneMatch(knowledge::knows)) score += 1_000_000;
        }
        return score;
    }

    private Map<ItemKey, Integer> ingredientNeeds(java.util.UUID settlementId, RecipeHolder<?> holder, int batches) {
        Map<ItemKey, Integer> needs = new LinkedHashMap<>();
        for (Ingredient ingredient : holder.value().getIngredients()) {
            ItemStack[] options = ingredient.getItems(); if (options.length == 0) continue;
            ItemStack selected = java.util.Arrays.stream(options).max(java.util.Comparator.comparingInt(stack ->
                    index.observed(settlementId, ItemKey.bulk(stack)))).orElse(options[0]);
            needs.merge(ItemKey.bulk(selected), batches, Integer::sum);
        }
        return needs;
    }

    private static ProductionMethod method(RecipeHolder<?> holder) {
        return holder.value().getType() == RecipeType.SMELTING ? ProductionMethod.SMELT :
                holder.value().getType() == RecipeType.SMOKING ? ProductionMethod.SMOKE :
                holder.value().getType() == RecipeType.BLASTING ? ProductionMethod.BLAST : ProductionMethod.CRAFT;
    }

    private ItemKey selectFuel(java.util.UUID settlementId) {
        for (ItemStack candidate : List.of(new ItemStack(Items.COAL), new ItemStack(Items.CHARCOAL),
                new ItemStack(Items.OAK_LOG), new ItemStack(Items.OAK_PLANKS))) {
            ItemKey key = ItemKey.bulk(candidate);
            if (AbstractFurnaceBlockEntity.isFuel(candidate) && index.observed(settlementId, key) > 0) return key;
        }
        return null;
    }
}
