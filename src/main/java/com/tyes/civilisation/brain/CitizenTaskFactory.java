package com.tyes.civilisation.brain;

import com.tyes.civilisation.brain.task.EatTask;
import com.tyes.civilisation.brain.task.IdleTask;
import com.tyes.civilisation.brain.task.MoveTask;
import com.tyes.civilisation.brain.task.SimpleWorkTask;
import com.tyes.civilisation.brain.task.SleepTask;
import com.tyes.civilisation.brain.task.SettlementWorkTask;
import com.tyes.civilisation.brain.task.DeliveryTask;
import com.tyes.civilisation.brain.task.ProductionTask;
import com.tyes.civilisation.brain.task.AcquireToolTask;
import com.tyes.civilisation.brain.task.BuilderTask;
import com.tyes.civilisation.brain.task.LearningTask;
import java.util.Optional;
import net.minecraft.nbt.CompoundTag;

public final class CitizenTaskFactory {
    private CitizenTaskFactory() { }

    public static Optional<CitizenTask> load(String type, CompoundTag tag) {
        try {
            return Optional.ofNullable(switch (type) {
                case MoveTask.TYPE -> MoveTask.load(tag);
                case EatTask.TYPE -> EatTask.load(tag);
                case SleepTask.TYPE -> SleepTask.load(tag);
                case SimpleWorkTask.TYPE -> SimpleWorkTask.load(tag);
                case SettlementWorkTask.TYPE -> SettlementWorkTask.load(tag);
                case DeliveryTask.TYPE -> DeliveryTask.load(tag);
                case ProductionTask.TYPE -> ProductionTask.load(tag);
                case AcquireToolTask.TYPE -> AcquireToolTask.load(tag);
                case BuilderTask.TYPE -> BuilderTask.load(tag);
                case LearningTask.TYPE -> LearningTask.load(tag);
                case IdleTask.TYPE -> IdleTask.load(tag);
                default -> null;
            });
        } catch (RuntimeException ignored) {
            return Optional.empty();
        }
    }
}
