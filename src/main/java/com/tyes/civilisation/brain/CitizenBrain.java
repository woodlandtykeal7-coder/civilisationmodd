package com.tyes.civilisation.brain;

import com.tyes.civilisation.brain.decision.EatDecisionProvider;
import com.tyes.civilisation.brain.decision.IdleDecisionProvider;
import com.tyes.civilisation.brain.decision.SleepDecisionProvider;
import com.tyes.civilisation.brain.decision.WorkDecisionProvider;
import com.tyes.civilisation.brain.decision.SettlementWorkDecisionProvider;
import com.tyes.civilisation.brain.decision.EconomyDecisionProvider;
import com.tyes.civilisation.brain.decision.ConstructionDecisionProvider;
import com.tyes.civilisation.brain.decision.EducationDecisionProvider;
import com.tyes.civilisation.brain.task.SimpleWorkTask;
import com.tyes.civilisation.brain.task.MoveTask;
import com.tyes.civilisation.brain.task.EatTask;
import com.tyes.civilisation.brain.task.SleepTask;
import com.tyes.civilisation.brain.task.SettlementWorkTask;
import com.tyes.civilisation.citizen.AssignedLocation;
import com.tyes.civilisation.citizen.CitizenRecord;
import com.tyes.civilisation.citizen.RuntimePresence;
import com.tyes.civilisation.entity.CivilianEntity;
import com.tyes.civilisation.entity.CitizenVisualState;
import com.tyes.civilisation.persistence.CitizenSavedData;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;

/** Sole owner of one loaded citizen's active physical task. */
public final class CitizenBrain {
    private static final int NEED_UPDATE_INTERVAL = 100;
    private static final int DECISION_INTERVAL = 40;
    private static final int PERSIST_INTERVAL = 200;
    private static final int DEBUG_HISTORY_LIMIT = 64;

    private final CivilianEntity entity;
    private final CitizenRecord record;
    private final CitizenSavedData savedData;
    private final List<DecisionProvider> providers = List.of(
            new EatDecisionProvider(), new SleepDecisionProvider(), new EducationDecisionProvider(), new EconomyDecisionProvider(), new ConstructionDecisionProvider(), new SettlementWorkDecisionProvider(),
            new WorkDecisionProvider(), new IdleDecisionProvider());
    private final Deque<String> debugHistory = new ArrayDeque<>();
    private final Map<String, Long> retryAfter = new HashMap<>();
    private final long scheduleJitter;
    private final int tickOffset;

    @Nullable private CitizenTask activeTask;
    @Nullable private CitizenTask suspendedTask;
    private boolean activeStarted;
    private boolean immediateDecision = true;
    private CitizenActivityState activity = CitizenActivityState.IDLE;
    private long lastDecisionTick = Long.MIN_VALUE;

    public CitizenBrain(CivilianEntity entity, CitizenRecord record, CitizenSavedData savedData) {
        this.entity = entity;
        this.record = record;
        this.savedData = savedData;
        this.scheduleJitter = Math.floorMod(record.appearanceSeed(), 401L) - 200L;
        this.tickOffset = Math.floorMod(record.citizenId().hashCode(), PERSIST_INTERVAL);
        this.activeTask = CitizenTaskFactory.load(record.brainPersistence().activeTaskType(),
                record.brainPersistence().activeTaskData()).orElse(null);
        this.suspendedTask = CitizenTaskFactory.load(record.brainPersistence().suspendedTaskType(),
                record.brainPersistence().suspendedTaskData()).filter(CitizenTask::resumable).orElse(null);
        if (activeTask != null) activity = activeTask.activity();
        log("brain restored; active=" + taskName(activeTask) + ", suspended=" + taskName(suspendedTask));
    }

    public void tick() {
        long now = level().getGameTime();
        if (Math.floorMod(now + tickOffset, NEED_UPDATE_INTERVAL) == 0L) {
            if (entity.isSleeping() || activity == CitizenActivityState.SLEEPING) record.needs().tickSleeping(NEED_UPDATE_INTERVAL);
            else record.needs().tickAwake(NEED_UPDATE_INTERVAL);
            record.updatePresence(RuntimePresence.MATERIALIZED, level(), entity.position());
            markDirty();
            if (criticalNeedShouldInterrupt()) interruptForCriticalNeed();
        }

        if (activeTask != null) tickActiveTask();
        if (activeTask == null && (immediateDecision || now - lastDecisionTick >= DECISION_INTERVAL)) decide();
        if (Math.floorMod(now + tickOffset, PERSIST_INTERVAL) == 0L) persistNow();
    }

