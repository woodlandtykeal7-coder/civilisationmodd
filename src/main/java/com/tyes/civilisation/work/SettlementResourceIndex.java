package com.tyes.civilisation.work;

import com.tyes.civilisation.economy.StockSummary;
import com.tyes.civilisation.economy.inventory.InventoryEndpointId;
import com.tyes.civilisation.economy.item.ItemKey;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Runtime cache and reservation foundation. Physical inventories are authoritative:
 * every reconciliation replaces cached counts and invalidates reservations that no
 * longer fit the observed stock.
 */
public final class SettlementResourceIndex {
    private final Map<UUID, Map<String, Integer>> observed = new HashMap<>();
    private final Map<UUID, Map<InventoryEndpointId, Map<ItemKey, Integer>>> endpointObserved = new HashMap<>();
    private final Map<UUID, LinkedHashMap<UUID, Reservation>> reservations = new HashMap<>();

    public synchronized void reconcile(UUID settlementId, Map<String, Integer> physicalCounts, long now) {
        expire(now);
        observed.put(settlementId, Map.copyOf(physicalCounts));
        LinkedHashMap<UUID, Reservation> entries = reservations.get(settlementId);
        if (entries == null) return;
        Map<String, Integer> remaining = new HashMap<>(physicalCounts);
        Iterator<Map.Entry<UUID, Reservation>> iterator = entries.entrySet().iterator();
        while (iterator.hasNext()) {
            Reservation reservation = iterator.next().getValue();
            int available = remaining.getOrDefault(reservation.resourceKey(), 0);
            if (reservation.amount() > available) iterator.remove();
            else remaining.put(reservation.resourceKey(), available - reservation.amount());
        }
        if (entries.isEmpty()) reservations.remove(settlementId);
    }

    public synchronized Optional<UUID> reserve(UUID settlementId, UUID citizenId, String resourceKey,
                                               int amount, long now, long ttlTicks) {
        if (amount <= 0 || ttlTicks <= 0) return Optional.empty();
        expire(now);
        if (available(settlementId, resourceKey, now) < amount) return Optional.empty();
        UUID token = UUID.randomUUID();
        reservations.computeIfAbsent(settlementId, ignored -> new LinkedHashMap<>())
                .put(token, new Reservation(citizenId, resourceKey, amount, now + ttlTicks));
        return Optional.of(token);
    }

    public synchronized void release(UUID settlementId, UUID token) {
        LinkedHashMap<UUID, Reservation> entries = reservations.get(settlementId);
        if (entries == null) return;
        entries.remove(token);
        if (entries.isEmpty()) reservations.remove(settlementId);
    }

    public synchronized int observed(UUID settlementId, String resourceKey) {
        return observed.getOrDefault(settlementId, Map.of()).getOrDefault(resourceKey, 0);
    }

    public synchronized int reserved(UUID settlementId, String resourceKey, long now) {
        expire(now);
        return reservations.getOrDefault(settlementId, new LinkedHashMap<>()).values().stream()
                .filter(r -> r.resourceKey().equals(resourceKey)).mapToInt(Reservation::amount).sum();
    }

    public synchronized int available(UUID settlementId, String resourceKey, long now) {
        return Math.max(0, observed(settlementId, resourceKey) - reserved(settlementId, resourceKey, now));
    }

    public synchronized void expire(long now) {
        reservations.values().forEach(entries -> entries.values().removeIf(r -> r.expiryTick() <= now));
        reservations.entrySet().removeIf(entry -> entry.getValue().isEmpty());
    }

    public synchronized void reconcileEndpoint(UUID settlementId, InventoryEndpointId endpoint,
                                               Map<ItemKey, Integer> physicalCounts) {
        endpointObserved.computeIfAbsent(settlementId, ignored -> new LinkedHashMap<>())
                .put(endpoint, Map.copyOf(physicalCounts));
    }

    public synchronized void removeEndpoint(UUID settlementId, InventoryEndpointId endpoint) {
        Map<InventoryEndpointId, Map<ItemKey, Integer>> endpoints = endpointObserved.get(settlementId);
        if (endpoints != null) endpoints.remove(endpoint);
    }

    public synchronized int observed(UUID settlementId, InventoryEndpointId endpoint, ItemKey item) {
        return endpointObserved.getOrDefault(settlementId, Map.of())
                .getOrDefault(endpoint, Map.of()).entrySet().stream()
                .filter(entry -> sameBulk(entry.getKey(), item)).mapToInt(Map.Entry::getValue).sum();
    }

    public synchronized int observed(UUID settlementId, ItemKey item) {
        return endpointObserved.getOrDefault(settlementId, Map.of()).values().stream()
                .flatMap(map -> map.entrySet().stream()).filter(entry -> sameBulk(entry.getKey(), item))
                .mapToInt(Map.Entry::getValue).sum();
    }

    public synchronized Map<InventoryEndpointId, Integer> locations(UUID settlementId, ItemKey item) {
        Map<InventoryEndpointId, Integer> result = new LinkedHashMap<>();
        endpointObserved.getOrDefault(settlementId, Map.of()).forEach((endpoint, counts) -> {
            int amount = counts.entrySet().stream().filter(entry -> sameBulk(entry.getKey(), item))
                    .mapToInt(Map.Entry::getValue).sum();
            if (amount > 0) result.put(endpoint, amount);
        });
        return Map.copyOf(result);
    }

    public synchronized StockSummary summary(UUID settlementId, ItemKey item, long reserved,
                                             long protectedAmount, long inTransit) {
        long physical = observed(settlementId, item);
        return new StockSummary(physical, Math.max(0, reserved), Math.max(0, protectedAmount),
                Math.max(0, inTransit), Math.max(0, physical - reserved - protectedAmount));
    }

    private static boolean sameBulk(ItemKey left, ItemKey right) {
        if (!left.itemId().equals(right.itemId())) return false;
        return !left.componentSensitive() || !right.componentSensitive() || left.componentHash() == right.componentHash();
    }

    private record Reservation(UUID citizenId, String resourceKey, int amount, long expiryTick) { }
}
