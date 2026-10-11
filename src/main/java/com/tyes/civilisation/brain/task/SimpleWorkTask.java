package com.tyes.civilisation.brain.task;

import com.tyes.civilisation.brain.CitizenActivityState;
import com.tyes.civilisation.brain.CitizenBrain;
import com.tyes.civilisation.brain.CitizenTask;
import com.tyes.civilisation.brain.TaskCancelReason;
import com.tyes.civilisation.brain.TaskResult;
import com.tyes.civilisation.brain.TaskStartResult;
import com.tyes.civilisation.citizen.AssignedLocation;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;

/** A bounded work cycle at a physical marker. It intentionally creates no resources. */
public final class SimpleWorkTask implements CitizenTask {
    public static final String TYPE = "simple_work";
    private final AssignedLocation workplace;
    private int progressTicks;

    public SimpleWorkTask(AssignedLocation workplace) { this(workplace, 0); }
    private SimpleWorkTask(AssignedLocation workplace, int progressTicks) {
        this.workplace = workplace;
        this.progressTicks = progressTicks;
    }

    @Override public String type() { return TYPE; }
    @Override public CitizenActivityState activity() { return CitizenActivityState.WORKING; }

    @Override
    public TaskStartResult start(CitizenBrain brain) {
        if (!stillValid(brain) || brain.distanceToSqr(workplace) > 9.0D) return TaskStartResult.INVALID;
        brain.entity().getNavigation().stop();
        return TaskStartResult.STARTED;
    }

    @Override
    public TaskResult tick(CitizenBrain brain) {
        if (!stillValid(brain) || brain.distanceToSqr(workplace) > 16.0D) return TaskResult.BLOCKED;
        brain.lookAt(workplace);
        progressTicks++;
        if (progressTicks % 40 == 0) brain.entity().swing(InteractionHand.MAIN_HAND);
        return progressTicks >= 600 ? TaskResult.COMPLETE : TaskResult.RUNNING;
    }

    @Override public void cancel(CitizenBrain brain, TaskCancelReason reason) { }
    @Override public boolean resumable() { return true; }
    @Override public boolean stillValid(CitizenBrain brain) { return brain.isValidWorkplace(workplace); }
    @Override public void resume(CitizenBrain brain) { }
    public int progressTicks() { return progressTicks; }

    @Override
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.put("workplace", workplace.save());
        tag.putInt("progressTicks", progressTicks);
        return tag;
    }

    public static SimpleWorkTask load(CompoundTag tag) {
        return new SimpleWorkTask(AssignedLocation.load(tag.getCompound("workplace")).orElseThrow(),
                tag.getInt("progressTicks"));
    }
}
