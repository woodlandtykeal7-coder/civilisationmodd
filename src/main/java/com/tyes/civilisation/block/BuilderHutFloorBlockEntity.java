package com.tyes.civilisation.block;

import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Persistent floor anchor only. The future WorkplaceSavedData will own the
 * authoritative worker list and enforce settlement membership and permissions.
 */
public final class BuilderHutFloorBlockEntity extends BlockEntity {
    private WorkplaceAnchorIdentity identity;

    public BuilderHutFloorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.BUILDER_HUT_FLOOR.get(), pos, state);
    }

    public Optional<WorkplaceAnchorIdentity> identity() {
        return Optional.ofNullable(identity);
    }

    /**
     * Future server workplace registry may bind an unbound anchor exactly once.
     * Re-applying the same IDs is idempotent; conflicting IDs cannot overwrite.
     */
    public boolean bind(UUID workplaceId, UUID settlementId) {
        if (level == null || level.isClientSide) return false;
        WorkplaceAnchorIdentity proposed = new WorkplaceAnchorIdentity(workplaceId, settlementId);
        if (identity != null) return identity.equals(proposed);
        identity = proposed;
        setChanged();
        return true;
    }

    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (identity != null) identity.write(tag);
    }

    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        identity = WorkplaceAnchorIdentity.read(tag).orElse(null);
    }
}
