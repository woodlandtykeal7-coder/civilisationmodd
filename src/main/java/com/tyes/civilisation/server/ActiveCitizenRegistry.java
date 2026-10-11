package com.tyes.civilisation.server;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** In-memory authority for loaded citizen bodies. Never persisted. */
public final class ActiveCitizenRegistry {
    public enum ClaimResult { CLAIMED, ALREADY_CLAIMED_BY_THIS_BODY, DUPLICATE_BODY }

    private final Map<UUID, UUID> bodiesByCitizen = new LinkedHashMap<>();

    public ClaimResult claim(UUID citizenId, UUID entityId) {
        UUID existing = bodiesByCitizen.putIfAbsent(citizenId, entityId);
        if (existing == null) return ClaimResult.CLAIMED;
        if (existing.equals(entityId)) return ClaimResult.ALREADY_CLAIMED_BY_THIS_BODY;
        return ClaimResult.DUPLICATE_BODY;
    }

    public boolean release(UUID citizenId, UUID entityId) {
        return bodiesByCitizen.remove(citizenId, entityId);
    }

    public Optional<UUID> bodyFor(UUID citizenId) {
        return Optional.ofNullable(bodiesByCitizen.get(citizenId));
    }

    public Map<UUID, UUID> snapshot() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(bodiesByCitizen));
    }

    public int size() {
        return bodiesByCitizen.size();
    }
}
