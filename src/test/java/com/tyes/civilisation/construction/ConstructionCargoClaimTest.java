package com.tyes.civilisation.construction;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.tyes.civilisation.citizen.AssignedLocation;
import com.tyes.civilisation.construction.blueprint.PlannedBlock;
import com.tyes.civilisation.construction.blueprint.RelativeBlockPos;
import com.tyes.civilisation.construction.blueprint.ResolvedBlueprint;
import com.tyes.civilisation.construction.material.MaterialPalette;
import com.tyes.civilisation.construction.material.MaterialRole;
import com.tyes.civilisation.construction.project.ConstructionProject;
import com.tyes.civilisation.construction.style.ArchitectureStyleId;
import com.tyes.civilisation.economy.inventory.InventoryEndpointId;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

final class ConstructionCargoClaimTest {
    @Test
    void carriedLayerMaterialDoesNotPreventASecondBuilderClaim() {
        ResourceLocation stone = ResourceLocation.parse("minecraft:cobblestone");
        ResolvedBlueprint blueprint = new ResolvedBlueprint(UUID.randomUUID(),
                ResourceLocation.parse("civilisation:starter_house"),
                new ArchitectureStyleId(ResourceLocation.parse("civilisation:temperate_timber")), 1,
                List.of(new PlannedBlock(new RelativeBlockPos(0, 0, 0), stone, true),
                        new PlannedBlock(new RelativeBlockPos(1, 0, 0), stone, true)));
        AssignedLocation location = new AssignedLocation(ResourceLocation.parse("minecraft:overworld"), BlockPos.ZERO);
        ConstructionProject project = new ConstructionProject(UUID.randomUUID(), "P-0001", UUID.randomUUID(),
                location, location, new InventoryEndpointId(UUID.randomUUID()), blueprint,
                new MaterialPalette(Map.of(MaterialRole.FOUNDATION, stone)));
        UUID holder = UUID.randomUUID();
        project.cargoPickedUp(holder, stone, 1);
        assertEquals(0, project.claim(UUID.randomUUID(), Set.of()).orElseThrow());
        assertEquals(1, project.claim(holder, Set.of(stone)).orElseThrow());
    }
}
