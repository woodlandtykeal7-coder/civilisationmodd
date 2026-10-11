package com.tyes.civilisation.settlement;

import com.tyes.civilisation.citizen.AssignedLocation;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

public final class SettlementRegistry {
    private final Map<UUID, SettlementState> settlements = new LinkedHashMap<>();
    private int nextSettlementNumber = 1;

    public SettlementState create(String name, long foundingTime, AssignedLocation centre) {
        UUID id = UUID.randomUUID();
        SettlementState state = new SettlementState(id, "S-%04d".formatted(nextSettlementNumber++), name,
                foundingTime, centre, SettlementStage.CAMP);
        settlements.put(id, state);
        return state;
    }

    public Optional<SettlementState> find(UUID id) { return Optional.ofNullable(settlements.get(id)); }
    public Optional<SettlementState> findByReadableId(String id) {
        return settlements.values().stream().filter(s -> s.readableId().equalsIgnoreCase(id)).findFirst();
    }
    public Collection<SettlementState> findByName(String name) {
        return settlements.values().stream().filter(s -> s.name().equalsIgnoreCase(name)).toList();
    }
    public Collection<SettlementState> all() { return Collections.unmodifiableCollection(settlements.values()); }
    public int size() { return settlements.size(); }
    public int nextSettlementNumber() { return nextSettlementNumber; }

    public ListTag save() {
        ListTag list = new ListTag();
        settlements.values().forEach(s -> list.add(s.save()));
        return list;
    }

    public static SettlementRegistry load(CompoundTag root) {
        SettlementRegistry registry = new SettlementRegistry();
        ListTag list = root.getList("settlements", Tag.TAG_COMPOUND);
        int highest = 0;
        for (int i = 0; i < list.size(); i++) {
            SettlementState state = SettlementState.load(list.getCompound(i));
            if (registry.settlements.putIfAbsent(state.settlementId(), state) != null) {
                throw new IllegalArgumentException("Duplicate settlement " + state.settlementId());
            }
            if (state.readableId().matches("(?i)S-\\d+")) highest = Math.max(highest, Integer.parseInt(state.readableId().substring(2)));
        }
        registry.nextSettlementNumber = Math.max(Math.max(1, root.getInt("nextSettlementNumber")), highest + 1);
        return registry;
    }
}
