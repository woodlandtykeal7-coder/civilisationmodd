package com.tyes.civilisation.construction;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.tyes.civilisation.construction.demand.BuildingDemand;
import com.tyes.civilisation.construction.demand.BuildingDemandReason;
import com.tyes.civilisation.construction.demand.DemandSeverity;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

final class BuildingDemandTest {
    @Test
    void housingIntentAndAttachedProjectPersistWithoutDuplication() {
        UUID settlement = UUID.randomUUID();
        UUID project = UUID.randomUUID();
        BuildingDemand demand = new BuildingDemand(settlement,
                ResourceLocation.parse("civilisation:starter_house"), BuildingDemandReason.HOUSING_SHORTAGE,
                DemandSeverity.HIGH, 12, 8, 200L);
        demand.attachProject(project);
        demand.update(DemandSeverity.CRITICAL, 14, 8);

        BuildingDemand loaded = BuildingDemand.load(demand.save());
        assertEquals(settlement, loaded.settlementId());
        assertEquals(project, loaded.activeProjectId().orElseThrow());
        assertEquals(DemandSeverity.CRITICAL, loaded.severity());
        assertEquals(14, loaded.desiredCapacity());
        assertEquals(8, loaded.currentCapacity());
    }
}
