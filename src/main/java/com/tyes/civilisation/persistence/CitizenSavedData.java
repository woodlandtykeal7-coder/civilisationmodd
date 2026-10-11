package com.tyes.civilisation.persistence;

import com.tyes.civilisation.CivilisationMod;
import com.tyes.civilisation.citizen.CitizenRegistry;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import com.tyes.civilisation.citizen.identity.CitizenAppearanceService;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

public final class CitizenSavedData extends SavedData {
    public static final int CURRENT_SCHEMA_VERSION = 6;
    private static final String DATA_NAME = CivilisationMod.MOD_ID + "_citizens";
    private final CitizenRegistry registry;

    private CitizenSavedData(CitizenRegistry registry) {
        this.registry = registry;
    }

    public CitizenSavedData() {
        this(new CitizenRegistry());
    }

    public static CitizenSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(factory(), DATA_NAME);
    }

    public static Factory<CitizenSavedData> factory() {
        return new Factory<>(CitizenSavedData::new, CitizenSavedData::load);
    }

    public CitizenRegistry registry() {
        return registry;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("schemaVersion", CURRENT_SCHEMA_VERSION);
        tag.put("citizens", registry.save(registries));
        tag.putInt("nextCitizenNumber", registry.nextCitizenNumber());
        return tag;
    }

    private static CitizenSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        int version = tag.contains("schemaVersion") ? tag.getInt("schemaVersion") : 0;
        CompoundTag migrated = migrate(tag, version);
        CitizenSavedData data = new CitizenSavedData(CitizenRegistry.load(migrated, registries));
        if (version != CURRENT_SCHEMA_VERSION || containsMissingAppearance(tag)) {
            data.setDirty();
        }
        CivilisationMod.LOGGER.info("Loaded {} citizens using schema {}", data.registry.size(), CURRENT_SCHEMA_VERSION);
        return data;
    }

    private static CompoundTag migrate(CompoundTag source, int fromVersion) {
        if (fromVersion > CURRENT_SCHEMA_VERSION) {
            throw new IllegalStateException("Citizen data schema " + fromVersion
                    + " is newer than supported schema " + CURRENT_SCHEMA_VERSION);
        }
        CompoundTag migrated = source.copy();
        if (fromVersion == 0) {
            // Schema 0 was the pre-versioned Phase 0 development format. Its citizen list is compatible.
            migrated.putInt("schemaVersion", 1);
            CivilisationMod.LOGGER.info("Migrated citizen data schema 0 -> 1");
        }
        if (fromVersion <= 1) {
            // Phase 1 fields are optional and load with safe defaults on each record.
            migrated.putInt("schemaVersion", 2);
            CivilisationMod.LOGGER.info("Migrated citizen data schema {} -> 2", fromVersion);
        }
        if (fromVersion <= 2) {
            // Schema 3 adds stable human-readable citizen IDs and a monotonic, never-reused counter.
            if (!migrated.contains("nextCitizenNumber")) migrated.putInt("nextCitizenNumber", 1);
            migrated.putInt("schemaVersion", 3);
            CivilisationMod.LOGGER.info("Migrated citizen data schema {} -> 3", fromVersion);
        }
        if (fromVersion <= 3) {
            // Schema 4 persists a generated appearance exactly once for every existing person.
            ListTag citizens = migrated.getList("citizens", Tag.TAG_COMPOUND);
            for (int i = 0; i < citizens.size(); i++) {
                CompoundTag citizen = citizens.getCompound(i);
                if (!citizen.contains("appearance", Tag.TAG_COMPOUND) && citizen.hasUUID("citizenId")) {
                    citizen.put("appearance", CitizenAppearanceService.generate(citizen.getUUID("citizenId"),
                            citizen.getLong("appearanceSeed")).save());
                }
            }
            migrated.putInt("schemaVersion", 4);
            CivilisationMod.LOGGER.info("Migrated citizen data schema {} -> 4 with persistent appearances", fromVersion);
        }
        if (fromVersion <= 4) {
            // Schema 5 adds bounded life-stage, household reference, education and career development.
            migrated.putInt("schemaVersion", 5);
            CivilisationMod.LOGGER.info("Migrated citizen data schema {} -> 5 with demographic profiles", fromVersion);
        }
        if (fromVersion <= 5) {
            // Schema 6 adds bounded personal knowledge and meaningful history. Both are optional
            // on load, so 0.5.1 worlds migrate without inventing knowledge.
            migrated.putInt("schemaVersion", 6);
            CivilisationMod.LOGGER.info("Migrated citizen data schema {} -> 6 with personal knowledge", fromVersion);
        }
        return migrated;
    }

    private static boolean containsMissingAppearance(CompoundTag source) {
        ListTag citizens = source.getList("citizens", Tag.TAG_COMPOUND);
        for (int i = 0; i < citizens.size(); i++) {
            if (!citizens.getCompound(i).contains("appearance", Tag.TAG_COMPOUND)) return true;
        }
        return false;
    }
}
