package com.tyes.civilisation.client.dashboard;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Screen-space click targets recorded while drawing the settlement's completed-building rows. */
public final class DashboardBuildingHitIndex {
    private final List<Target> targets = new ArrayList<>();
    private int left, right, top, bottom;

    /** Start a new frame; the bounds match the dashboard's visible content viewport. */
    public void beginFrame(int left, int right, int top, int bottom) {
        targets.clear();
        this.left = left;
        this.right = right;
        this.top = top;
        this.bottom = bottom;
    }

    /** Register only completed buildings with valid identifiers and visible row geometry. */
    public void add(UUID buildingId, int rowTop, int rowBottom) {
        if (buildingId != null && rowTop < rowBottom && rowTop < bottom && rowBottom > top)
            targets.add(new Target(buildingId, rowTop, rowBottom));
    }

    /** Returns null outside the visible region or when no displayed building row was clicked. */
    public UUID hit(double mouseX, double mouseY) {
        if (mouseX < left || mouseX >= right || mouseY < top || mouseY >= bottom)
            return null;
        for (Target target : targets) {
            if (mouseY >= target.top && mouseY < target.bottom) return target.id;
        }
        return null;
    }

    private record Target(UUID id, int top, int bottom) {}
}
