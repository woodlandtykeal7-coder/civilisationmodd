package com.tyes.civilisation.block;

import static org.junit.jupiter.api.Assertions.*;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

class WorkplaceAnchorIdentityTest {
    @Test void stableIdsRoundTripThroughNbt() {
        var identity = new WorkplaceAnchorIdentity(UUID.randomUUID(), UUID.randomUUID());
        CompoundTag tag = new CompoundTag();
        identity.write(tag);
        assertEquals(identity, WorkplaceAnchorIdentity.read(tag).orElseThrow());
        assertEquals(identity.workplaceId(), tag.getUUID("workplaceId"));
    }

    @Test void unboundOrIncompleteMarkersCannotAccidentallyInventWorkplaceIds() {
        assertTrue(WorkplaceAnchorIdentity.read(new CompoundTag()).isEmpty());
        CompoundTag partial = new CompoundTag();
        partial.putUUID("workplaceId", UUID.randomUUID());
        assertTrue(WorkplaceAnchorIdentity.read(partial).isEmpty());
    }

    @Test void differentWorkplaceIdentitiesAreNotInterchangeable() {
        UUID settlement = UUID.randomUUID();
        var left = new WorkplaceAnchorIdentity(UUID.randomUUID(), settlement);
        var right = new WorkplaceAnchorIdentity(UUID.randomUUID(), settlement);
        assertNotEquals(left, right);
    }
}
