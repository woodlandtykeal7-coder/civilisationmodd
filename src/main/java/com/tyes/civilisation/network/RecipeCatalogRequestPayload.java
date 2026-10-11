package com.tyes.civilisation.network;

import com.tyes.civilisation.CivilisationMod;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record RecipeCatalogRequestPayload(UUID citizenId) implements CustomPacketPayload {
    public static final Type<RecipeCatalogRequestPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(
            CivilisationMod.MOD_ID, "recipe_catalog_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RecipeCatalogRequestPayload> STREAM_CODEC = StreamCodec.of(
            (buf, value) -> buf.writeUUID(value.citizenId), buf -> new RecipeCatalogRequestPayload(buf.readUUID()));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
