package com.tyes.civilisation.economy;

import com.tyes.civilisation.economy.inventory.InventoryEndpoint;
import com.tyes.civilisation.economy.inventory.InventoryEndpointId;
import com.tyes.civilisation.economy.item.ItemKey;
import com.tyes.civilisation.economy.logistics.DeliveryRequest;
import com.tyes.civilisation.economy.production.ProductionRequest;
import com.tyes.civilisation.economy.reservation.ResourceReservation;
import com.tyes.civilisation.economy.shortage.ShortageSeverity;
import com.tyes.civilisation.economy.shortage.ShortageType;
import com.tyes.civilisation.economy.workshop.Workshop;
import java.util.Collection;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/** Persistent economic coordination state. It never owns item quantities. */
public final class SettlementEconomyState {
    private final UUID settlementId;
    private final Map<UUID, InventoryEndpoint> endpoints = new LinkedHashMap<>();
    private final Map<UUID, Workshop> workshops = new LinkedHashMap<>();
    private final Map<UUID, ResourceReservation> reservations = new LinkedHashMap<>();
    private final Map<UUID, ProductionRequest> production = new LinkedHashMap<>();
    private final Map<UUID, DeliveryRequest> deliveries = new LinkedHashMap<>();
    private final Map<ItemKey, Integer> protectedReserves = new LinkedHashMap<>();
    private final Map<ShortageType, ShortageSeverity> shortages = new EnumMap<>(ShortageType.class);

    public SettlementEconomyState(UUID settlementId) { this.settlementId = settlementId; }
    public UUID settlementId() { return settlementId; }
    public Collection<InventoryEndpoint> endpoints() { return java.util.List.copyOf(endpoints.values()); }
    public Collection<Workshop> workshops() { return java.util.List.copyOf(workshops.values()); }
    public Collection<ResourceReservation> reservations() { return java.util.List.copyOf(reservations.values()); }
    public Collection<ProductionRequest> productionRequests() { return java.util.List.copyOf(production.values()); }
    public Collection<DeliveryRequest> deliveryRequests() { return java.util.List.copyOf(deliveries.values()); }
    public Map<ItemKey, Integer> protectedReserves() { return Map.copyOf(protectedReserves); }
    public Map<ShortageType, ShortageSeverity> shortages() { return Map.copyOf(shortages); }
    public Optional<InventoryEndpoint> endpoint(InventoryEndpointId id) { return Optional.ofNullable(endpoints.get(id.value())); }
    public Optional<Workshop> workshop(UUID id) { return Optional.ofNullable(workshops.get(id)); }
    public Optional<ProductionRequest> production(UUID id) { return Optional.ofNullable(production.get(id)); }
    public Optional<DeliveryRequest> delivery(UUID id) { return Optional.ofNullable(deliveries.get(id)); }
    public Optional<ResourceReservation> reservation(UUID id) { return Optional.ofNullable(reservations.get(id)); }
    public void putEndpoint(InventoryEndpoint endpoint) { endpoints.put(endpoint.id().value(), endpoint); }
    public void removeEndpoint(InventoryEndpointId id) { endpoints.remove(id.value()); }
    public void putWorkshop(Workshop workshop) { workshops.put(workshop.id(), workshop); }
    public void putReservation(ResourceReservation reservation) { reservations.put(reservation.reservationId(), reservation); }
    public void removeReservation(UUID id) { reservations.remove(id); }
    public void putProduction(ProductionRequest request) { production.put(request.requestId(), request); }
    public void putDelivery(DeliveryRequest request) { deliveries.put(request.id(), request); }
    public void setProtected(ItemKey item, int amount) { if (amount <= 0) protectedReserves.remove(item); else protectedReserves.put(item, amount); }
    public void setShortage(ShortageType type, ShortageSeverity severity) { shortages.put(type, severity); }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag(); tag.putUUID("settlementId", settlementId);
        tag.put("endpoints", list(endpoints.values().stream().map(InventoryEndpoint::save).toList()));
        tag.put("workshops", list(workshops.values().stream().map(Workshop::save).toList()));
        tag.put("reservations", list(reservations.values().stream().map(ResourceReservation::save).toList()));
        tag.put("production", list(production.values().stream().map(ProductionRequest::save).toList()));
        tag.put("deliveries", list(deliveries.values().stream().map(DeliveryRequest::save).toList()));
        ListTag protectedTag = new ListTag(); protectedReserves.forEach((item, amount) -> { CompoundTag e = new CompoundTag(); e.put("item", item.save()); e.putInt("amount", amount); protectedTag.add(e); }); tag.put("protected", protectedTag);
        CompoundTag shortageTag = new CompoundTag(); shortages.forEach((type, severity) -> shortageTag.putString(type.name(), severity.name())); tag.put("shortages", shortageTag);
        return tag;
    }
    private static ListTag list(java.util.List<CompoundTag> entries) { ListTag list = new ListTag(); list.addAll(entries); return list; }
    public static SettlementEconomyState load(CompoundTag tag) {
        SettlementEconomyState state = new SettlementEconomyState(tag.getUUID("settlementId"));
        loadList(tag, "endpoints", e -> state.putEndpoint(InventoryEndpoint.load(e)));
        loadList(tag, "workshops", e -> state.putWorkshop(Workshop.load(e)));
        loadList(tag, "reservations", e -> state.putReservation(ResourceReservation.load(e)));
        loadList(tag, "production", e -> state.putProduction(ProductionRequest.load(e)));
        loadList(tag, "deliveries", e -> state.putDelivery(DeliveryRequest.load(e)));
        loadList(tag, "protected", e -> state.setProtected(ItemKey.load(e.getCompound("item")), e.getInt("amount")));
        CompoundTag shortages = tag.getCompound("shortages"); for (String key : shortages.getAllKeys()) state.setShortage(ShortageType.valueOf(key), ShortageSeverity.valueOf(shortages.getString(key)));
        return state;
    }
    private static void loadList(CompoundTag root, String key, java.util.function.Consumer<CompoundTag> consumer) {
        ListTag list = root.getList(key, Tag.TAG_COMPOUND); for (int i = 0; i < list.size(); i++) consumer.accept(list.getCompound(i));
    }
}
