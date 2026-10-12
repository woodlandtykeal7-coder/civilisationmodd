package com.tyes.civilisation.construction;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class ConstructionInteractionPlannerTest {
    @Test void roofTargetsIncludeLowerExteriorGroundAndInteriorStances() {
        BlockPos roof = new BlockPos(10, 74, 10);
        var positions = ConstructionInteractionPlanner.candidates(roof, 68, 75);
        // The caller now inspects two lower ground levels on uneven terrain.
        assertTrue(positions.contains(new BlockPos(7, 69, 10)));
        assertTrue(positions.contains(new BlockPos(11, 70, 11)));
        assertTrue(positions.contains(new BlockPos(10, 72, 10)));
    }

    @Test void roofTargetOffersRealisticInsetAimPoints() {
        BlockPos target = new BlockPos(10, 74, -4);
        var aims = ConstructionInteractionPlanner.aimPoints(target);
        assertEquals(10, aims.size());
        // All four inside-facing roof-corner combinations must be considered.
        assertTrue(aims.contains(new Vec3(10.08D, 74.92D, -3.92D)));
        assertTrue(aims.contains(new Vec3(10.08D, 74.92D, -3.08D)));
        assertTrue(aims.contains(new Vec3(10.92D, 74.92D, -3.92D)));
        assertTrue(aims.contains(new Vec3(10.92D, 74.92D, -3.08D)));
        assertTrue(aims.contains(new Vec3(10.5D, 74.5D, -3.5D)));
        assertTrue(aims.stream().anyMatch(p -> p.y > 74.9D));
        assertTrue(aims.stream().allMatch(p -> p.x > 10 && p.x < 11
                && p.y > 74 && p.y < 75 && p.z > -4 && p.z < -3));
    }

    @Test void roofCandidatesIncludeInteriorFloorAndCardinalPositions() {
        BlockPos roof = new BlockPos(10, 74, 10);
        List<BlockPos> candidates = ConstructionInteractionPlanner.candidates(roof, 70, 74);
        assertTrue(candidates.contains(new BlockPos(10, 70, 10)));
        assertTrue(candidates.contains(new BlockPos(9, 70, 10)));
        assertTrue(candidates.contains(new BlockPos(11, 70, 10)));
        assertTrue(candidates.contains(new BlockPos(10, 73, 9)));
    }

    @Test void reachableRoofStanceWinsOverShorterPartialPath() {
        assertTrue(ConstructionInteractionPlanner.compareCandidatePaths(true, 120.0, false, -30.0) < 0);
        assertTrue(ConstructionInteractionPlanner.compareCandidatePaths(false, -30.0, true, 120.0) > 0);
    }

    @Test void equallyReachablePositionsPreferLowerCost() {
        assertTrue(ConstructionInteractionPlanner.compareCandidatePaths(true, 20.0, true, 35.0) < 0);
        assertTrue(ConstructionInteractionPlanner.compareCandidatePaths(false, 20.0, false, 35.0) < 0);
        assertEquals(0, ConstructionInteractionPlanner.compareCandidatePaths(true, 20.0, true, 20.0));
    }

    @Test void failedCandidatesAreNotImmediatelyReusedAndAlternationIsDetected() {
        var memory = new ConstructionInteractionPlanner.FailureMemory();
        BlockPos a = new BlockPos(1, 2, 3), b = new BlockPos(2, 2, 3);
        memory.failed(a);
        assertFalse(memory.available(a));
        assertTrue(memory.available(b));
        memory.selected(a); memory.selected(b); memory.selected(a); memory.selected(b);
        assertTrue(memory.alternating());
        for (int i = 0; i < 240; i++) memory.tick();
        assertTrue(memory.available(a));
    }
}
