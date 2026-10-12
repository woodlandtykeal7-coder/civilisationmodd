package com.tyes.civilisation.economy;

import static org.junit.jupiter.api.Assertions.*;
import com.tyes.civilisation.economy.inventory.InventoryEndpointId;
import com.tyes.civilisation.economy.item.ItemKey;
import com.tyes.civilisation.economy.logistics.*;
import com.tyes.civilisation.economy.production.*;
import java.util.Map;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class ProductionSupplyRecoveryTest {
    private static ItemKey item(String id) { return new ItemKey(ResourceLocation.parse(id), 0, false); }
    private static ProductionRequest planks() {
        return new ProductionRequest(UUID.randomUUID(), UUID.randomUUID(), item("minecraft:oak_planks"),
                133, ProductionPriority.CRITICAL, ProductionReason.CONSTRUCTION);
    }

    @Test void aSingleLogCanStartPlankCraftingDespite65PlanksRemaining() {
        assertEquals(1, ProductionBatching.batchesForNextStep(ProductionMethod.CRAFT, 65, 4));
        assertEquals(1, ProductionBatching.batchesForNextStep(ProductionMethod.CRAFT, 133, 4));
        assertEquals(1, ProductionBatching.batchesForNextStep(ProductionMethod.CRAFT, 1, 4));
        assertEquals(17, ProductionBatching.batchesForNextStep(ProductionMethod.SMELT, 65, 4));
    }

    @Test void oversizedSavedCraftOrderIsDetectableAndReplanningPreservesProgress() {
        ProductionRequest req = planks();
        req.complete(68);
        req.plan(ProductionMethod.CRAFT, ResourceLocation.parse("minecraft:oak_planks"),
                Map.of(item("minecraft:oak_log"), 17));
        req.setState(ProductionState.WAITING_FOR_DELIVERY);
        assertTrue(ProductionBatching.hasOversizedCraftInputs(req, Map.of(item("minecraft:oak_log"), 1)));
        var restored = ProductionRequest.load(req.save());
        assertEquals(68, restored.completedQuantity());
        assertTrue(ProductionBatching.hasOversizedCraftInputs(restored, Map.of(item("minecraft:oak_log"), 1)));
        restored.plan(ProductionMethod.CRAFT, ResourceLocation.parse("minecraft:oak_planks"),
                Map.of(item("minecraft:oak_log"), 1));
        restored.setState(ProductionState.WAITING_FOR_DELIVERY);
        assertFalse(ProductionBatching.hasOversizedCraftInputs(restored, Map.of(item("minecraft:oak_log"), 1)));
        assertEquals(65, restored.remainingQuantity());
    }

    @Test void aCraftOrderAlreadyClaimedByAnNpcIsNeverReplanned() {
        ProductionRequest req = planks();
        req.plan(ProductionMethod.CRAFT, ResourceLocation.parse("minecraft:oak_planks"),
                Map.of(item("minecraft:oak_log"), 17));
        req.claim(UUID.randomUUID());
        assertFalse(ProductionBatching.hasOversizedCraftInputs(req, Map.of(item("minecraft:oak_log"), 1)));
    }

    @Test void onlyExactWorkshopInputDeliveriesCountAndLoadedCargoIsConserved() {
        ProductionRequest request = planks();
        InventoryEndpointId source = InventoryEndpointId.create(), input = InventoryEndpointId.create();
        DeliveryRequest delivery = new DeliveryRequest(UUID.randomUUID(), request.settlementId(), source, input,
                item("minecraft:oak_log"), 17, DeliveryPriority.CRITICAL, DeliveryPurpose.WORKSHOP_INPUT,
                request.requestId(), UUID.randomUUID());
        delivery.setState(DeliveryState.RESERVED);
        assertTrue(WorkshopSupplyPolicy.matchesInput(delivery, request, input, item("minecraft:oak_log")));
        assertFalse(WorkshopSupplyPolicy.matchesInput(delivery, request, InventoryEndpointId.create(), item("minecraft:oak_log")));
        assertFalse(WorkshopSupplyPolicy.matchesInput(delivery, request, input, item("minecraft:oak_planks")));
        assertTrue(WorkshopSupplyPolicy.canCancelUnloaded(delivery));
        assertEquals(17, WorkshopSupplyPolicy.incoming(delivery));
        // The migrated order now needs only one log, so the old 17-log
        // reservation must be released before pickup, never duplicated.
        assertTrue(WorkshopSupplyPolicy.shouldRetire(delivery, true, 17, 1));
        assertFalse(WorkshopSupplyPolicy.shouldRetire(delivery, true, 1, 1));
        // Expired reservations may not count as incoming stock.
        assertTrue(WorkshopSupplyPolicy.shouldRetire(delivery, false, 1, 1));
        delivery.assign(UUID.randomUUID()); delivery.loaded(2);
        assertEquals(2, WorkshopSupplyPolicy.incoming(delivery));
        assertFalse(WorkshopSupplyPolicy.canCancelUnloaded(delivery));
        assertFalse(WorkshopSupplyPolicy.shouldRetire(delivery, false, 2, 1));
        assertEquals(2, DeliveryRequest.load(delivery.save()).cargoQuantity());
    }

    @Test void completingEachRealBatchPreservesAmountsAndReload() {
        ProductionRequest req = planks();
        req.plan(ProductionMethod.CRAFT, ResourceLocation.parse("minecraft:oak_planks"),
                Map.of(item("minecraft:oak_log"), 1));
        req.complete(4);
        assertEquals(4, req.completedQuantity());
        assertEquals(129, req.remainingQuantity());
        assertEquals(ProductionState.PLANNED, req.state());
        assertEquals(4, ProductionRequest.load(req.save()).completedQuantity());
    }
}
