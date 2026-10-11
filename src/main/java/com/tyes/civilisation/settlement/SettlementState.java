package com.tyes.civilisation.settlement;

import com.tyes.civilisation.citizen.AssignedLocation;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

/** Persistent authority for one settlement and its membership. */
public final class SettlementState {
    private final UUID settlementId;
    private final String readableId;
    private final String name;
    private final long foundingTime;
    private final AssignedLocation centre;
    private final Set<UUID> members = new LinkedHashSet<>();
    private final Set<AssignedLocation> storageLocations = new LinkedHashSet<>();
    private SettlementStage stage;

    public SettlementState(UUID settlementId, String readableId, String name, long foundingTime,
                           AssignedLocation centre, SettlementStage stage) {
        this.settlementId = settlementId;
        this.readableId = readableId;
        this.name = name;
        this.foundingTime = foundingTime;
        this.centre = centre;
        this.stage = stage;
    }

    public UUID settlementId() { return settlementId; }
    public String readableId() { return readableId; }
    public String name() { return name; }
    public long foundingTime() { return foundingTime; }
    public AssignedLocation centre() { return centre; }
    public SettlementStage stage() { return stage; }
    public Collection<UUID> members() { return Set.copyOf(members); }
    public Collection<AssignedLocation> storageLocations() { return Set.copyOf(storageLocations); }
    public boolean addMember(UUID citizenId) { return members.add(citizenId); }
    public boolean removeMember(UUID citizenId) { return members.remove(citizenId); }
    public boolean addStorage(AssignedLocation location) { return storageLocations.add(location); }
    public boolean removeStorage(AssignedLocation location) { return storageLocations.remove(location); }
    public Optional<AssignedLocation> primaryStorage() { return storageLocations.stream().findFirst(); }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("settlementId", settlementId);
        tag.putString("readableId", readableId);
        tag.putString("name", name);
        tag.putLong("foundingTime", foundingTime);
        tag.put("centre", centre.save());
        tag.putString("stage", stage.name());
        ListTag memberTags = new ListTag();
        members.forEach(id -> memberTags.add(StringTag.valueOf(id.toString())));
        tag.put("members", memberTags);
        ListTag storages = new ListTag();
        storageLocations.forEach(location -> storages.add(location.save()));
        tag.put("storages", storages);
        return tag;
    }

    public static SettlementState load(CompoundTag tag) {
        AssignedLocation centre = AssignedLocation.load(tag.getCompound("centre"))
                .orElseThrow(() -> new IllegalArgumentException("Settlement missing centre"));
        SettlementState state = new SettlementState(tag.getUUID("settlementId"), tag.getString("readableId"),
                tag.getString("name"), tag.getLong("foundingTime"), centre,
                SettlementStage.parse(tag.getString("stage")));
        ListTag members = tag.getList("members", Tag.TAG_STRING);
        for (int i = 0; i < members.size(); i++) state.members.add(UUID.fromString(members.getString(i)));
        ListTag storages = tag.getList("storages", Tag.TAG_COMPOUND);
        for (int i = 0; i < storages.size(); i++) AssignedLocation.load(storages.getCompound(i)).ifPresent(state.storageLocations::add);
        return state;
    }
}
