package com.tyes.civilisation.economy.logistics;

import com.tyes.civilisation.economy.inventory.InventoryEndpointId;
import com.tyes.civilisation.economy.item.ItemKey;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;

public final class DeliveryRequest {
    private final UUID id;
    private final UUID settlementId;
    private final InventoryEndpointId source;
    private InventoryEndpointId destination;
    private final ItemKey item;
    private final int quantity;
    private DeliveryPriority priority;
    private final DeliveryPurpose purpose;
    private final UUID ownerId;
    private final UUID reservationId;
    private DeliveryState state = DeliveryState.REQUESTED;
    private int cargoQuantity;
    private UUID courierId;

    public DeliveryRequest(UUID id, UUID settlementId, InventoryEndpointId source, InventoryEndpointId destination,
                           ItemKey item, int quantity, DeliveryPriority priority, DeliveryPurpose purpose, UUID ownerId,
                           UUID reservationId) {
        if (quantity <= 0) throw new IllegalArgumentException("Delivery quantity must be positive");
        this.id = id; this.settlementId = settlementId; this.source = source; this.destination = destination;
        this.item = item; this.quantity = quantity; this.priority = priority; this.purpose = purpose;
        this.ownerId = ownerId; this.reservationId = reservationId;
    }
    public UUID id() { return id; } public UUID settlementId() { return settlementId; }
    public InventoryEndpointId source() { return source; } public InventoryEndpointId destination() { return destination; }
    public ItemKey item() { return item; } public int quantity() { return quantity; }
    public DeliveryPriority priority() { return priority; } public DeliveryPurpose purpose() { return purpose; }
    public UUID ownerId() { return ownerId; } public DeliveryState state() { return state; }
    public UUID reservationId() { return reservationId; }
    public int cargoQuantity() { return cargoQuantity; } public Optional<UUID> courierId() { return Optional.ofNullable(courierId); }
    public void assign(UUID courier) { courierId = courier; state = DeliveryState.ASSIGNED; }
    public void promote(DeliveryPriority next) { if (next.ordinal() < priority.ordinal()) priority = next; }
    public void setState(DeliveryState state) { this.state = state; }
    public void loaded(int amount) { cargoQuantity += amount; state = DeliveryState.TRAVELLING_TO_DESTINATION; }
    public void unloaded(int amount) { cargoQuantity = Math.max(0, cargoQuantity - amount); if (cargoQuantity == 0) { state = DeliveryState.COMPLETE; courierId = null; } }
    public void reroute(InventoryEndpointId endpoint) { destination = endpoint; state = DeliveryState.TRAVELLING_TO_DESTINATION; }
    public void releaseCourier() { courierId = null; if (state != DeliveryState.COMPLETE && state != DeliveryState.CANCELLED) state = DeliveryState.REQUESTED; }
    public void courierLost() { courierId = null; if (state != DeliveryState.COMPLETE && state != DeliveryState.CANCELLED) state = DeliveryState.BLOCKED; }
    public void cancel() { courierId = null; state = DeliveryState.CANCELLED; }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag(); tag.putUUID("id", id); tag.putUUID("settlementId", settlementId);
        tag.putUUID("source", source.value()); tag.putUUID("destination", destination.value()); tag.put("item", item.save());
        tag.putInt("quantity", quantity); tag.putString("priority", priority.name()); tag.putString("purpose", purpose.name());
        tag.putUUID("ownerId", ownerId); tag.putUUID("reservationId", reservationId);
        tag.putString("state", state.name()); tag.putInt("cargo", cargoQuantity);
        if (courierId != null) tag.putUUID("courier", courierId); return tag;
    }
    public static DeliveryRequest load(CompoundTag tag) {
        DeliveryRequest request = new DeliveryRequest(tag.getUUID("id"), tag.getUUID("settlementId"),
                new InventoryEndpointId(tag.getUUID("source")), new InventoryEndpointId(tag.getUUID("destination")),
                ItemKey.load(tag.getCompound("item")), tag.getInt("quantity"), DeliveryPriority.valueOf(tag.getString("priority")),
                DeliveryPurpose.valueOf(tag.getString("purpose")), tag.getUUID("ownerId"),
                tag.hasUUID("reservationId") ? tag.getUUID("reservationId") : UUID.randomUUID());
        request.state = DeliveryState.valueOf(tag.getString("state")); request.cargoQuantity = tag.getInt("cargo");
        request.courierId = tag.hasUUID("courier") ? tag.getUUID("courier") : null; return request;
    }
}
