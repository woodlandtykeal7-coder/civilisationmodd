package com.tyes.civilisation.economy.logistics;

import com.tyes.civilisation.economy.SettlementEconomyState;
import com.tyes.civilisation.economy.inventory.InventoryEndpointId;
import com.tyes.civilisation.economy.item.ItemKey;
import com.tyes.civilisation.persistence.EconomySavedData;
import java.util.Comparator;
import java.util.Optional;
import java.util.UUID;

public final class DeliveryRequestManager {
    private final EconomySavedData data;
    public DeliveryRequestManager(EconomySavedData data) { this.data = data; }
    public DeliveryRequest create(UUID settlementId, InventoryEndpointId source, InventoryEndpointId destination,
                                  ItemKey item, int quantity, DeliveryPriority priority, DeliveryPurpose purpose,
                                  UUID ownerId, UUID reservationId) {
        DeliveryRequest request = new DeliveryRequest(UUID.randomUUID(), settlementId, source, destination, item,
                quantity, priority, purpose, ownerId, reservationId);
        request.setState(DeliveryState.RESERVED); data.forSettlement(settlementId).putDelivery(request); data.setDirty(); return request;
    }
    public Optional<DeliveryRequest> claim(UUID settlementId, UUID citizenId) {
        SettlementEconomyState state = data.forSettlement(settlementId);
        Optional<DeliveryRequest> request = state.deliveryRequests().stream()
                .filter(r -> (r.state() == DeliveryState.REQUESTED || r.state() == DeliveryState.RESERVED)
                        && r.courierId().isEmpty())
                .min(Comparator.comparingInt(r -> r.priority().ordinal()));
        request.ifPresent(r -> { r.assign(citizenId); data.setDirty(); }); return request;
    }
    public void release(UUID settlementId, UUID deliveryId) {
        data.forSettlement(settlementId).delivery(deliveryId).ifPresent(DeliveryRequest::releaseCourier); data.setDirty();
    }
}
