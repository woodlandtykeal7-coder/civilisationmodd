package com.tyes.civilisation.brain.decision;

import com.tyes.civilisation.brain.CitizenBrain;
import com.tyes.civilisation.brain.DecisionCandidate;
import com.tyes.civilisation.brain.DecisionProvider;
import com.tyes.civilisation.brain.task.EatTask;
import com.tyes.civilisation.brain.task.MoveTask;
import com.tyes.civilisation.citizen.AssignedLocation;
import java.util.Optional;

public final class EatDecisionProvider implements DecisionProvider {
    @Override
    public Optional<DecisionCandidate> evaluate(CitizenBrain brain) {
        if (!brain.record().needs().isHungry() || !brain.isTaskAvailable(EatTask.TYPE)) return Optional.empty();
        int score = brain.record().needs().isCriticallyHungry() ? 1000 : 700;
        if (brain.record().inventory().findFood().isPresent()) {
            return Optional.of(new DecisionCandidate(new EatTask(), score, "personal food available"));
        }
        AssignedLocation storage = brain.record().assignments().foodStorage().orElse(null);
        if (storage == null || !brain.hasFood(storage)) return Optional.empty();
        if (brain.distanceToSqr(storage) > 12.25D) {
            if (!brain.isTaskAvailable(MoveTask.TYPE)) return Optional.empty();
            return Optional.of(new DecisionCandidate(new MoveTask(storage, 3.0D, EatTask.TYPE), score,
                    "travel to assigned food storage"));
        }
        return Optional.of(new DecisionCandidate(new EatTask(), score, "take physical food from storage"));
    }
}
