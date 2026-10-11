package com.tyes.civilisation.brain.decision;

import com.tyes.civilisation.brain.CitizenBrain;
import com.tyes.civilisation.brain.CitizenSchedule;
import com.tyes.civilisation.brain.DecisionCandidate;
import com.tyes.civilisation.brain.DecisionProvider;
import com.tyes.civilisation.brain.task.MoveTask;
import com.tyes.civilisation.brain.task.SimpleWorkTask;
import com.tyes.civilisation.citizen.AssignedLocation;
import java.util.Optional;

public final class WorkDecisionProvider implements DecisionProvider {
    @Override
    public Optional<DecisionCandidate> evaluate(CitizenBrain brain) {
        if (!brain.record().development().lifeStage().mayPerformFullWork()
                || brain.record().settlementId().isPresent()
                || brain.record().needs().isCriticallyHungry() || brain.record().needs().isCriticallyTired()
                || !CitizenSchedule.isWorkTime(brain.level().getDayTime(), brain.scheduleJitter())
                || !brain.isTaskAvailable(SimpleWorkTask.TYPE)) return Optional.empty();
        AssignedLocation workplace = brain.record().assignments().workplace().orElse(null);
        if (workplace == null || !brain.isValidWorkplace(workplace)) return Optional.empty();
        if (brain.distanceToSqr(workplace) > 9.0D) {
            if (!brain.isTaskAvailable(MoveTask.TYPE)) return Optional.empty();
            return Optional.of(new DecisionCandidate(new MoveTask(workplace, 2.5D, SimpleWorkTask.TYPE), 600,
                    "travel to simple workplace"));
        }
        return Optional.of(new DecisionCandidate(new SimpleWorkTask(workplace), 600, "scheduled simple work"));
    }
}
