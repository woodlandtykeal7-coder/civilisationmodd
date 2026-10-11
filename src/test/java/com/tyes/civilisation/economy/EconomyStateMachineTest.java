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

class EconomyStateMachineTest {
    @Test void deliveryPersistsOneAuthoritativeCargoHolder() {
        UUID courier = UUID.randomUUID();
        DeliveryRequest request = new DeliveryRequest(UUID.randomUUID(), UUID.randomUUID(), InventoryEndpointId.create(),
                InventoryEndpointId.create(), key("minecraft:oak_log"), 7, DeliveryPriority.NORMAL,
                DeliveryPurpose.WORKSHOP_INPUT, UUID.randomUUID(), UUID.randomUUID());
        request.assign(courier); request.loaded(7);
        DeliveryRequest restored = DeliveryRequest.load(request.save());
        assertEquals(7, restored.cargoQuantity()); assertEquals(courier, restored.courierId().orElseThrow());
        restored.unloaded(7);
        assertEquals(DeliveryState.COMPLETE, restored.state()); assertEquals(0, restored.cargoQuantity());
    }

    @Test void productionProgressIsBoundedAndRoundTrips() {
        ProductionRequest request = new ProductionRequest(UUID.randomUUID(), UUID.randomUUID(),
                key("minecraft:oak_planks"), 8, ProductionPriority.NORMAL, ProductionReason.MANUAL_TEST);
        request.plan(ProductionMethod.CRAFT, ResourceLocation.parse("minecraft:oak_planks"),
                Map.of(key("minecraft:oak_log"), 2)); request.complete(4);
        ProductionRequest restored = ProductionRequest.load(request.save());
        assertEquals(4, restored.completedQuantity()); assertEquals(4, restored.remainingQuantity());
        restored.complete(100);
        assertEquals(8, restored.completedQuantity()); assertEquals(0, restored.remainingQuantity());
        assertEquals(ProductionState.COMPLETE, restored.state());
    }

    @Test void constructionDemandCanBePromotedAndAggregatedWithoutResettingProgress() {
        ProductionRequest request = new ProductionRequest(UUID.randomUUID(), UUID.randomUUID(),
                key("minecraft:oak_planks"), 12, ProductionPriority.NORMAL, ProductionReason.CONSTRUCTION);
        request.complete(4); request.ensureRemainingDemand(16); request.promote(ProductionPriority.HIGH);
        ProductionRequest restored = ProductionRequest.load(request.save());
        assertEquals(20, restored.desiredQuantity()); assertEquals(4, restored.completedQuantity());
        assertEquals(16, restored.remainingQuantity()); assertEquals(ProductionPriority.HIGH, restored.priority());
    }

    private static ItemKey key(String id) { return new ItemKey(ResourceLocation.parse(id), 0, false); }
}
