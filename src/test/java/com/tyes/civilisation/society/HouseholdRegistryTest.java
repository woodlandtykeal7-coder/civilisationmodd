package com.tyes.civilisation.society;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

final class HouseholdRegistryTest {
    @Test
    void membershipGuardiansDependantsAndHomePersist() {
        HouseholdRegistry registry = new HouseholdRegistry();
        UUID settlement = UUID.randomUUID();
        UUID adult = UUID.randomUUID();
        UUID child = UUID.randomUUID();
        UUID home = UUID.randomUUID();
        HouseholdRecord household = registry.create(settlement, 10L);
        household.addMember(adult, true, false, 11L);
        household.addMember(child, false, true, 12L);
        household.setHome(home, 13L);

        HouseholdRegistry restored = HouseholdRegistry.load(registry.save());
        HouseholdRecord loaded = restored.find(household.householdId()).orElseThrow();
        assertEquals("H-0001", loaded.readableId());
        assertEquals(home, loaded.homeBuildingId().orElseThrow());
        assertEquals(2, loaded.members().size());
        assertTrue(loaded.guardians().contains(adult));
        assertTrue(loaded.dependants().contains(child));
        assertEquals(HouseholdState.STABLE, loaded.state());
    }

    @Test
    void removingLastMemberDissolvesHouseholdWithoutDanglingRoles() {
        HouseholdRecord household = new HouseholdRecord(UUID.randomUUID(), "H-0001", UUID.randomUUID(), 0L);
        UUID member = UUID.randomUUID();
        household.addMember(member, true, false, 1L);
        household.removeMember(member, 2L);
        assertTrue(household.members().isEmpty());
        assertFalse(household.guardians().contains(member));
        assertEquals(HouseholdState.DISSOLVED, household.state());
    }
}
