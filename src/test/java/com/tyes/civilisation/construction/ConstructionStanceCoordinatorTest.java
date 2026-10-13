package com.tyes.civilisation.construction;

import static org.junit.jupiter.api.Assertions.*;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class ConstructionStanceCoordinatorTest {
    private static final ResourceLocation OVERWORLD = ResourceLocation.parse("minecraft:overworld");

    @Test void oneWorkingSquareCannotBeClaimedByTwoBuilders() {
        var c = new ConstructionStanceCoordinator();
        UUID project = UUID.randomUUID(), a = UUID.randomUUID(), b = UUID.randomUUID();
        BlockPos position = new BlockPos(12, 64, 20);
        assertTrue(c.claim(project, OVERWORLD, position, a, 10));
        assertTrue(c.claim(project, OVERWORLD, position, a, 20));
        assertFalse(c.available(project, OVERWORLD, position, b, 20));
        assertFalse(c.claim(project, OVERWORLD, position, b, 20));
        assertEquals(1, c.activeLeases(20));
        c.release(project, OVERWORLD, position, b);
        assertEquals(1, c.activeLeases(20));
        c.release(project, OVERWORLD, position, a);
        assertTrue(c.claim(project, OVERWORLD, position, b, 21));
    }

    @Test void leasesAreSeparateForProjectsAndWorlds() {
        var c = new ConstructionStanceCoordinator();
        UUID p = UUID.randomUUID(), q = UUID.randomUUID();
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        var feet = new BlockPos(0, 64, 0);
        assertTrue(c.claim(p, OVERWORLD, feet, a, 0));
        assertTrue(c.claim(q, OVERWORLD, feet, b, 0));
        assertTrue(c.claim(p, ResourceLocation.parse("minecraft:the_nether"), feet, b, 0));
        assertEquals(3, c.activeLeases(5));
        c.releaseProject(p);
        assertEquals(1, c.activeLeases(5));
    }

    @Test void expiryMakesAbandonedStandingSpaceAvailableAgain() {
        var c = new ConstructionStanceCoordinator();
        UUID p = UUID.randomUUID(), a = UUID.randomUUID(), b = UUID.randomUUID();
        BlockPos feet = BlockPos.ZERO;
        assertTrue(c.claim(p, OVERWORLD, feet, a, 100));
        assertFalse(c.claim(p, OVERWORLD, feet, b, 100 + ConstructionStanceCoordinator.LEASE_TICKS - 1));
        assertTrue(c.claim(p, OVERWORLD, feet, b, 100 + ConstructionStanceCoordinator.LEASE_TICKS));
        c.release(p, OVERWORLD, feet, a);
        assertEquals(1, c.activeLeases(100 + ConstructionStanceCoordinator.LEASE_TICKS));
        c.prune(100 + 2 * ConstructionStanceCoordinator.LEASE_TICKS);
        assertEquals(0, c.activeLeases(100 + 2 * ConstructionStanceCoordinator.LEASE_TICKS));
    }

    @Test void oneBuilderMaySelectDifferentNearbySquaresWhileTheOtherWorks() {
        var c = new ConstructionStanceCoordinator();
        UUID p = UUID.randomUUID(), a = UUID.randomUUID(), b = UUID.randomUUID();
        assertTrue(c.claim(p, OVERWORLD, new BlockPos(1,64,1), a, 0));
        assertTrue(c.claim(p, OVERWORLD, new BlockPos(1,64,2), b, 0));
        assertEquals(2, c.activeLeases(1));
    }
}
