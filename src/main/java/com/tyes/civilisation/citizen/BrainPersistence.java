package com.tyes.civilisation.citizen;

import net.minecraft.nbt.CompoundTag;

public final class BrainPersistence {
    private String activeTaskType = "";
    private CompoundTag activeTaskData = new CompoundTag();
    private String suspendedTaskType = "";
    private CompoundTag suspendedTaskData = new CompoundTag();

    public String activeTaskType() { return activeTaskType; }
    public CompoundTag activeTaskData() { return activeTaskData.copy(); }
    public String suspendedTaskType() { return suspendedTaskType; }
    public CompoundTag suspendedTaskData() { return suspendedTaskData.copy(); }

    public void setActive(String type, CompoundTag data) {
        activeTaskType = type;
        activeTaskData = data.copy();
    }

    public void clearActive() { setActive("", new CompoundTag()); }

    public void setSuspended(String type, CompoundTag data) {
        suspendedTaskType = type;
        suspendedTaskData = data.copy();
    }

    public void clearSuspended() { setSuspended("", new CompoundTag()); }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("activeTaskType", activeTaskType);
        tag.put("activeTaskData", activeTaskData.copy());
        tag.putString("suspendedTaskType", suspendedTaskType);
        tag.put("suspendedTaskData", suspendedTaskData.copy());
        return tag;
    }

    public static BrainPersistence load(CompoundTag tag) {
        BrainPersistence result = new BrainPersistence();
        result.activeTaskType = tag.getString("activeTaskType");
        result.activeTaskData = tag.getCompound("activeTaskData").copy();
        result.suspendedTaskType = tag.getString("suspendedTaskType");
        result.suspendedTaskData = tag.getCompound("suspendedTaskData").copy();
        return result;
    }
}
