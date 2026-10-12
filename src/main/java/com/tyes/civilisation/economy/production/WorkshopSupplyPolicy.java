package com.tyes.civilisation.economy.production;

import com.tyes.civilisation.economy.inventory.InventoryEndpointId;
import com.tyes.civilisation.economy.item.ItemKey;
import com.tyes.civilisation.economy.logistics.DeliveryPurpose;
import com.tyes.civilisation.economy.logistics.DeliveryRequest;
import com.tyes.civilisation.economy.logistics.DeliveryState;

/** Pure, conservative workshop input-delivery accounting. Real cargo is never discarded. */
public final class WorkshopSupplyPolicy {
    private WorkshopSupplyPolicy() {}

    public static boolean matchesInput(DeliveryRequest delivery, ProductionRequest request,
                                       InventoryEndpointId destination, ItemKey ingredient) {
        return delivery.ownerId().equals(request.requestId())
                && delivery.purpose() == DeliveryPurpose.WORKSHOP_INPUT
                && delivery.destination().equals(destination)
                && delivery.item().itemId().equals(ingredient.itemId())
                && delivery.state() != DeliveryState.COMPLETE
                && delivery.state() != DeliveryState.CANCELLED;
    }

    /** A partially loaded courier can only promise the items it physically carries. */
    public static int incoming(DeliveryRequest delivery) {
        return delivery.cargoQuantity() > 0 ? delivery.cargoQuantity() : delivery.quantity();
    }

    /** Never cancel a delivery while an NPC owns actual physical cargo. */
    public static boolean canCancelUnloaded(DeliveryRequest delivery) {
        return delivery.cargoQuantity() == 0
                && delivery.state() != DeliveryState.COMPLETE
                && delivery.state() != DeliveryState.CANCELLED;
    }

    /**
     * A now-oversized or orphaned reservation may be retired only before pickup.
     * Existing citizen cargo always wins over the size of the next craft batch.
     */
    public static boolean shouldRetire(DeliveryRequest delivery, boolean reservationExists,
                                       int incoming, int stillNeeded) {
        return canCancelUnloaded(delivery)
                && (!reservationExists || delivery.state() == DeliveryState.BLOCKED || incoming > stillNeeded);
    }
}
