package com.tyes.civilisation.client.dashboard;

/** Pure GUI geometry helpers: works without a running Minecraft client. */
public final class DashboardViewport {
    private DashboardViewport() {}

    /** Seven vertical tabs need at least 55 + 7*25 + 12 pixels of panel height. */
    public static boolean compactTabs(int panelWidth, int panelHeight) {
        return panelWidth < 430 || panelHeight < 246;
    }

    public static int cycleTab(int selected, int delta, int tabCount) {
        if (tabCount <= 0) throw new IllegalArgumentException("A dashboard needs at least one tab");
        return Math.floorMod(selected + delta, tabCount);
    }

    public static int maxScroll(int contentHeight, int viewportHeight) {
        return Math.max(0, contentHeight - Math.max(0, viewportHeight));
    }

    public static int clampScroll(int wanted, int maxScroll) {
        return Math.max(0, Math.min(wanted, Math.max(0, maxScroll)));
    }

    public static boolean contains(double mouseX, double mouseY,
                                   int left, int top, int width, int height) {
        return width > 0 && height > 0 && mouseX >= left && mouseX < left + width
                && mouseY >= top && mouseY < top + height;
    }
}
