package com.tyes.civilisation.network;

import com.tyes.civilisation.CivilisationMod;
import com.tyes.civilisation.dashboard.DashboardView;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record DashboardRequestPayload(DashboardView view, UUID target) implements CustomPacketPayload {
    public static final Type<DashboardRequestPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CivilisationMod.MOD_ID, "dashboard_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, DashboardRequestPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> { buf.writeEnum(payload.view); buf.writeUUID(payload.target); },
            buf -> new DashboardRequestPayload(buf.readEnum(DashboardView.class), buf.readUUID()));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
