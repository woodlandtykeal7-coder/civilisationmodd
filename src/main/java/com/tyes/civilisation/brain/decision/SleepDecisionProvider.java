package com.tyes.civilisation.brain.decision;

import com.tyes.civilisation.brain.CitizenBrain;
import com.tyes.civilisation.brain.CitizenSchedule;
import com.tyes.civilisation.brain.DecisionCandidate;
import com.tyes.civilisation.brain.DecisionProvider;
import com.tyes.civilisation.brain.task.MoveTask;
import com.tyes.civilisation.brain.task.SleepTask;
import com.tyes.civilisation.citizen.AssignedLocation;
import java.util.Optional;

public final class SleepDecisionProvider implements DecisionProvider {
    @Override
    public Optional<DecisionCandidate> evaluate(CitizenBrain brain) {
        boolean scheduled = CitizenSchedule.isSleepTime(brain.level().getDayTime(), brain.scheduleJitter());
        if ((!scheduled && !brain.record().needs().isCriticallyTired()) || !brain.isTaskAvailable(SleepTask.TYPE)) {
            return Optional.empty();
        }
        AssignedLocation bed = brain.record().assignments().bed().orElse(null);
        if (bed == null || !brain.isValidBed(bed)) return Optional.empty();
        int score = brain.record().needs().isCriticallyTired() ? 950 : 650;
        if (brain.distanceToSqr(bed) > 6.25D) {
            if (!brain.isTaskAvailable(MoveTask.TYPE)) return Optional.empty();
            return Optional.of(new DecisionCandidate(new MoveTask(bed, 2.0D, SleepTask.TYPE), score, "travel home to sleep"));
        }
        return Optional.of(new DecisionCandidate(new SleepTask(bed), score, "scheduled sleep"));
    }
}
