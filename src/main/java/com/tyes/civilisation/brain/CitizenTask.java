package com.tyes.civilisation.brain;

import net.minecraft.nbt.CompoundTag;

public interface CitizenTask {
    String type();
    CitizenActivityState activity();
    TaskStartResult start(CitizenBrain brain);
    TaskResult tick(CitizenBrain brain);
    void cancel(CitizenBrain brain, TaskCancelReason reason);
    default boolean resumable() { return false; }
    default boolean stillValid(CitizenBrain brain) { return true; }
    default void resume(CitizenBrain brain) { }
    CompoundTag save();
}
