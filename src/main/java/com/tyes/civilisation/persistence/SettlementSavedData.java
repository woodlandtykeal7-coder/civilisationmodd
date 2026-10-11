package com.tyes.civilisation.persistence;

import com.tyes.civilisation.CivilisationMod;
import com.tyes.civilisation.settlement.SettlementRegistry;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

public final class SettlementSavedData extends SavedData {
    public static final int CURRENT_SCHEMA_VERSION = 1;
    private static final String DATA_NAME = CivilisationMod.MOD_ID + "_settlements";
    private final SettlementRegistry registry;

    public SettlementSavedData() { this(new SettlementRegistry()); }
    private SettlementSavedData(SettlementRegistry registry) { this.registry = registry; }
    public SettlementRegistry registry() { return registry; }
    public static SettlementSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(factory(), DATA_NAME);
    }
    public static Factory<SettlementSavedData> factory() {
        return new Factory<>(SettlementSavedData::new, SettlementSavedData::load);
    }
    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("schemaVersion", CURRENT_SCHEMA_VERSION);
        tag.putInt("nextSettlementNumber", registry.nextSettlementNumber());
        tag.put("settlements", registry.save());
        return tag;
    }
    private static SettlementSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        int version = tag.contains("schemaVersion") ? tag.getInt("schemaVersion") : 0;
        if (version > CURRENT_SCHEMA_VERSION) throw new IllegalStateException("Unsupported settlement schema " + version);
        SettlementSavedData data = new SettlementSavedData(SettlementRegistry.load(tag));
        if (version != CURRENT_SCHEMA_VERSION) data.setDirty();
        return data;
    }
}
