package com.tyes.civilisation.construction;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;

/** Bounded geometry and transient failed-position memory for physical block placement. */
public final class ConstructionInteractionPlanner {
    private ConstructionInteractionPlanner() {}

    /**
     * Includes same-column interior floor positions, cardinal/diagonal positions,
     * and nearby completed surfaces. World collision and path reachability are
     * deliberately evaluated by the server-side task after this bounded pass.
     */
    public static List<BlockPos> candidates(BlockPos target, int lowestFeetY, int highestFeetY) {
        LinkedHashSet<BlockPos> result = new LinkedHashSet<>();
        int low = Math.min(lowestFeetY, highestFeetY);
        int high = Math.max(lowestFeetY, highestFeetY);
        for (int y = low; y <= high; y++) {
            for (int radius = 0; radius <= 3; radius++) {
                for (int dx = -radius; dx <= radius; dx++) {
                    for (int dz = -radius; dz <= radius; dz++) {
                        if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) continue;
                        result.add(new BlockPos(target.getX() + dx, y, target.getZ() + dz));
                    }
                }
            }
        }
        return new ArrayList<>(result);
    }

    /** Short-lived task-local blacklist; intentionally not persistent project state. */
    public static final class FailureMemory {
        private static final int FAILURE_TTL = 240;
        private final Map<BlockPos, Integer> failedUntil = new HashMap<>();
        private final ArrayDeque<BlockPos> selections = new ArrayDeque<>();
        private int clock;

        public void tick() {
            clock++;
            failedUntil.entrySet().removeIf(entry -> entry.getValue() <= clock);
        }

        public void failed(BlockPos position) {
            if (position != null) failedUntil.put(position.immutable(), clock + FAILURE_TTL);
        }

        public boolean available(BlockPos position) {
            return failedUntil.getOrDefault(position, 0) <= clock;
        }

        public void selected(BlockPos position) {
            selections.addLast(position.immutable());
            while (selections.size() > 4) selections.removeFirst();
        }

        public boolean alternating() {
            if (selections.size() < 4) return false;
            BlockPos[] values = selections.toArray(BlockPos[]::new);
            return values[0].equals(values[2]) && values[1].equals(values[3])
                    && !values[0].equals(values[1]);
        }

        /** A completed placement starts a new, independent candidate search. */
        public void clear() {
            failedUntil.clear();
            selections.clear();
        }

        public int blockedCount() { return failedUntil.size(); }
    }
}
