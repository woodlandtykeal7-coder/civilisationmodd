package com.tyes.civilisation.brain.task;

import com.tyes.civilisation.brain.CitizenActivityState;
import com.tyes.civilisation.brain.CitizenBrain;
import com.tyes.civilisation.brain.CitizenTask;
import com.tyes.civilisation.brain.TaskCancelReason;
import com.tyes.civilisation.brain.TaskResult;
import com.tyes.civilisation.brain.TaskStartResult;
import com.tyes.civilisation.citizen.AssignedLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.Vec3;

public final class MoveTask implements CitizenTask {
    public static final String TYPE = "move";
    private final AssignedLocation target;
    private final double arrivalDistance;
    private final String purpose;
    private int age;
    private int retries;
    private int stationaryChecks;
    private Vec3 lastProgressPosition;

    public MoveTask(AssignedLocation target, double arrivalDistance) {
        this(target, arrivalDistance, "generic", 0, 0, 0, Vec3.ZERO);
    }

    public MoveTask(AssignedLocation target, double arrivalDistance, String purpose) {
        this(target, arrivalDistance, purpose, 0, 0, 0, Vec3.ZERO);
    }

    private MoveTask(AssignedLocation target, double arrivalDistance, String purpose, int age, int retries,
                     int stationaryChecks, Vec3 lastProgressPosition) {
        this.target = target;
        this.arrivalDistance = arrivalDistance;
        this.purpose = purpose;
        this.age = age;
        this.retries = retries;
        this.stationaryChecks = stationaryChecks;
        this.lastProgressPosition = lastProgressPosition;
    }

    @Override public String type() { return TYPE; }
    @Override public CitizenActivityState activity() { return CitizenActivityState.MOVING; }
    public String purpose() { return purpose; }

    @Override
    public TaskStartResult start(CitizenBrain brain) {
        if (!target.isIn(brain.level()) || !brain.level().isLoaded(target.pos())) return TaskStartResult.INVALID;
        lastProgressPosition = brain.entity().position();
        if (arrived(brain)) return TaskStartResult.STARTED;
        return requestPath(brain) ? TaskStartResult.STARTED : TaskStartResult.PATH_FAILED;
    }

    @Override
    public TaskResult tick(CitizenBrain brain) {
        age++;
        if (!target.isIn(brain.level()) || !brain.level().isLoaded(target.pos())) return TaskResult.BLOCKED;
        if (arrived(brain)) {
            brain.entity().getNavigation().stop();
            return TaskResult.COMPLETE;
        }
        if (age > 1600) return TaskResult.PATH_FAILED;

        if (age % 40 == 0) {
            double moved = brain.entity().position().distanceToSqr(lastProgressPosition);
            stationaryChecks = moved < 0.04D ? stationaryChecks + 1 : 0;
            lastProgressPosition = brain.entity().position();
            if (stationaryChecks >= 3 || brain.entity().getNavigation().isDone()) {
                if (retries >= 2) return TaskResult.PATH_FAILED;
                retries++;
                stationaryChecks = 0;
                if (!requestPath(brain)) return retries >= 2 ? TaskResult.PATH_FAILED : TaskResult.WAITING;
            }
        }
        return TaskResult.RUNNING;
    }

    private boolean requestPath(CitizenBrain brain) {
        BlockPos pos = target.pos();
        return brain.entity().getNavigation().moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D,
                Math.max(1, (int)Math.ceil(arrivalDistance)), 1.0D);
    }

    private boolean arrived(CitizenBrain brain) {
        return brain.entity().position().distanceToSqr(Vec3.atCenterOf(target.pos()))
                <= arrivalDistance * arrivalDistance;
    }

    @Override
    public void cancel(CitizenBrain brain, TaskCancelReason reason) {
        brain.entity().getNavigation().stop();
    }

    @Override
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.put("target", target.save());
        tag.putDouble("arrivalDistance", arrivalDistance);
        tag.putString("purpose", purpose);
        tag.putInt("age", age);
        tag.putInt("retries", retries);
        tag.putInt("stationaryChecks", stationaryChecks);
        tag.putDouble("lastX", lastProgressPosition.x);
        tag.putDouble("lastY", lastProgressPosition.y);
        tag.putDouble("lastZ", lastProgressPosition.z);
        return tag;
    }

    public static MoveTask load(CompoundTag tag) {
        AssignedLocation target = AssignedLocation.load(tag.getCompound("target")).orElseThrow();
        String purpose = tag.getString("purpose");
        if (purpose.isBlank()) purpose = "generic";
        return new MoveTask(target, tag.getDouble("arrivalDistance"), purpose, tag.getInt("age"),
                tag.getInt("retries"), tag.getInt("stationaryChecks"),
                new Vec3(tag.getDouble("lastX"), tag.getDouble("lastY"), tag.getDouble("lastZ")));
    }
}
