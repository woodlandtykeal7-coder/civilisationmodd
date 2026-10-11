package com.tyes.civilisation.society;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

public final class HouseholdRegistry {
    private final Map<UUID, HouseholdRecord> households = new LinkedHashMap<>();
    private int nextNumber = 1;

    public HouseholdRecord create(UUID settlementId, long now) {
        HouseholdRecord household = new HouseholdRecord(UUID.randomUUID(), "H-%04d".formatted(nextNumber++),
                settlementId, now);
        households.put(household.householdId(), household);
        return household;
    }

    public Optional<HouseholdRecord> find(UUID id) { return Optional.ofNullable(households.get(id)); }
    public Optional<HouseholdRecord> findByReadableId(String id) {
        return households.values().stream().filter(h -> h.readableId().equalsIgnoreCase(id)).findFirst();
    }
    public Collection<HouseholdRecord> all() { return List.copyOf(households.values()); }
    public Collection<HouseholdRecord> forSettlement(UUID settlementId) {
        return households.values().stream().filter(h -> h.settlementId().equals(settlementId)).toList();
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("next", nextNumber);
        ListTag list = new ListTag();
        households.values().forEach(h -> list.add(h.save()));
        tag.put("households", list);
        return tag;
    }

    public static HouseholdRegistry load(CompoundTag tag) {
        HouseholdRegistry registry = new HouseholdRegistry();
        ListTag list = tag.getList("households", Tag.TAG_COMPOUND);
        int highest = 0;
        for (int i = 0; i < list.size(); i++) {
            HouseholdRecord household = HouseholdRecord.load(list.getCompound(i));
            registry.households.put(household.householdId(), household);
            if (household.readableId().matches("(?i)H-\\d+")) {
                highest = Math.max(highest, Integer.parseInt(household.readableId().substring(2)));
            }
        }
        registry.nextNumber = Math.max(Math.max(1, tag.getInt("next")), highest + 1);
        return registry;
    }
}
