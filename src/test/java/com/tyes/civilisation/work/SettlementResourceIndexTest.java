package com.tyes.civilisation.work;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SettlementResourceIndexTest {
    @Test
    void reservationsNeverExceedPhysicalStockAndExpire() {
        SettlementResourceIndex index = new SettlementResourceIndex();
        UUID settlement = UUID.randomUUID();
        UUID citizen = UUID.randomUUID();
        index.reconcile(settlement, Map.of("minecraft:carrot", 5), 10);
        assertTrue(index.reserve(settlement, citizen, "minecraft:carrot", 4, 10, 20).isPresent());
        assertTrue(index.reserve(settlement, citizen, "minecraft:carrot", 2, 10, 20).isEmpty());
        assertEquals(1, index.available(settlement, "minecraft:carrot", 10));
        assertEquals(5, index.available(settlement, "minecraft:carrot", 30));
    }

    @Test
    void physicalReconciliationInvalidatesReservationsThatNoLongerFit() {
        SettlementResourceIndex index = new SettlementResourceIndex();
        UUID settlement = UUID.randomUUID();
        index.reconcile(settlement, Map.of("minecraft:bread", 3), 0);
        assertTrue(index.reserve(settlement, UUID.randomUUID(), "minecraft:bread", 3, 0, 100).isPresent());
        index.reconcile(settlement, Map.of("minecraft:bread", 1), 1);
        assertEquals(0, index.reserved(settlement, "minecraft:bread", 1));
        assertEquals(1, index.available(settlement, "minecraft:bread", 1));
    }
}
