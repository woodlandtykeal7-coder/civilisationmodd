package com.tyes.civilisation.brain.task;

import com.tyes.civilisation.brain.CitizenActivityState;
import com.tyes.civilisation.brain.CitizenBrain;
import com.tyes.civilisation.brain.CitizenTask;
import com.tyes.civilisation.brain.TaskCancelReason;
import com.tyes.civilisation.brain.TaskResult;
import com.tyes.civilisation.brain.TaskStartResult;
import com.tyes.civilisation.citizen.AssignedLocation;
import com.tyes.civilisation.entity.CitizenVisualState;
import com.tyes.civilisation.server.CivilisationServices;
import com.tyes.civilisation.settlement.WorkRole;
import com.tyes.civilisation.society.DemographyService;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;

/** A bounded physical teaching/work-experience session at a real mentor location. */
public final class LearningTask implements CitizenTask {
    public static final String TYPE = "learning";
    private static final int SESSION_TICKS = 200;
    private final AssignedLocation location;
    private final UUID mentorId;
    private final WorkRole focus;
    @Nullable private final UUID apprenticeshipId;
    private int age;
    private int learningTicks;

    public LearningTask(DemographyService.LearningOpportunity opportunity) {
        this(opportunity.location(), opportunity.mentorId(), opportunity.focus(),
                opportunity.apprenticeship().orElse(null), 0, 0);
    }

    private LearningTask(AssignedLocation location, UUID mentorId, WorkRole focus,
                         @Nullable UUID apprenticeshipId, int age, int learningTicks) {
        this.location = location;
        this.mentorId = mentorId;
        this.focus = focus;
        this.apprenticeshipId = apprenticeshipId;
        this.age = age;
        this.learningTicks = learningTicks;
    }

    @Override public String type() { return TYPE; }
    @Override public CitizenActivityState activity() { return CitizenActivityState.WORKING; }

    @Override
    public TaskStartResult start(CitizenBrain brain) {
        if (!stillValid(brain)) return TaskStartResult.INVALID;
        if (brain.distanceToSqr(location) <= 12.25D) return TaskStartResult.STARTED;
        return brain.entity().getNavigation().moveTo(location.pos().getX() + 0.5D, location.pos().getY(),
                location.pos().getZ() + 0.5D, 1.0D) ? TaskStartResult.STARTED : TaskStartResult.PATH_FAILED;
    }

    @Override
    public TaskResult tick(CitizenBrain brain) {
        if (!stillValid(brain)) return TaskResult.FAILED;
        if (brain.distanceToSqr(location) > 12.25D) {
            brain.entity().setTaskVisualState(CitizenVisualState.WALKING);
            if (++age > 1200) return TaskResult.PATH_FAILED;
            if (age % 40 == 0 && brain.entity().getNavigation().isDone()
                    && !brain.entity().getNavigation().moveTo(location.pos().getX() + 0.5D,
                    location.pos().getY(), location.pos().getZ() + 0.5D, 1.0D)) return TaskResult.PATH_FAILED;
            return TaskResult.RUNNING;
        }
        brain.entity().getNavigation().stop();
        brain.lookAt(location);
        brain.entity().setTaskVisualState(CitizenVisualState.WORKING);
        if (++learningTicks < SESSION_TICKS) return TaskResult.RUNNING;
        CivilisationServices.demography(brain.level().getServer()).completeLearning(brain.record(), focus, 5,
                Optional.ofNullable(apprenticeshipId));
        return TaskResult.COMPLETE;
    }

    @Override
    public boolean stillValid(CitizenBrain brain) {
        if (!location.isIn(brain.level())) return false;
        return CivilisationServices.activeCitizens(brain.level().getServer()).bodyFor(mentorId).isPresent();
    }

    @Override
    public void cancel(CitizenBrain brain, TaskCancelReason reason) {
        brain.entity().getNavigation().stop();
    }

    @Override
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.put("location", location.save());
        tag.putUUID("mentor", mentorId);
        tag.putString("focus", focus.name());
        if (apprenticeshipId != null) tag.putUUID("apprenticeship", apprenticeshipId);
        tag.putInt("age", age);
        tag.putInt("learningTicks", learningTicks);
        return tag;
    }

    public static LearningTask load(CompoundTag tag) {
        return new LearningTask(AssignedLocation.load(tag.getCompound("location")).orElseThrow(),
                tag.getUUID("mentor"), WorkRole.parse(tag.getString("focus")),
                tag.hasUUID("apprenticeship") ? tag.getUUID("apprenticeship") : null,
                tag.getInt("age"), tag.getInt("learningTicks"));
    }
}
