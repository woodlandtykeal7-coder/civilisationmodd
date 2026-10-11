package com.tyes.civilisation.network;

import com.tyes.civilisation.CivilisationMod;
import java.util.Objects;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record DashboardSnapshotPayload(CompoundTag data) implements CustomPacketPayload {
    public static final Type<DashboardSnapshotPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CivilisationMod.MOD_ID, "dashboard_snapshot"));
    public static final StreamCodec<RegistryFriendlyByteBuf, DashboardSnapshotPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> buf.writeNbt(payload.data),
            buf -> new DashboardSnapshotPayload(Objects.requireNonNullElseGet(buf.readNbt(), CompoundTag::new)));

    public DashboardSnapshotPayload { data = data.copy(); }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
