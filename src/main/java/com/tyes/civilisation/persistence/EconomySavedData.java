package com.tyes.civilisation.persistence;

import com.tyes.civilisation.CivilisationMod;
import com.tyes.civilisation.economy.SettlementEconomyState;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

public final class EconomySavedData extends SavedData {
    public static final int CURRENT_SCHEMA_VERSION = 1;
    private static final String DATA_NAME = CivilisationMod.MOD_ID + "_economy";
    private final Map<UUID, SettlementEconomyState> settlements = new LinkedHashMap<>();
    public static EconomySavedData get(MinecraftServer server) { return server.overworld().getDataStorage().computeIfAbsent(factory(), DATA_NAME); }
    public static Factory<EconomySavedData> factory() { return new Factory<>(EconomySavedData::new, EconomySavedData::load); }
    public SettlementEconomyState forSettlement(UUID id) { return settlements.computeIfAbsent(id, SettlementEconomyState::new); }
    public Collection<SettlementEconomyState> all() { return java.util.List.copyOf(settlements.values()); }
    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("economySchemaVersion", CURRENT_SCHEMA_VERSION); ListTag list = new ListTag(); settlements.values().forEach(s -> list.add(s.save())); tag.put("settlements", list); return tag;
    }
    private static EconomySavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        int version = tag.contains("economySchemaVersion") ? tag.getInt("economySchemaVersion") : 0;
        if (version > CURRENT_SCHEMA_VERSION) throw new IllegalStateException("Unsupported economy schema " + version);
        EconomySavedData data = new EconomySavedData(); ListTag list = tag.getList("settlements", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) { SettlementEconomyState state = SettlementEconomyState.load(list.getCompound(i)); data.settlements.put(state.settlementId(), state); }
        if (version != CURRENT_SCHEMA_VERSION) data.setDirty();
        CivilisationMod.LOGGER.info("Loaded economy schema {} for {} settlements", CURRENT_SCHEMA_VERSION, data.settlements.size()); return data;
    }
}
