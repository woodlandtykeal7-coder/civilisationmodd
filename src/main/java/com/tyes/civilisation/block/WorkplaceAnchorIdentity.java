package com.tyes.civilisation.block;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;

/**
 * Block entity pointer to a future server-authoritative WorkplaceRecord.
 * A floor marker never owns its citizen assignments, items, or work queue.
 */
public record WorkplaceAnchorIdentity(UUID workplaceId, UUID settlementId) {
    public WorkplaceAnchorIdentity {
        Objects.requireNonNull(workplaceId, "workplaceId");
        Objects.requireNonNull(settlementId, "settlementId");
    }

    public void write(CompoundTag tag) {
        tag.putUUID("workplaceId", workplaceId);
        tag.putUUID("settlementId", settlementId);
    }

    public static Optional<WorkplaceAnchorIdentity> read(CompoundTag tag) {
        if (!tag.hasUUID("workplaceId") || !tag.hasUUID("settlementId")) return Optional.empty();
        return Optional.of(new WorkplaceAnchorIdentity(tag.getUUID("workplaceId"), tag.getUUID("settlementId")));
    }
}
