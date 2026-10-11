package com.tyes.civilisation.economy;

import static org.junit.jupiter.api.Assertions.*;
import com.tyes.civilisation.citizen.AssignedLocation;
import com.tyes.civilisation.economy.inventory.*;
import com.tyes.civilisation.economy.item.ItemKey;
import com.tyes.civilisation.economy.production.*;
import com.tyes.civilisation.economy.reservation.*;
import com.tyes.civilisation.persistence.EconomySavedData;
import com.tyes.civilisation.work.SettlementResourceIndex;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;

class ReservationAndPersistenceTest {
    @Test void partialReservationsRespectProtectedStockAndNeverDoubleReserve() {
        UUID settlement = UUID.randomUUID(); ItemKey bread = new ItemKey(ResourceLocation.parse("minecraft:bread"), 0, false);
        EconomySavedData data = new EconomySavedData(); SettlementEconomyState state = data.forSettlement(settlement);
        SettlementResourceIndex index = new SettlementResourceIndex();
        InventoryEndpoint a = endpoint(settlement, 1), b = endpoint(settlement, 2);
        state.putEndpoint(a); state.putEndpoint(b); state.setProtected(bread, 3);
        index.reconcileEndpoint(settlement, a.id(), Map.of(bread, 4)); index.reconcileEndpoint(settlement, b.id(), Map.of(bread, 4));
        ResourceReservationManager manager = new ResourceReservationManager(data, index);
        var first = manager.reservePartial(settlement, UUID.randomUUID(), bread, 6, ReservationPurpose.PRODUCTION, 0, 20);
        assertEquals(5, first.stream().mapToInt(ResourceReservation::quantity).sum());
        assertTrue(manager.reservePartial(settlement, UUID.randomUUID(), bread, 1, ReservationPurpose.PRODUCTION, 0, 20).isEmpty());
        manager.expire(20); assertEquals(0, manager.reserved(settlement, bread));
    }

    @Test void economicStateRoundTripsWithoutCreatingStock() {
        UUID settlement = UUID.randomUUID(); SettlementEconomyState state = new SettlementEconomyState(settlement);
        InventoryEndpoint endpoint = endpoint(settlement, 3); state.putEndpoint(endpoint);
        ProductionRequest request = new ProductionRequest(UUID.randomUUID(), settlement,
                new ItemKey(ResourceLocation.parse("minecraft:oak_planks"), 0, false), 8, ProductionPriority.HIGH, ProductionReason.MANUAL_TEST);
        request.plan(ProductionMethod.CRAFT, ResourceLocation.parse("minecraft:oak_planks"),
                Map.of(new ItemKey(ResourceLocation.parse("minecraft:oak_log"), 0, false), 2)); state.putProduction(request);
        SettlementEconomyState restored = SettlementEconomyState.load(state.save());
        assertEquals(1, restored.endpoints().size()); assertEquals(1, restored.productionRequests().size());
        assertEquals(0, restored.protectedReserves().values().stream().mapToInt(Integer::intValue).sum());
        assertEquals(ProductionState.WAITING_FOR_INPUT, restored.production(request.requestId()).orElseThrow().state());
    }

    private static InventoryEndpoint endpoint(UUID settlement, int x) {
        return new InventoryEndpoint(InventoryEndpointId.create(), settlement,
                new AssignedLocation(ResourceLocation.parse("minecraft:overworld"), new BlockPos(x, 64, 0)),
                InventoryEndpointType.SETTLEMENT_STORAGE, InventoryOwnership.SETTLEMENT);
    }
}
