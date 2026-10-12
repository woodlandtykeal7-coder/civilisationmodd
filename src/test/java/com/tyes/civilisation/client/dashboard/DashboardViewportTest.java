package com.tyes.civilisation.client.dashboard;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class DashboardViewportTest {
    @Test void normalGuiKeepsExistingVerticalTabs() {
        assertFalse(DashboardViewport.compactTabs(620, 390));
        assertFalse(DashboardViewport.compactTabs(430, 246));
    }

    @Test void smallWidthOrHeightUsesAccessibleCyclingTabs() {
        assertTrue(DashboardViewport.compactTabs(320, 390));
        assertTrue(DashboardViewport.compactTabs(620, 230));
        assertEquals(6, DashboardViewport.cycleTab(0, -1, 7));
        assertEquals(0, DashboardViewport.cycleTab(6, 1, 7));
        assertEquals(4, DashboardViewport.cycleTab(2, 2, 7));
    }

    @Test void scrollNeverEscapesRenderedContent() {
        assertEquals(0, DashboardViewport.maxScroll(75, 150));
        assertEquals(80, DashboardViewport.maxScroll(200, 120));
        assertEquals(0, DashboardViewport.clampScroll(-100, 80));
        assertEquals(80, DashboardViewport.clampScroll(1000, 80));
        assertEquals(25, DashboardViewport.clampScroll(25, 80));
        assertEquals(0, DashboardViewport.clampScroll(25, 0));
    }

    @Test void scrollingAndClickTargetsRespectVisibleContentBounds() {
        assertTrue(DashboardViewport.contains(35, 55, 10, 20, 50, 80));
        assertFalse(DashboardViewport.contains(60, 55, 10, 20, 50, 80));
        assertFalse(DashboardViewport.contains(35, 100, 10, 20, 50, 80));
        assertFalse(DashboardViewport.contains(35, 19, 10, 20, 50, 80));
        assertFalse(DashboardViewport.contains(35, 55, 10, 20, 0, 80));
    }
}
