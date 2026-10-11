package com.tyes.civilisation.citizen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

class CitizenRegistryTest {
    @Test
    void saveAndLoadKeepsTheSameCitizenIdentity() {
        CitizenRegistry registry = new CitizenRegistry();
        UUID id = UUID.fromString("3c43ed9c-e7d2-4c07-a426-419a0f1b8422");
        registry.create(id, "Ada", 42L);

        CompoundTag root = new CompoundTag();
        root.put("citizens", registry.save());
        CitizenRegistry restored = CitizenRegistry.load(root);

        assertEquals(1, restored.size());
        CitizenRecord citizen = restored.find(id).orElseThrow();
        assertEquals(id, citizen.citizenId());
        assertEquals("Ada", citizen.displayName());
        assertEquals("C-0001", citizen.readableId());
        assertEquals(42L, citizen.appearanceSeed());
        assertEquals(registry.find(id).orElseThrow().appearance(), citizen.appearance());
        assertEquals(RuntimePresence.PENDING_MATERIALIZATION, citizen.presence());
    }

    @Test
    void readableIdsAreMonotonicAndNeverReused() {
        CitizenRegistry registry = new CitizenRegistry();
        CitizenRecord first = registry.create();
        CitizenRecord second = registry.create();
        assertEquals("C-0001", first.readableId());
        assertEquals("C-0002", second.readableId());

        CompoundTag root = new CompoundTag();
        root.put("citizens", registry.save());
        root.putInt("nextCitizenNumber", registry.nextCitizenNumber());
        CitizenRegistry restored = CitizenRegistry.load(root);
        assertEquals("C-0003", restored.create().readableId());
    }

    @Test
    void registryRejectsDuplicatePersistentPeople() {
        CitizenRegistry registry = new CitizenRegistry();
        UUID id = UUID.randomUUID();
        registry.create(id, "First", 1L);

        assertThrows(IllegalStateException.class, () -> registry.create(id, "Duplicate", 2L));
        assertEquals(1, registry.size());
        assertTrue(registry.find(id).isPresent());
    }
}
