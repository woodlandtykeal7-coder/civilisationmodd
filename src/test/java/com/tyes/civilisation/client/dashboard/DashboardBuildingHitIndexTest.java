package com.tyes.civilisation.client.dashboard;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class DashboardBuildingHitIndexTest {
    @Test void clicksFollowDrawnCoordinatesInsteadOfAssumingProjectRowHeights() {
        var index = new DashboardBuildingHitIndex();
        UUID first = UUID.randomUUID(), second = UUID.randomUUID();
        index.beginFrame(100, 320, 70, 260);
        // The project preceding these rows can have variable-height reasons, builders and materials.
        index.add(first, 165, 192);
        index.add(second, 215, 242);
        assertEquals(first, index.hit(130, 175));
        assertEquals(second, index.hit(130, 232));
        assertNull(index.hit(130, 135)); // Project text, not a completed building
    }

    @Test void clippingPreventsInvisibleRowsAndEdgesBeingClicked() {
        var index = new DashboardBuildingHitIndex();
        UUID id = UUID.randomUUID();
        index.beginFrame(100, 320, 70, 200);
        index.add(id, 185, 212); // Row extends below the scissor viewport.
        assertEquals(id, index.hit(150, 195));
        assertNull(index.hit(150, 200));
        assertNull(index.hit(150, 210));
        assertNull(index.hit(99, 195));
        assertNull(index.hit(320, 195));
    }

    @Test void scrollingUsesFreshScreenPositionsAndClearsStaleTargets() {
        var index = new DashboardBuildingHitIndex();
        UUID id = UUID.randomUUID();
        index.beginFrame(100, 320, 70, 200);
        index.add(id, 160, 187);
        assertEquals(id, index.hit(150, 170));
        index.beginFrame(100, 320, 70, 200);
        index.add(id, 110, 137); // Rendered position after scrolling.
        assertNull(index.hit(150, 170));
        assertEquals(id, index.hit(150, 120));
    }

    @Test void offscreenAndInvalidTargetsAreIgnored() {
        var index = new DashboardBuildingHitIndex();
        UUID id = UUID.randomUUID();
        index.beginFrame(100, 320, 70, 200);
        index.add(null, 90, 110);
        index.add(id, 250, 277);
        index.add(id, 90, 90);
        assertNull(index.hit(150, 100));
    }
}