    private boolean criticalNeedShouldInterrupt() {
        if (activeTask == null) return false;
        if (!record.needs().isCriticallyHungry() && !record.needs().isCriticallyTired()) return false;
        // A missing resource must stop productive work, but must not cause an idle/path
        // cancel/reselect loop while there is no food or valid bed to respond with.
        if (!hasViableCriticalResponse()) {
            if (activeTask instanceof SettlementWorkTask work && record.needs().isCriticallyHungry()
                    && work.producesFood()) return false;
            return activeTask instanceof SimpleWorkTask
                    || activeTask instanceof SettlementWorkTask
                    || activeTask instanceof com.tyes.civilisation.brain.task.DeliveryTask
                    || activeTask instanceof com.tyes.civilisation.brain.task.ProductionTask
                    || activeTask instanceof com.tyes.civilisation.brain.task.BuilderTask
                    || activeTask instanceof MoveTask move && SimpleWorkTask.TYPE.equals(move.purpose());
        }
        if (activeTask instanceof MoveTask move) {
            if (record.needs().isCriticallyHungry()) return !EatTask.TYPE.equals(move.purpose());
            if (record.needs().isCriticallyTired()) return !SleepTask.TYPE.equals(move.purpose());
        }
        if (record.needs().isCriticallyHungry()) {
            return activity != CitizenActivityState.EATING && activity != CitizenActivityState.SLEEPING;
        }
        return record.needs().isCriticallyTired()
                && activity != CitizenActivityState.SLEEPING && activity != CitizenActivityState.EATING;
    }

    private boolean hasViableCriticalResponse() {
        boolean hungerResponse = record.needs().isCriticallyHungry()
                && (record.inventory().findFood().isPresent()
                || record.assignments().foodStorage().filter(this::hasFood).isPresent());
        boolean sleepResponse = record.needs().isCriticallyTired()
                && record.assignments().bed().filter(this::isValidBed).isPresent();
        return hungerResponse || sleepResponse;
    }

    private void interruptForCriticalNeed() {
        CitizenTask interrupted = activeTask;
        if (interrupted == null) return;
        interrupted.cancel(this, TaskCancelReason.CRITICAL_NEED);
        if (interrupted.resumable() && interrupted.stillValid(this)) {
            suspendedTask = interrupted;
            log("suspended " + interrupted.type() + " for critical need");
        } else {
            log("cancelled " + interrupted.type() + " for critical need");
        }
        activeTask = null;
        activeStarted = false;
        setActivity(CitizenActivityState.IDLE);
        immediateDecision = true;
        persistNow();
    }

    private void tickActiveTask() {
        CitizenTask task = activeTask;
        if (task == null) return;
        if (!activeStarted) {
            TaskStartResult start = task.start(this);
            if (start != TaskStartResult.STARTED) {
                finishTask(start == TaskStartResult.PATH_FAILED ? TaskResult.PATH_FAILED : TaskResult.FAILED);
                return;
            }
            activeStarted = true;
            setActivity(task.activity());
            log("started " + task.type());
        }
        TaskResult result = task.tick(this);
        if (result != TaskResult.RUNNING && result != TaskResult.WAITING) finishTask(result);
    }

    private void finishTask(TaskResult result) {
        CitizenTask finished = activeTask;
        if (finished == null) return;
        log(finished.type() + " -> " + result);
        if (result == TaskResult.COMPLETE) {
            com.tyes.civilisation.server.CivilisationServices.demography(level().getServer())
                    .recordTaskCompletion(record, finished);
        }
        if (result == TaskResult.PATH_FAILED || result == TaskResult.BLOCKED || result == TaskResult.FAILED) {
            finished.cancel(this, result == TaskResult.PATH_FAILED ? TaskCancelReason.STUCK : TaskCancelReason.TARGET_INVALID);
            retryAfter.put(finished.type(), level().getGameTime() + 200L);
        }
        activeTask = null;
        activeStarted = false;
        setActivity(CitizenActivityState.IDLE);
        immediateDecision = true;
        persistNow();
    }

