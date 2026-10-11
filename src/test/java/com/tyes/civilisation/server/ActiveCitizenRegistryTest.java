package com.tyes.civilisation.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class ActiveCitizenRegistryTest {
    @Test
    void oneCitizenCanHaveAtMostOneActiveBody() {
        ActiveCitizenRegistry registry = new ActiveCitizenRegistry();
        UUID citizen = UUID.randomUUID();
        UUID firstBody = UUID.randomUUID();
        UUID duplicateBody = UUID.randomUUID();

        assertEquals(ActiveCitizenRegistry.ClaimResult.CLAIMED, registry.claim(citizen, firstBody));
        assertEquals(ActiveCitizenRegistry.ClaimResult.ALREADY_CLAIMED_BY_THIS_BODY,
                registry.claim(citizen, firstBody));
        assertEquals(ActiveCitizenRegistry.ClaimResult.DUPLICATE_BODY,
                registry.claim(citizen, duplicateBody));
        assertEquals(firstBody, registry.bodyFor(citizen).orElseThrow());
        assertEquals(1, registry.size());
    }

    @Test
    void onlyTheClaimingBodyCanReleaseTheCitizen() {
        ActiveCitizenRegistry registry = new ActiveCitizenRegistry();
        UUID citizen = UUID.randomUUID();
        UUID body = UUID.randomUUID();
        registry.claim(citizen, body);

        assertFalse(registry.release(citizen, UUID.randomUUID()));
        assertTrue(registry.bodyFor(citizen).isPresent());
        assertTrue(registry.release(citizen, body));
        assertTrue(registry.bodyFor(citizen).isEmpty());
    }
}
