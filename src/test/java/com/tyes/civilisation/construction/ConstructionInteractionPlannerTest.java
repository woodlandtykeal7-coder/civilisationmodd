package com.tyes.civilisation.construction;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

class ConstructionInteractionPlannerTest {
    @Test void roofCandidatesIncludeInteriorFloorAndCardinalPositions() {
        BlockPos roof = new BlockPos(10, 74, 10);
        List<BlockPos> candidates = ConstructionInteractionPlanner.candidates(roof, 70, 74);
        assertTrue(candidates.contains(new BlockPos(10, 70, 10)));
        assertTrue(candidates.contains(new BlockPos(9, 70, 10)));
        assertTrue(candidates.contains(new BlockPos(11, 70, 10)));
        assertTrue(candidates.contains(new BlockPos(10, 73, 9)));
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
