package com.tyes.civilisation.network;

import com.tyes.civilisation.CivilisationMod;
import java.util.Objects;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record RecipeCatalogPayload(CompoundTag data) implements CustomPacketPayload {
    public static final Type<RecipeCatalogPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(
            CivilisationMod.MOD_ID, "recipe_catalog"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RecipeCatalogPayload> STREAM_CODEC = StreamCodec.of(
            (buf, value) -> buf.writeNbt(value.data),
            buf -> new RecipeCatalogPayload(Objects.requireNonNullElseGet(buf.readNbt(), CompoundTag::new)));
    public RecipeCatalogPayload { data = data.copy(); }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