    private void decide() {
        long now = level().getGameTime();
        immediateDecision = false;
        lastDecisionTick = now;

        if (canResumeWork()) {
            boolean inResumePosition = suspendedTask instanceof SettlementWorkTask
                    || record.assignments().workplace().filter(w -> distanceToSqr(w) <= 9.0D).isPresent();
            if (inResumePosition) {
                activeTask = suspendedTask;
                suspendedTask = null;
                activeStarted = true;
                setActivity(activeTask.activity());
                activeTask.resume(this);
                log("resumed " + activeTask.type());
                persistNow();
                return;
            }
        }

        List<DecisionCandidate> candidates = new ArrayList<>();
        providers.forEach(provider -> provider.evaluate(this).ifPresent(candidates::add));
        Optional<DecisionCandidate> best = candidates.stream().max(Comparator.comparingInt(DecisionCandidate::score));
        if (best.isEmpty()) return;
        activeTask = best.get().task();
        activeStarted = false;
        activity = activeTask.activity();
        log("selected " + activeTask.type() + " score=" + best.get().score() + " because " + best.get().reason());
        persistNow();
    }

    private boolean canResumeWork() {
        if (!(suspendedTask instanceof SimpleWorkTask || suspendedTask instanceof SettlementWorkTask
                || suspendedTask instanceof com.tyes.civilisation.brain.task.DeliveryTask
                || suspendedTask instanceof com.tyes.civilisation.brain.task.ProductionTask
                || suspendedTask instanceof com.tyes.civilisation.brain.task.BuilderTask)
                || !suspendedTask.stillValid(this)) {
            if (suspendedTask != null && !suspendedTask.stillValid(this)) {
                log("discarded invalid suspended " + suspendedTask.type());
                suspendedTask = null;
            }
            return false;
        }
        return !record.needs().isHungry() && !record.needs().isTired()
                && CitizenSchedule.isWorkTime(level().getDayTime(), scheduleJitter);
    }

    public void persistNow() {
        if (activeTask == null) record.brainPersistence().clearActive();
        else record.brainPersistence().setActive(activeTask.type(), activeTask.save());
        if (suspendedTask == null) record.brainPersistence().clearSuspended();
        else record.brainPersistence().setSuspended(suspendedTask.type(), suspendedTask.save());
        markDirty();
    }

    public void cancel(TaskCancelReason reason) {
        if (activeTask != null) activeTask.cancel(this, reason);
        activeTask = null;
        suspendedTask = null;
        entity.clearVisualItem();
        setActivity(CitizenActivityState.IDLE);
        persistNow();
    }

    public CivilianEntity entity() { return entity; }
    public CitizenRecord record() { return record; }
    public ServerLevel level() { return (ServerLevel)entity.level(); }
    public long scheduleJitter() { return scheduleJitter; }
    public CitizenActivityState activity() { return activity; }
    public String activeTaskType() { return taskName(activeTask); }
    public String suspendedTaskType() { return taskName(suspendedTask); }
    public void markDirty() { savedData.setDirty(); }

    public double distanceToSqr(AssignedLocation location) {
        if (!location.isIn(level())) return Double.POSITIVE_INFINITY;
        return entity.position().distanceToSqr(Vec3.atCenterOf(location.pos()));
    }

    public boolean isValidBed(AssignedLocation bed) {
        return bed.isIn(level()) && level().isLoaded(bed.pos())
                && level().getBlockState(bed.pos()).isBed(level(), bed.pos(), entity);
    }

    public boolean isValidWorkplace(AssignedLocation workplace) {
        return workplace.isIn(level()) && level().isLoaded(workplace.pos())
                && level().getBlockState(workplace.pos()).is(Blocks.CRAFTING_TABLE);
    }

