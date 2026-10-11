package com.tyes.civilisation.society;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

/** Membership authority for one bounded family/guardian unit. */
public final class HouseholdRecord {
    private final UUID householdId;
    private final String readableId;
    private final UUID settlementId;
    private final Set<UUID> members = new LinkedHashSet<>();
    private final Set<UUID> guardians = new LinkedHashSet<>();
    private final Set<UUID> dependants = new LinkedHashSet<>();
    @Nullable private UUID homeBuildingId;
    private HouseholdState state = HouseholdState.NEEDS_HOME;
    private long lastChangedTick;
    private long lastGrowthTick;

    public HouseholdRecord(UUID householdId, String readableId, UUID settlementId, long now) {
        this.householdId = householdId;
        this.readableId = readableId;
        this.settlementId = settlementId;
        this.lastChangedTick = now;
        this.lastGrowthTick = now;
    }

    public UUID householdId() { return householdId; }
    public String readableId() { return readableId; }
    public UUID settlementId() { return settlementId; }
    public Collection<UUID> members() { return Set.copyOf(members); }
    public Collection<UUID> guardians() { return Set.copyOf(guardians); }
    public Collection<UUID> dependants() { return Set.copyOf(dependants); }
    public Optional<UUID> homeBuildingId() { return Optional.ofNullable(homeBuildingId); }
    public HouseholdState state() { return state; }
    public long lastChangedTick() { return lastChangedTick; }
    public long lastGrowthTick() { return lastGrowthTick; }

    public void addMember(UUID citizenId, boolean guardian, boolean dependant, long now) {
        members.add(citizenId);
        if (guardian) guardians.add(citizenId); else guardians.remove(citizenId);
        if (dependant) dependants.add(citizenId); else dependants.remove(citizenId);
        lastChangedTick = now;
    }

    public void removeMember(UUID citizenId, long now) {
        members.remove(citizenId);
        guardians.remove(citizenId);
        dependants.remove(citizenId);
        lastChangedTick = now;
        if (members.isEmpty()) state = HouseholdState.DISSOLVED;
    }

    public void setHome(@Nullable UUID buildingId, long now) {
        homeBuildingId = buildingId;
        state = buildingId == null ? HouseholdState.NEEDS_HOME : HouseholdState.STABLE;
        lastChangedTick = now;
    }

    public void setState(HouseholdState state) { this.state = state; }
    public void markGrowth(long now) { lastGrowthTick = now; }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("id", householdId);
        tag.putString("readable", readableId);
        tag.putUUID("settlement", settlementId);
        tag.put("members", saveIds(members));
        tag.put("guardians", saveIds(guardians));
        tag.put("dependants", saveIds(dependants));
        if (homeBuildingId != null) tag.putUUID("home", homeBuildingId);
        tag.putString("state", state.name());
        tag.putLong("lastChanged", lastChangedTick);
        tag.putLong("lastGrowth", lastGrowthTick);
        return tag;
    }

    public static HouseholdRecord load(CompoundTag tag) {
        HouseholdRecord result = new HouseholdRecord(tag.getUUID("id"), tag.getString("readable"),
                tag.getUUID("settlement"), tag.getLong("lastChanged"));
        loadIds(tag.getList("members", Tag.TAG_STRING), result.members);
        loadIds(tag.getList("guardians", Tag.TAG_STRING), result.guardians);
        loadIds(tag.getList("dependants", Tag.TAG_STRING), result.dependants);
        if (tag.hasUUID("home")) result.homeBuildingId = tag.getUUID("home");
        try { result.state = HouseholdState.valueOf(tag.getString("state")); }
        catch (IllegalArgumentException ignored) { result.state = HouseholdState.NEEDS_HOME; }
        result.lastGrowthTick = tag.getLong("lastGrowth");
        return result;
    }

    private static ListTag saveIds(Collection<UUID> ids) {
        ListTag list = new ListTag();
        ids.forEach(id -> list.add(StringTag.valueOf(id.toString())));
        return list;
    }

    private static void loadIds(ListTag source, Set<UUID> target) {
        for (int i = 0; i < source.size(); i++) target.add(UUID.fromString(source.getString(i)));
    }
}
