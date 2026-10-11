package com.tyes.civilisation.economy.reservation;

import com.tyes.civilisation.economy.inventory.InventoryEndpointId;
import com.tyes.civilisation.economy.item.ItemKey;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;

public record ResourceReservation(UUID reservationId, UUID settlementId, UUID ownerId, ItemKey item,
                                  int quantity, InventoryEndpointId source, ReservationPurpose purpose,
                                  long createdTick, long expiryTick, long sourceRevision) {
    public ResourceReservation {
        if (quantity <= 0) throw new IllegalArgumentException("Reservation quantity must be positive");
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("reservationId", reservationId);
        tag.putUUID("settlementId", settlementId);
        tag.putUUID("ownerId", ownerId);
        tag.put("item", item.save());
        tag.putInt("quantity", quantity);
        tag.putUUID("source", source.value());
        tag.putString("purpose", purpose.name());
        tag.putLong("createdTick", createdTick);
        tag.putLong("expiryTick", expiryTick);
        tag.putLong("sourceRevision", sourceRevision);
        return tag;
    }

    public static ResourceReservation load(CompoundTag tag) {
        return new ResourceReservation(tag.getUUID("reservationId"), tag.getUUID("settlementId"),
                tag.getUUID("ownerId"), ItemKey.load(tag.getCompound("item")), tag.getInt("quantity"),
                new InventoryEndpointId(tag.getUUID("source")), ReservationPurpose.valueOf(tag.getString("purpose")),
                tag.getLong("createdTick"), tag.getLong("expiryTick"), tag.getLong("sourceRevision"));
    }
}
