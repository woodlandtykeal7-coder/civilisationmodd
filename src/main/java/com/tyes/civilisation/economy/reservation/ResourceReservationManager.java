package com.tyes.civilisation.economy.reservation;

import com.tyes.civilisation.CivilisationMod;
import com.tyes.civilisation.economy.SettlementEconomyState;
import com.tyes.civilisation.economy.inventory.InventoryEndpoint;
import com.tyes.civilisation.economy.inventory.InventoryEndpointId;
import com.tyes.civilisation.economy.item.ItemKey;
import com.tyes.civilisation.persistence.EconomySavedData;
import com.tyes.civilisation.work.SettlementResourceIndex;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Persistent endpoint-scoped reservations. A reservation is never treated as possession. */
public final class ResourceReservationManager {
    private final EconomySavedData data;
    private final SettlementResourceIndex index;

    public ResourceReservationManager(EconomySavedData data, SettlementResourceIndex index) {
        this.data = data; this.index = index;
    }

    public List<ResourceReservation> reservePartial(UUID settlementId, UUID ownerId, ItemKey item, int requested,
                                                    ReservationPurpose purpose, long now, long ttlTicks) {
        if (requested <= 0 || ttlTicks <= 0) return List.of();
        SettlementEconomyState state = data.forSettlement(settlementId);
        int protectedAmount = purpose == ReservationPurpose.FOOD_CONSUMPTION || purpose == ReservationPurpose.SEED_RESERVE
                ? 0 : state.protectedReserves().getOrDefault(item, 0);
        int remainingProtected = protectedAmount;
        int remaining = requested;
        List<ResourceReservation> created = new ArrayList<>();
        List<Map.Entry<InventoryEndpointId, Integer>> locations = new ArrayList<>(index.locations(settlementId, item).entrySet());
        locations.sort(Comparator.comparing(entry -> entry.getKey().value()));
        for (Map.Entry<InventoryEndpointId, Integer> location : locations) {
            if (remaining == 0) break;
            InventoryEndpoint endpoint = state.endpoint(location.getKey()).orElse(null);
            if (endpoint == null || !endpoint.ownership().permitsAutomaticUse()) continue;
            int alreadyReserved = state.reservations().stream().filter(r -> r.source().equals(location.getKey())
                    && same(r.item(), item)).mapToInt(ResourceReservation::quantity).sum();
            int free = Math.max(0, location.getValue() - alreadyReserved);
            int protectedHere = Math.min(free, remainingProtected);
            free -= protectedHere;
            remainingProtected -= protectedHere;
            int amount = Math.min(free, remaining);
            if (amount <= 0) continue;
            ResourceReservation reservation = new ResourceReservation(UUID.randomUUID(), settlementId, ownerId,
                    item, amount, location.getKey(), purpose, now, now + ttlTicks, endpoint.revision());
            state.putReservation(reservation); created.add(reservation); remaining -= amount;
        }
        if (!created.isEmpty()) data.setDirty();
        return List.copyOf(created);
    }

    /** Reserves from one already-selected physical endpoint; useful for output collection. */
    public java.util.Optional<ResourceReservation> reserveFrom(UUID settlementId, UUID ownerId,
                                                                InventoryEndpointId source, ItemKey item, int requested,
                                                                ReservationPurpose purpose, long now, long ttlTicks) {
        if (requested <= 0 || ttlTicks <= 0) return java.util.Optional.empty();
        SettlementEconomyState state = data.forSettlement(settlementId);
        InventoryEndpoint endpoint = state.endpoint(source).orElse(null);
        if (endpoint == null || !endpoint.ownership().permitsAutomaticUse()) return java.util.Optional.empty();
        int held = reservedAt(settlementId, source, item);
        int free = Math.max(0, index.observed(settlementId, source, item) - held);
        int amount = Math.min(requested, free);
        if (amount <= 0) return java.util.Optional.empty();
        ResourceReservation reservation = new ResourceReservation(UUID.randomUUID(), settlementId, ownerId,
                item, amount, source, purpose, now, now + ttlTicks, endpoint.revision());
        state.putReservation(reservation); data.setDirty();
        return java.util.Optional.of(reservation);
    }

    public void releaseOwner(UUID settlementId, UUID ownerId) {
        SettlementEconomyState state = data.forSettlement(settlementId);
        List<UUID> ids = state.reservations().stream().filter(r -> r.ownerId().equals(ownerId))
                .map(ResourceReservation::reservationId).toList();
        ids.forEach(state::removeReservation); if (!ids.isEmpty()) data.setDirty();
    }

    public void release(UUID settlementId, UUID reservationId) {
        data.forSettlement(settlementId).removeReservation(reservationId);
        data.setDirty();
    }

    public void expire(long now) {
        for (SettlementEconomyState state : data.all()) {
            List<UUID> expired = state.reservations().stream().filter(r -> r.expiryTick() <= now)
                    .map(ResourceReservation::reservationId).toList();
            expired.forEach(id -> { state.removeReservation(id); CivilisationMod.LOGGER.debug("[Economy] Reservation {} expired", id); });
            if (!expired.isEmpty()) data.setDirty();
        }
    }

    public int reserved(UUID settlementId, ItemKey item) {
        return data.forSettlement(settlementId).reservations().stream().filter(r -> same(r.item(), item))
                .mapToInt(ResourceReservation::quantity).sum();
    }

    public int reservedAt(UUID settlementId, InventoryEndpointId endpoint, ItemKey item) {
        return data.forSettlement(settlementId).reservations().stream()
                .filter(r -> r.source().equals(endpoint) && same(r.item(), item))
                .mapToInt(ResourceReservation::quantity).sum();
    }

    private static boolean same(ItemKey a, ItemKey b) {
        return a.itemId().equals(b.itemId()) && (!a.componentSensitive() || !b.componentSensitive() || a.componentHash() == b.componentHash());
    }
}
