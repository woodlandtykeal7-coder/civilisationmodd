package com.tyes.civilisation.economy.production;

import com.tyes.civilisation.economy.item.ItemKey;
import java.util.Map;

/** Plans only the next *physical* crafting operation, not an entire house-worth of ingredients. */
public final class ProductionBatching {
    private ProductionBatching() {}

    public static int batchesForNextStep(ProductionMethod method, int remaining, int outputPerBatch) {
        if (remaining <= 0 || outputPerBatch <= 0)
            throw new IllegalArgumentException("A production batch must have positive quantities");
        // ProductionTask.craft consumes one recipe and makes one batch per work cycle.
        // Requesting every batch's ingredients at once stalls a construction pipeline.
        if (method == ProductionMethod.CRAFT) return 1;
        return 1 + (remaining - 1) / outputPerBatch;
    }

    public static boolean hasOversizedCraftInputs(ProductionRequest request, Map<ItemKey, Integer> oneBatchNeeds) {
        if (request.method() != ProductionMethod.CRAFT || request.claimedBy().isPresent()) return false;
        return request.needs().entrySet().stream()
                .anyMatch(entry -> entry.getValue() > oneBatchNeeds.getOrDefault(entry.getKey(), 0));
    }
}
