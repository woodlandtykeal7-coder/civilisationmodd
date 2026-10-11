package com.tyes.civilisation.brain.decision;

import com.tyes.civilisation.brain.CitizenBrain;
import com.tyes.civilisation.brain.CitizenSchedule;
import com.tyes.civilisation.brain.DecisionCandidate;
import com.tyes.civilisation.brain.DecisionProvider;
import com.tyes.civilisation.brain.task.LearningTask;
import com.tyes.civilisation.server.CivilisationServices;
import java.util.Optional;

public final class EducationDecisionProvider implements DecisionProvider {
    @Override
    public Optional<DecisionCandidate> evaluate(CitizenBrain brain) {
        if (brain.record().development().lifeStage().mayPerformFullWork()
                || brain.record().needs().isCriticallyHungry() || brain.record().needs().isCriticallyTired()
                || !CitizenSchedule.isWorkTime(brain.level().getDayTime(), brain.scheduleJitter())
                || !brain.isTaskAvailable(LearningTask.TYPE)) return Optional.empty();
        return CivilisationServices.demography(brain.level().getServer()).learningOpportunity(brain.record())
                .map(opportunity -> new DecisionCandidate(new LearningTask(opportunity), 700,
                        "education/work experience with mentor " + opportunity.mentorId()));
    }
}
