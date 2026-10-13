package com.tyes.civilisation.construction;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

/**
 * Per-server short-lived standing-square coordination for physical construction.
 * A world cell is exclusive across ALL active projects, not just within one
 * blueprint. This matters when a single guild sends builders to adjoining jobs.
 * Leases never represent physical blocks, inventories or construction claims.
 */
public final class ConstructionStanceCoordinator {
    public static final long LEASE_TICKS = 160L;
    private final Map<StanceKey, Lease> leases = new HashMap<>();

    public synchronized boolean available(UUID projectId, ResourceLocation dimension, BlockPos feet,
                                          UUID citizenId, long now) {
        Lease current = leases.get(new StanceKey(dimension, feet.immutable()));
        return current == null || current.expiresAt <= now
                || current.projectId.equals(projectId) && current.owner.equals(citizenId);
    }

    public synchronized boolean claim(UUID projectId, ResourceLocation dimension, BlockPos feet,
                                      UUID citizenId, long now) {
        StanceKey key = new StanceKey(dimension, feet.immutable());
        Lease current = leases.get(key);
        if (current != null && current.expiresAt > now
                && (!current.projectId.equals(projectId) || !current.owner.equals(citizenId))) return false;
        leases.put(key, new Lease(projectId, citizenId, now + LEASE_TICKS));
        if (leases.size() > 256) prune(now);
        return true;
    }

    public synchronized void release(UUID projectId, ResourceLocation dimension, BlockPos feet, UUID citizenId) {
        StanceKey key = new StanceKey(dimension, feet);
        Lease current = leases.get(key);
        if (current != null && current.projectId.equals(projectId) && current.owner.equals(citizenId))
            leases.remove(key);
    }

    public synchronized void releaseProject(UUID projectId) {
        leases.values().removeIf(lease -> lease.projectId.equals(projectId));
    }

    public synchronized void prune(long now) {
        Iterator<Map.Entry<StanceKey, Lease>> iterator = leases.entrySet().iterator();
        while (iterator.hasNext()) if (iterator.next().getValue().expiresAt <= now) iterator.remove();
    }

    public synchronized int activeLeases(long now) {
        prune(now);
        return leases.size();
    }

    private record StanceKey(ResourceLocation dimension, BlockPos feet) {}
    private record Lease(UUID projectId, UUID owner, long expiresAt) {}
}