    public Optional<Container> foodContainer(AssignedLocation storage) {
        if (!storage.isIn(level()) || !level().isLoaded(storage.pos())) return Optional.empty();
        BlockEntity blockEntity = level().getBlockEntity(storage.pos());
        return blockEntity instanceof Container container ? Optional.of(container) : Optional.empty();
    }

    public boolean hasFood(AssignedLocation storage) {
        Container container = foodContainer(storage).orElse(null);
        if (container == null) return false;
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack stack = container.getItem(i);
            if (!stack.isEmpty() && stack.has(DataComponents.FOOD)) return true;
        }
        return false;
    }

    /**
     * ChestBlockEntity.startOpen is player/menu-only in 1.21.1. A vanilla block event is
     * therefore used for the lid animation without creating a fake player or GUI.
     */
    public boolean openChestVisual(AssignedLocation storage) {
        if (!storage.isIn(level()) || !(level().getBlockEntity(storage.pos()) instanceof ChestBlockEntity chest)) {
            return false;
        }
        int realOpeners = ChestBlockEntity.getOpenCount(level(), storage.pos());
        level().blockEvent(storage.pos(), chest.getBlockState().getBlock(), 1, realOpeners + 1);
        if (realOpeners == 0) {
            level().playSound(null, storage.pos(), SoundEvents.CHEST_OPEN, SoundSource.BLOCKS, 0.5F, 1.0F);
        }
        level().gameEvent(entity, GameEvent.CONTAINER_OPEN, storage.pos());
        return true;
    }

    public void closeChestVisual(AssignedLocation storage) {
        if (!storage.isIn(level()) || !(level().getBlockEntity(storage.pos()) instanceof ChestBlockEntity chest)) return;
        int realOpeners = ChestBlockEntity.getOpenCount(level(), storage.pos());
        level().blockEvent(storage.pos(), chest.getBlockState().getBlock(), 1, realOpeners);
        if (realOpeners == 0) {
            level().playSound(null, storage.pos(), SoundEvents.CHEST_CLOSE, SoundSource.BLOCKS, 0.5F, 1.0F);
        }
        level().gameEvent(entity, GameEvent.CONTAINER_CLOSE, storage.pos());
    }

    public boolean isTaskAvailable(String type) {
        return retryAfter.getOrDefault(type, 0L) <= level().getGameTime();
    }

    public void lookAt(AssignedLocation target) {
        if (target.isIn(level())) {
            Vec3 center = Vec3.atCenterOf(target.pos());
            entity.getLookControl().setLookAt(center.x, center.y, center.z, 20.0F, 20.0F);
        }
    }

    public List<String> debugHistory() { return List.copyOf(debugHistory); }

    public String debugSummary() {
        return "activity=" + activity + ", visual=" + entity.visualState()
                + ", visualItem=" + (entity.visualItem().isEmpty() ? "none" : entity.visualItem().getHoverName().getString())
                + ", activeTask=" + activeTaskType() + ", suspendedTask="
                + suspendedTaskType() + ", hunger=" + String.format("%.1f", record.needs().hunger())
                + ", fatigue=" + String.format("%.1f", record.needs().fatigue())
                + ", inventory=" + record.inventory().summary();
    }

    private void log(String message) {
        String entry = "t=" + level().getGameTime() + " " + message;
        debugHistory.addLast(entry);
        while (debugHistory.size() > DEBUG_HISTORY_LIMIT) debugHistory.removeFirst();
        // Kept in the bounded per-citizen ring buffer exposed by /civ debug citizen.
        // Logging every idle/path transition for every citizen floods dedicated-server logs.
    }

    private void setActivity(CitizenActivityState next) {
        activity = next;
        CitizenVisualState visual = switch (next) {
            case IDLE -> CitizenVisualState.IDLE;
            case MOVING -> CitizenVisualState.WALKING;
            case WORKING -> CitizenVisualState.WORKING;
            case EATING -> CitizenVisualState.EATING;
            case SLEEPING -> CitizenVisualState.SLEEPING;
        };
        entity.setTaskVisualState(visual);
        if (next != CitizenActivityState.EATING) entity.clearVisualItem();
    }

    private static String taskName(@Nullable CitizenTask task) { return task == null ? "none" : task.type(); }
}
