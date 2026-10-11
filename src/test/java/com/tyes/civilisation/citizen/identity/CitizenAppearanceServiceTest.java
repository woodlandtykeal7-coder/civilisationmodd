package com.tyes.civilisation.citizen.identity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CitizenAppearanceServiceTest {
    @Test
    void profileNbtRoundTripKeepsEveryFutureLayerIndex() {
        CitizenAppearanceProfile profile = new CitizenAppearanceProfile(
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("civilisation", "civilian_07"),
                3, 4, 1, 2, 8);
        assertEquals(profile, CitizenAppearanceProfile.load(profile.save()));
    }

    @Test
    void initialGenerationIsDeterministicAndUsesVisibleVariety() {
        Set<CitizenAppearanceProfile> profiles = new HashSet<>();
        for (int i = 0; i < 64; i++) {
            UUID id = new UUID(0x1234ABCDL + i, 0x987654321L * (i + 1L));
            CitizenAppearanceProfile first = CitizenAppearanceService.generate(id, i * 31L);
            assertEquals(first, CitizenAppearanceService.generate(id, i * 31L));
            profiles.add(first);
        }
        assertTrue(profiles.size() >= 8, "expected at least eight profiles across a representative colony");
        assertEquals(9, CitizenAppearanceService.variantCount());
    }
}
