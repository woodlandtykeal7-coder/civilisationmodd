package com.tyes.civilisation.settlement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tyes.civilisation.citizen.AssignedLocation;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class SettlementRegistryTest {
    @Test
    void settlementIdentityMembershipAndStoragePersist() {
        SettlementRegistry registry = new SettlementRegistry();
        AssignedLocation centre = new AssignedLocation(ResourceLocation.withDefaultNamespace("overworld"), BlockPos.ZERO);
        SettlementState state = registry.create("Oak Camp", 123L, centre);
        UUID member = UUID.randomUUID();
        state.addMember(member);
        state.addStorage(new AssignedLocation(centre.dimension(), new BlockPos(4, 0, 0)));

        CompoundTag root = new CompoundTag();
        root.put("settlements", registry.save());
        root.putInt("nextSettlementNumber", registry.nextSettlementNumber());
        SettlementRegistry restored = SettlementRegistry.load(root);
        SettlementState loaded = restored.find(state.settlementId()).orElseThrow();
        assertEquals("S-0001", loaded.readableId());
        assertEquals("Oak Camp", loaded.name());
        assertTrue(loaded.members().contains(member));
        assertEquals(1, loaded.storageLocations().size());
        assertEquals("S-0002", restored.create("Next", 124L, centre).readableId());
    }
}
