package com.tyes.civilisation.brain.task;

import com.tyes.civilisation.brain.CitizenActivityState;
import com.tyes.civilisation.brain.CitizenBrain;
import com.tyes.civilisation.brain.CitizenTask;
import com.tyes.civilisation.brain.TaskCancelReason;
import com.tyes.civilisation.brain.TaskResult;
import com.tyes.civilisation.brain.TaskStartResult;
import net.minecraft.nbt.CompoundTag;

public final class IdleTask implements CitizenTask {
    public static final String TYPE = "idle";
    private int remainingTicks;

    public IdleTask(int remainingTicks) { this.remainingTicks = remainingTicks; }
    @Override public String type() { return TYPE; }
    @Override public CitizenActivityState activity() { return CitizenActivityState.IDLE; }
    @Override public TaskStartResult start(CitizenBrain brain) { brain.entity().getNavigation().stop(); return TaskStartResult.STARTED; }
    @Override public TaskResult tick(CitizenBrain brain) { return --remainingTicks <= 0 ? TaskResult.COMPLETE : TaskResult.RUNNING; }
    @Override public void cancel(CitizenBrain brain, TaskCancelReason reason) { }
    @Override public CompoundTag save() { CompoundTag tag = new CompoundTag(); tag.putInt("remainingTicks", remainingTicks); return tag; }
    public static IdleTask load(CompoundTag tag) { return new IdleTask(Math.max(1, tag.getInt("remainingTicks"))); }
}
