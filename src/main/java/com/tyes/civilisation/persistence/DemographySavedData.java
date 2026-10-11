package com.tyes.civilisation.persistence;

import com.tyes.civilisation.CivilisationMod;
import com.tyes.civilisation.society.ApprenticeshipRecord;
import com.tyes.civilisation.society.HouseholdRegistry;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

public final class DemographySavedData extends SavedData {
    public static final int CURRENT_SCHEMA_VERSION = 1;
    private static final String DATA_NAME = CivilisationMod.MOD_ID + "_demography";
    private final HouseholdRegistry households;
    private final Map<UUID, ApprenticeshipRecord> apprenticeships = new LinkedHashMap<>();

    public DemographySavedData() { this(new HouseholdRegistry()); }
    private DemographySavedData(HouseholdRegistry households) { this.households = households; }

    public static DemographySavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new Factory<>(DemographySavedData::new, DemographySavedData::load), DATA_NAME);
    }

    public HouseholdRegistry households() { return households; }
    public Collection<ApprenticeshipRecord> apprenticeships() { return List.copyOf(apprenticeships.values()); }
    public Optional<ApprenticeshipRecord> apprenticeship(UUID id) {
        return Optional.ofNullable(apprenticeships.get(id));
    }
    public Optional<ApprenticeshipRecord> forApprentice(UUID citizenId) {
        return apprenticeships.values().stream().filter(a -> a.apprenticeId().equals(citizenId)
                && a.state() != com.tyes.civilisation.society.ApprenticeshipState.COMPLETE).findFirst();
    }
    public void add(ApprenticeshipRecord apprenticeship) {
        apprenticeships.put(apprenticeship.apprenticeshipId(), apprenticeship);
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("schema", CURRENT_SCHEMA_VERSION);
        tag.put("householdRegistry", households.save());
        ListTag list = new ListTag();
        apprenticeships.values().forEach(a -> list.add(a.save()));
        tag.put("apprenticeships", list);
        return tag;
    }

    private static DemographySavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        int schema = tag.getInt("schema");
        if (schema > CURRENT_SCHEMA_VERSION) throw new IllegalStateException("Unsupported demography schema " + schema);
        DemographySavedData data = new DemographySavedData(HouseholdRegistry.load(tag.getCompound("householdRegistry")));
        ListTag list = tag.getList("apprenticeships", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            ApprenticeshipRecord apprenticeship = ApprenticeshipRecord.load(list.getCompound(i));
            data.apprenticeships.put(apprenticeship.apprenticeshipId(), apprenticeship);
        }
        return data;
    }
}
