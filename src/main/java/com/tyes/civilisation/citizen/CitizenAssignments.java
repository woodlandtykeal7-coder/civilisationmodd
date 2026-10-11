package com.tyes.civilisation.citizen;

import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;

public final class CitizenAssignments {
    @Nullable private AssignedLocation home;
    @Nullable private AssignedLocation bed;
    @Nullable private AssignedLocation foodStorage;
    @Nullable private AssignedLocation workplace;

    public Optional<AssignedLocation> home() { return Optional.ofNullable(home); }
    public Optional<AssignedLocation> bed() { return Optional.ofNullable(bed); }
    public Optional<AssignedLocation> foodStorage() { return Optional.ofNullable(foodStorage); }
    public Optional<AssignedLocation> workplace() { return Optional.ofNullable(workplace); }

    public void setHome(AssignedLocation value) { home = value; }
    public void clearHome() { home = null; }
    public void setBed(AssignedLocation value) { bed = value; }
    public void setFoodStorage(AssignedLocation value) { foodStorage = value; }
    public void setWorkplace(AssignedLocation value) { workplace = value; }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        if (home != null) tag.put("home", home.save());
        if (bed != null) tag.put("bed", bed.save());
        if (foodStorage != null) tag.put("foodStorage", foodStorage.save());
        if (workplace != null) tag.put("workplace", workplace.save());
        return tag;
    }

    public static CitizenAssignments load(CompoundTag tag) {
        CitizenAssignments result = new CitizenAssignments();
        if (tag.contains("home")) result.home = AssignedLocation.load(tag.getCompound("home")).orElse(null);
        if (tag.contains("bed")) result.bed = AssignedLocation.load(tag.getCompound("bed")).orElse(null);
        if (tag.contains("foodStorage")) result.foodStorage = AssignedLocation.load(tag.getCompound("foodStorage")).orElse(null);
        if (tag.contains("workplace")) result.workplace = AssignedLocation.load(tag.getCompound("workplace")).orElse(null);
        return result;
    }
}
