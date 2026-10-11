package com.tyes.civilisation.economy.production;

import com.tyes.civilisation.economy.SettlementEconomyState;
import com.tyes.civilisation.economy.item.ItemKey;
import com.tyes.civilisation.persistence.EconomySavedData;
import java.util.Comparator;
import java.util.Optional;
import java.util.UUID;

public final class ProductionRequestManager {
    private final EconomySavedData data;
    public ProductionRequestManager(EconomySavedData data) { this.data = data; }
    public ProductionRequest create(UUID settlementId, ItemKey output, int quantity,
                                    ProductionPriority priority, ProductionReason reason) {
        ProductionRequest request = new ProductionRequest(UUID.randomUUID(), settlementId, output, quantity, priority, reason);
        data.forSettlement(settlementId).putProduction(request); data.setDirty(); return request;
    }
    public Optional<ProductionRequest> claimReady(UUID settlementId, UUID citizenId) {
        SettlementEconomyState state = data.forSettlement(settlementId);
        Optional<ProductionRequest> request = state.productionRequests().stream()
                .filter(r -> r.state() == ProductionState.READY && r.claimedBy().isEmpty())
                .min(Comparator.comparingInt(r -> r.priority().ordinal()));
        request.ifPresent(r -> { r.claim(citizenId); data.setDirty(); }); return request;
    }
    public void release(UUID settlementId, UUID requestId) {
        data.forSettlement(settlementId).production(requestId).ifPresent(ProductionRequest::releaseClaim); data.setDirty();
    }
}
