package com.tyes.civilisation.brain.decision;

import com.tyes.civilisation.brain.CitizenBrain;
import com.tyes.civilisation.brain.CitizenSchedule;
import com.tyes.civilisation.brain.DecisionCandidate;
import com.tyes.civilisation.brain.DecisionProvider;
import com.tyes.civilisation.brain.task.SettlementWorkTask;
import com.tyes.civilisation.server.CivilisationServices;
import com.tyes.civilisation.construction.project.ConstructionPriority;
import java.util.Optional;
import com.tyes.civilisation.settlement.WorkRole;
import net.minecraft.tags.ItemTags;

public final class SettlementWorkDecisionProvider implements DecisionProvider {
    @Override
    public Optional<DecisionCandidate> evaluate(CitizenBrain brain) {
        if (!brain.record().development().lifeStage().mayPerformFullWork()
                || brain.record().settlementId().isEmpty() || brain.record().needs().isCriticallyTired()
                || !CitizenSchedule.isWorkTime(brain.level().getDayTime(), brain.scheduleJitter())
                || !brain.isTaskAvailable(SettlementWorkTask.TYPE)) return Optional.empty();
        if (brain.record().workRole() == WorkRole.LUMBERJACK && !brain.record().inventory().has(ItemTags.AXES)) {
            return Optional.empty();
        }
        if (brain.record().needs().isCriticallyHungry()) {
            return CivilisationServices.workBoard(brain.level().getServer()).claimFood(brain.record(), brain.level())
                    .map(work -> new DecisionCandidate(new SettlementWorkTask(work), 925,
                            "emergency physical food gathering: " + work.unitId()));
        }
        var construction = CivilisationServices.construction(brain.level().getServer());
        Optional<ConstructionPriority> timberPriority = brain.record().workRole() == WorkRole.LUMBERJACK
                ? construction.timberPriority(brain.record().settlementId().orElseThrow()) : Optional.empty();
        int score = timberPriority.map(priority -> switch (priority) {
            case CRITICAL -> 850;
            case HIGH -> 815;
            case NORMAL -> 700;
            case LOW -> 625;
        }).orElse(625);
        String reason = timberPriority.map(priority -> "construction timber supply (" + priority + ")")
                .orElse("settlement work");
        return CivilisationServices.workBoard(brain.level().getServer()).claim(brain.record(), brain.level())
                .map(work -> new DecisionCandidate(new SettlementWorkTask(work), score,
                        reason + ": " + work.unitId()));
    }
}
