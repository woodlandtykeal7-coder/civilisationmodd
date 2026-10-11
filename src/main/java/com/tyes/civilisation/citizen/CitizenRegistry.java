package com.tyes.civilisation.citizen;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;

/** Owns the authoritative set of persistent people. */
public final class CitizenRegistry {
    private static final String[] NAMES = {
            "Mira", "Alden", "Elara", "Tomas", "Nora", "Rowan", "Lina", "Cedric",
            "Maeve", "Jonas", "Iris", "Bram", "Clara", "Owen", "Talia", "Finn"
    };
    private final Map<UUID, CitizenRecord> citizens = new LinkedHashMap<>();
    private int nextCitizenNumber = 1;

    public CitizenRecord create() {
        UUID id = UUID.randomUUID();
        String name = NAMES[Math.floorMod(nextCitizenNumber - 1, NAMES.length)];
        return create(id, name, id.getMostSignificantBits() ^ id.getLeastSignificantBits());
    }

    CitizenRecord create(UUID id, String displayName, long appearanceSeed) {
        if (citizens.containsKey(id)) {
            throw new IllegalStateException("Citizen already exists: " + id);
        }
        CitizenRecord record = new CitizenRecord(id, formatReadableId(nextCitizenNumber++), displayName, appearanceSeed);
        citizens.put(id, record);
        return record;
    }

    public Optional<CitizenRecord> find(UUID citizenId) {
        return Optional.ofNullable(citizens.get(citizenId));
    }

    public Optional<CitizenRecord> findByReadableId(String readableId) {
        return citizens.values().stream().filter(c -> c.readableId().equalsIgnoreCase(readableId)).findFirst();
    }

    public Collection<CitizenRecord> findByName(String name) {
        return citizens.values().stream().filter(c -> c.displayName().equalsIgnoreCase(name)).toList();
    }

    public int nextCitizenNumber() { return nextCitizenNumber; }

    public static String formatReadableId(int number) { return "C-%04d".formatted(number); }

    public Collection<CitizenRecord> all() {
        return Collections.unmodifiableCollection(citizens.values());
    }

    public int size() {
        return citizens.size();
    }

    public ListTag save(HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        citizens.values().forEach(record -> list.add(record.save(registries)));
        return list;
    }

    public ListTag save() { return save(RegistryAccess.EMPTY); }

    public static CitizenRegistry load(ListTag list, HolderLookup.Provider registries) {
        return load(list, registries, 1);
    }

    public static CitizenRegistry load(ListTag list, HolderLookup.Provider registries, int nextNumberHint) {
        CitizenRegistry registry = new CitizenRegistry();
        int generatedNumber = 1;
        int highestNumber = 0;
        for (int i = 0; i < list.size(); i++) {
            CompoundTag tag = list.getCompound(i);
            CitizenRecord record = CitizenRecord.load(tag, registries, formatReadableId(generatedNumber++));
            CitizenRecord previous = registry.citizens.putIfAbsent(record.citizenId(), record);
            if (previous != null) {
                throw new IllegalArgumentException("Duplicate citizen record: " + record.citizenId());
            }
            if (record.readableId().matches("(?i)C-\\d+")) {
                highestNumber = Math.max(highestNumber, Integer.parseInt(record.readableId().substring(2)));
            }
        }
        registry.nextCitizenNumber = Math.max(Math.max(1, nextNumberHint), highestNumber + 1);
        return registry;
    }

    public static CitizenRegistry load(ListTag list) { return load(list, RegistryAccess.EMPTY); }

    public static CitizenRegistry load(CompoundTag root, HolderLookup.Provider registries) {
        return load(root.getList("citizens", Tag.TAG_COMPOUND), registries, root.getInt("nextCitizenNumber"));
    }

    public static CitizenRegistry load(CompoundTag root) { return load(root, RegistryAccess.EMPTY); }
}
