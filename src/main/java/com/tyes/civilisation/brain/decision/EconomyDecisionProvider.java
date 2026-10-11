package com.tyes.civilisation.brain.decision;

import com.tyes.civilisation.brain.*;
import com.tyes.civilisation.brain.task.DeliveryTask;
import com.tyes.civilisation.brain.task.ProductionTask;
import com.tyes.civilisation.brain.task.AcquireToolTask;
import com.tyes.civilisation.server.CivilisationServices;
import com.tyes.civilisation.settlement.WorkRole;
import java.util.Optional;
import net.minecraft.tags.ItemTags;

/** Converts settlement coordination requests into the one physical task owned by CitizenBrain. */
public final class EconomyDecisionProvider implements DecisionProvider {
    @Override public Optional<DecisionCandidate> evaluate(CitizenBrain brain) {
        if (!brain.record().development().lifeStage().mayPerformFullWork()
                || brain.record().settlementId().isEmpty() || brain.record().needs().isCriticallyHungry()
                || brain.record().needs().isCriticallyTired()
                || !CitizenSchedule.isWorkTime(brain.level().getDayTime(), brain.scheduleJitter())) return Optional.empty();
        var economy = CivilisationServices.economy(brain.level().getServer());
        WorkRole role = brain.record().workRole();
        if (role == WorkRole.LUMBERJACK && !brain.record().inventory().has(ItemTags.AXES)
                && brain.isTaskAvailable(AcquireToolTask.TYPE)) {
            var reservation = economy.reserveAxe(brain.record());
            if (reservation.isPresent()) return Optional.of(new DecisionCandidate(new AcquireToolTask(reservation.get()), 805,
                    "check out a physical axe"));
        }
        if ((role == WorkRole.COURIER || role == WorkRole.HAULER || role == WorkRole.GENERAL_LABOURER)
                && brain.isTaskAvailable(DeliveryTask.TYPE)) {
            var delivery = economy.claimDelivery(brain.record());
            if (delivery.isPresent()) return Optional.of(new DecisionCandidate(new DeliveryTask(delivery.get()), score(delivery.get().priority(), 790),
                    "physical delivery " + delivery.get().id()));
        }
        if ((role == WorkRole.CRAFTER || role == WorkRole.SMELTER || role == WorkRole.GENERAL_LABOURER)
                && brain.isTaskAvailable(ProductionTask.TYPE)) {
            var production = economy.claimProduction(brain.record());
            if (production.isPresent()) return Optional.of(new DecisionCandidate(new ProductionTask(production.get()), score(production.get().priority(), 760),
                    "production request " + production.get().requestId()));
        }
        return Optional.empty();
    }

    private static int score(Enum<?> priority, int normal) {
        return switch (priority.name()) {
            case "CRITICAL" -> normal + 90;
            case "HIGH" -> normal + 50;
            case "LOW" -> normal - 90;
            default -> normal;
        };
    }
}
