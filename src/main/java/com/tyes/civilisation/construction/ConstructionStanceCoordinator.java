package com.tyes.civilisation.construction;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

/**
 * Per-MinecraftServer temporary standing-space reservations for physical construction.
 * A lease is never a world block, persisted inventory, project claim or route shortcut.
 * It only prevents multiple builders from selecting one intended working square.
 */
public final class ConstructionStanceCoordinator {
    public static final long LEASE_TICKS = 160L;
    private final Map<StanceKey, Lease> leases = new HashMap<>();

    public synchronized boolean available(UUID projectId, ResourceLocation dimension, BlockPos feet,
                                          UUID citizenId, long now) {
        Lease current = leases.get(new StanceKey(projectId, dimension, feet.immutable()));
        return current == null || current.expiresAt <= now || current.owner.equals(citizenId);
    }

    public synchronized boolean claim(UUID projectId, ResourceLocation dimension, BlockPos feet,
                                      UUID citizenId, long now) {
        StanceKey key = new StanceKey(projectId, dimension, feet.immutable());
        Lease previous = leases.get(key);
        if (previous != null && previous.expiresAt > now && !previous.owner.equals(citizenId)) return false;
        leases.put(key, new Lease(citizenId, now + LEASE_TICKS));
        // Opportunistic pruning keeps stale positions bounded on a long-running server.
        if (leases.size() > 256) prune(now);
        return true;
    }

    public synchronized void release(UUID projectId, ResourceLocation dimension, BlockPos feet, UUID citizenId) {
        StanceKey key = new StanceKey(projectId, dimension, feet);
        Lease previous = leases.get(key);
        if (previous != null && previous.owner.equals(citizenId)) leases.remove(key);
    }

    public synchronized void releaseProject(UUID projectId) {
        leases.keySet().removeIf(key -> key.projectId.equals(projectId));
    }

    public synchronized void prune(long now) {
        Iterator<Map.Entry<StanceKey, Lease>> it = leases.entrySet().iterator();
        while (it.hasNext()) if (it.next().getValue().expiresAt <= now) it.remove();
    }

    public synchronized int activeLeases(long now) {
        prune(now);
        return leases.size();
    }

    private record StanceKey(UUID projectId, ResourceLocation dimension, BlockPos feet) {}
    private record Lease(UUID owner, long expiresAt) {}
}
