package com.tyes.civilisation.brain.task;

import com.tyes.civilisation.brain.CitizenActivityState;
import com.tyes.civilisation.brain.CitizenBrain;
import com.tyes.civilisation.brain.CitizenSchedule;
import com.tyes.civilisation.brain.CitizenTask;
import com.tyes.civilisation.brain.TaskCancelReason;
import com.tyes.civilisation.brain.TaskResult;
import com.tyes.civilisation.brain.TaskStartResult;
import com.tyes.civilisation.citizen.AssignedLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;

public final class SleepTask implements CitizenTask {
    public static final String TYPE = "sleep";
    private final AssignedLocation bed;
    private int sleptTicks;

    public SleepTask(AssignedLocation bed) { this(bed, 0); }
    private SleepTask(AssignedLocation bed, int sleptTicks) {
        this.bed = bed;
        this.sleptTicks = sleptTicks;
    }

    @Override public String type() { return TYPE; }
    @Override public CitizenActivityState activity() { return CitizenActivityState.SLEEPING; }

    @Override
    public TaskStartResult start(CitizenBrain brain) {
        if (!stillValid(brain) || brain.distanceToSqr(bed) > 9.0D) return TaskStartResult.INVALID;
        brain.entity().getNavigation().stop();
        brain.lookAt(bed);
        if (!brain.entity().isSleeping()) brain.entity().startSleeping(sleepingPosition(brain));
        return TaskStartResult.STARTED;
    }

    @Override
    public TaskResult tick(CitizenBrain brain) {
        if (!stillValid(brain)) {
            if (brain.entity().isSleeping()) brain.entity().stopSleeping();
            return TaskResult.BLOCKED;
        }
        if (!brain.entity().isSleeping()) brain.entity().startSleeping(sleepingPosition(brain));
        sleptTicks++;
        boolean sleepTime = CitizenSchedule.isSleepTime(brain.level().getDayTime(), brain.scheduleJitter());
        if ((!sleepTime && brain.record().needs().fatigue() <= 18.0D) || sleptTicks >= 12000) {
            brain.entity().stopSleeping();
            return TaskResult.COMPLETE;
        }
        return TaskResult.RUNNING;
    }

    @Override
    public void cancel(CitizenBrain brain, TaskCancelReason reason) {
        if (brain.entity().isSleeping()) brain.entity().stopSleeping();
    }

    @Override public boolean stillValid(CitizenBrain brain) { return brain.isValidBed(bed); }

    private BlockPos sleepingPosition(CitizenBrain brain) {
        BlockState state = brain.level().getBlockState(bed.pos());
        if (state.getBlock() instanceof BedBlock && state.getValue(BedBlock.PART) == BedPart.FOOT) {
            return bed.pos().relative(state.getValue(BedBlock.FACING));
        }
        return bed.pos();
    }

    @Override
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.put("bed", bed.save());
        tag.putInt("sleptTicks", sleptTicks);
        return tag;
    }

    public static SleepTask load(CompoundTag tag) {
        return new SleepTask(AssignedLocation.load(tag.getCompound("bed")).orElseThrow(),
                tag.getInt("sleptTicks"));
    }
}
