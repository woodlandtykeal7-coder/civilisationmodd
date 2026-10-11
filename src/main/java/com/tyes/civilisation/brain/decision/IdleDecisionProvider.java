package com.tyes.civilisation.brain.decision;

import com.tyes.civilisation.brain.CitizenBrain;
import com.tyes.civilisation.brain.DecisionCandidate;
import com.tyes.civilisation.brain.DecisionProvider;
import com.tyes.civilisation.brain.task.IdleTask;
import com.tyes.civilisation.brain.task.MoveTask;
import com.tyes.civilisation.citizen.AssignedLocation;
import java.util.Optional;

public final class IdleDecisionProvider implements DecisionProvider {
    @Override
    public Optional<DecisionCandidate> evaluate(CitizenBrain brain) {
        AssignedLocation home = brain.record().assignments().home().orElse(null);
        if (home != null && home.isIn(brain.level()) && brain.distanceToSqr(home) > 36.0D
                && brain.isTaskAvailable(MoveTask.TYPE)) {
            return Optional.of(new DecisionCandidate(new MoveTask(home, 3.0D, IdleTask.TYPE), 100, "return home"));
        }
        int duration = 80 + (int)Math.floorMod(brain.level().getGameTime() + brain.scheduleJitter(), 80L);
        return Optional.of(new DecisionCandidate(new IdleTask(duration), 0, "idle safely"));
    }
}
