package com.tyes.civilisation.citizen;

import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

public record AssignedLocation(ResourceLocation dimension, BlockPos pos) {
    public static AssignedLocation at(ServerLevel level, BlockPos pos) {
        return new AssignedLocation(level.dimension().location(), pos.immutable());
    }

    public boolean isIn(ServerLevel level) {
        return level.dimension().location().equals(dimension);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("dimension", dimension.toString());
        tag.putInt("x", pos.getX());
        tag.putInt("y", pos.getY());
        tag.putInt("z", pos.getZ());
        return tag;
    }

    public static Optional<AssignedLocation> load(CompoundTag tag) {
        ResourceLocation dimension = ResourceLocation.tryParse(tag.getString("dimension"));
        if (dimension == null) return Optional.empty();
        return Optional.of(new AssignedLocation(dimension,
                new BlockPos(tag.getInt("x"), tag.getInt("y"), tag.getInt("z"))));
    }
}
