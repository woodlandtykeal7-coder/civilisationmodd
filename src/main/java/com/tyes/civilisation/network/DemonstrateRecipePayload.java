package com.tyes.civilisation.network;

import com.tyes.civilisation.CivilisationMod;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record DemonstrateRecipePayload(UUID citizenId, ResourceLocation recipeId) implements CustomPacketPayload {
    public static final Type<DemonstrateRecipePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(
            CivilisationMod.MOD_ID, "demonstrate_recipe"));
    public static final StreamCodec<RegistryFriendlyByteBuf, DemonstrateRecipePayload> STREAM_CODEC = StreamCodec.of(
            (buf, value) -> { buf.writeUUID(value.citizenId); buf.writeResourceLocation(value.recipeId); },
            buf -> new DemonstrateRecipePayload(buf.readUUID(), buf.readResourceLocation()));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
