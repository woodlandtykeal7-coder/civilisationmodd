package com.tyes.civilisation.brain.task;

import com.tyes.civilisation.brain.CitizenActivityState;
import com.tyes.civilisation.brain.CitizenBrain;
import com.tyes.civilisation.brain.CitizenTask;
import com.tyes.civilisation.brain.TaskCancelReason;
import com.tyes.civilisation.brain.TaskResult;
import com.tyes.civilisation.brain.TaskStartResult;
import com.tyes.civilisation.citizen.AssignedLocation;
import com.tyes.civilisation.entity.CitizenVisualState;
import com.tyes.civilisation.persistence.SettlementSavedData;
import com.tyes.civilisation.server.CivilisationServices;
import com.tyes.civilisation.settlement.SettlementState;
import com.tyes.civilisation.work.SettlementWorkBoard;
import com.tyes.civilisation.work.WorkAssignment;
import com.tyes.civilisation.work.WorkType;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.Vec3;
import net.minecraft.tags.ItemTags;

/** Executes one claimed physical target, then carries its real drops to registered storage. */
public final class SettlementWorkTask implements CitizenTask {
    public static final String TYPE = "settlement_work";
    private enum Phase { TO_TARGET, HARVEST, TO_STORAGE, DEPOSIT }
    private final WorkAssignment assignment;
    private Phase phase;
    private int actionTicks;
    private int pathAge;
    private int pathRetries;
    private Vec3 lastPosition = Vec3.ZERO;

    public SettlementWorkTask(WorkAssignment assignment) { this(assignment, Phase.TO_TARGET, 0, 0, 0); }
    private SettlementWorkTask(WorkAssignment assignment, Phase phase, int actionTicks, int pathAge, int pathRetries) {
        this.assignment = assignment;
        this.phase = phase;
        this.actionTicks = actionTicks;
        this.pathAge = pathAge;
        this.pathRetries = pathRetries;
    }
    @Override public String type() { return TYPE; }
    @Override public CitizenActivityState activity() { return CitizenActivityState.WORKING; }
    @Override public boolean resumable() { return true; }
    public boolean producesFood() { return assignment.type() == WorkType.FORAGE || assignment.type() == WorkType.FARM; }

    @Override public TaskStartResult start(CitizenBrain brain) {
        if (!stillValid(brain)) return TaskStartResult.INVALID;
        if ((phase == Phase.TO_TARGET || phase == Phase.HARVEST) && !claim(brain)) return TaskStartResult.INVALID;
        lastPosition = brain.entity().position();
        if (phase == Phase.TO_TARGET) return move(brain, assignment.target()) ? TaskStartResult.STARTED : TaskStartResult.PATH_FAILED;
        if (phase == Phase.TO_STORAGE) {
            AssignedLocation storage = storage(brain);
            return storage != null && move(brain, storage) ? TaskStartResult.STARTED : TaskStartResult.PATH_FAILED;
        }
        return TaskStartResult.STARTED;
    }

    @Override public TaskResult tick(CitizenBrain brain) {
        return switch (phase) {
            case TO_TARGET -> travel(brain, assignment.target(), Phase.HARVEST);
            case HARVEST -> harvest(brain);
            case TO_STORAGE -> {
                AssignedLocation storage = storage(brain);
                yield storage == null ? fail(brain, TaskResult.BLOCKED) : travel(brain, storage, Phase.DEPOSIT);
            }
            case DEPOSIT -> deposit(brain);
        };
    }

    private TaskResult travel(CitizenBrain brain, AssignedLocation target, Phase next) {
        brain.entity().setTaskVisualState(CitizenVisualState.WALKING);
        if (!target.isIn(brain.level()) || !brain.level().isLoaded(target.pos())) return fail(brain, TaskResult.BLOCKED);
        double range = next == Phase.HARVEST ? 6.25D : 12.25D;
        if (brain.entity().position().distanceToSqr(Vec3.atCenterOf(target.pos())) <= range) {
            brain.entity().getNavigation().stop();
            phase = next;
            actionTicks = 0;
            return TaskResult.RUNNING;
        }
        pathAge++;
        if (pathAge > 1200) return fail(brain, TaskResult.PATH_FAILED);
        if (pathAge % 40 == 0) {
            double moved = brain.entity().position().distanceToSqr(lastPosition);
            lastPosition = brain.entity().position();
            if (moved < 0.04D || brain.entity().getNavigation().isDone()) {
                if (++pathRetries > 2 || !move(brain, target)) return fail(brain, TaskResult.PATH_FAILED);
            }
        }
        return TaskResult.RUNNING;
    }

    private TaskResult harvest(CitizenBrain brain) {
        BlockPos pos = assignment.target().pos();
        BlockState state = brain.level().getBlockState(pos);
        if (!SettlementWorkBoard.valid(assignment.type(), state)) return fail(brain, TaskResult.BLOCKED);
        if (assignment.type() == WorkType.LUMBER && !brain.record().inventory().has(ItemTags.AXES)) {
            return fail(brain, TaskResult.BLOCKED);
        }
        brain.lookAt(assignment.target());
        brain.entity().setTaskVisualState(assignment.type() == WorkType.LUMBER
                ? CitizenVisualState.CHOPPING : CitizenVisualState.HARVESTING);
        if (++actionTicks % 10 == 0) brain.entity().swing(InteractionHand.MAIN_HAND);
        int required = assignment.type() == WorkType.LUMBER ? 60 : 30;
        if (actionTicks < required) return TaskResult.RUNNING;

        BlockEntity blockEntity = brain.level().getBlockEntity(pos);
        List<ItemStack> drops = Block.getDrops(state, brain.level(), pos, blockEntity, brain.entity(), ItemStack.EMPTY);
        if (assignment.type() == WorkType.FARM && state.getBlock() instanceof CropBlock crop) {
            ItemStack seed = crop.getCloneItemStack(brain.level(), pos, state);
            boolean replanted = consumeDrop(drops, seed);
            brain.level().setBlock(pos, replanted ? crop.getStateForAge(0) : net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
        } else if (assignment.type() == WorkType.FORAGE) {
            brain.level().setBlock(pos, state.setValue(SweetBerryBushBlock.AGE, 1), 3);
        } else {
            brain.level().setBlock(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
        }
        for (ItemStack drop : drops) {
            ItemStack remaining = drop.copy();
            brain.record().inventory().add(remaining);
            if (!remaining.isEmpty()) brain.level().addFreshEntity(new ItemEntity(brain.level(),
                    pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D, remaining));
        }
        if (assignment.type() == WorkType.LUMBER) brain.record().inventory().damageFirst(ItemTags.AXES, 1, brain.entity());
        CivilisationServices.knowledge(brain.level().getServer()).recordWork(brain.record(), assignment.type());
        brain.markDirty();
        release(brain);
        AssignedLocation storage = storage(brain);
        if (storage == null || brain.record().inventory().isEmpty()) return TaskResult.COMPLETE;
        phase = Phase.TO_STORAGE;
        pathAge = 0;
        pathRetries = 0;
        lastPosition = brain.entity().position();
        return move(brain, storage) ? TaskResult.RUNNING : TaskResult.PATH_FAILED;
    }

    private TaskResult deposit(CitizenBrain brain) {
        AssignedLocation storage = storage(brain);
        if (storage == null || brain.distanceToSqr(storage) > 16.0D) return failDeposit(brain, storage);
        BlockEntity blockEntity = brain.level().getBlockEntity(storage.pos());
        if (!(blockEntity instanceof Container container)) return failDeposit(brain, storage);
        brain.lookAt(storage);
        brain.entity().setTaskVisualState(CitizenVisualState.HAULING);
        if (++actionTicks == 1) brain.openChestVisual(storage);
        if (actionTicks < 15) return TaskResult.RUNNING;
        int moved = brain.record().inventory().transferResourcesTo(container);
        brain.closeChestVisual(storage);
        if (moved > 0) brain.markDirty();
        return TaskResult.COMPLETE;
    }

    private TaskResult failDeposit(CitizenBrain brain, AssignedLocation storage) {
        if (storage != null) brain.closeChestVisual(storage);
        return TaskResult.BLOCKED;
    }

    private static boolean consumeDrop(List<ItemStack> drops, ItemStack wanted) {
        if (wanted.isEmpty()) return false;
        for (ItemStack stack : drops) if (ItemStack.isSameItemSameComponents(stack, wanted) && !stack.isEmpty()) {
            stack.shrink(1);
            return true;
        }
        return false;
    }
    private boolean move(CitizenBrain brain, AssignedLocation target) {
        BlockPos p = target.pos();
        return brain.entity().getNavigation().moveTo(p.getX() + 0.5D, p.getY(), p.getZ() + 0.5D, 2, 1.0D);
    }
    private AssignedLocation storage(CitizenBrain brain) {
        SettlementState settlement = SettlementSavedData.get(brain.level().getServer()).registry()
                .find(assignment.settlementId()).orElse(null);
        return settlement == null ? null : settlement.primaryStorage().orElse(null);
    }
    private boolean claim(CitizenBrain brain) {
        return CivilisationServices.workBoard(brain.level().getServer()).claimSpecific(
                brain.record().citizenId(), assignment, brain.level().getGameTime());
    }
    private void release(CitizenBrain brain) {
        CivilisationServices.workBoard(brain.level().getServer()).release(brain.record().citizenId(), assignment);
    }
    private TaskResult fail(CitizenBrain brain, TaskResult result) { release(brain); return result; }
    @Override public boolean stillValid(CitizenBrain brain) {
        if (phase == Phase.TO_STORAGE || phase == Phase.DEPOSIT) return storage(brain) != null;
        return assignment.target().isIn(brain.level()) && brain.level().isLoaded(assignment.target().pos())
                && SettlementWorkBoard.valid(assignment.type(), brain.level().getBlockState(assignment.target().pos()));
    }
    @Override public void resume(CitizenBrain brain) {
        if (phase == Phase.TO_TARGET || phase == Phase.HARVEST) claim(brain);
    }
    @Override public void cancel(CitizenBrain brain, TaskCancelReason reason) {
        brain.entity().getNavigation().stop();
        AssignedLocation storage = storage(brain);
        if (phase == Phase.DEPOSIT && storage != null) brain.closeChestVisual(storage);
        release(brain);
    }
    @Override public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("settlementId", assignment.settlementId());
        tag.putString("unitId", assignment.unitId());
        tag.putString("workType", assignment.type().name());
        tag.put("target", assignment.target().save());
        tag.putString("phase", phase.name());
        tag.putInt("actionTicks", actionTicks);
        tag.putInt("pathAge", pathAge);
        tag.putInt("pathRetries", pathRetries);
        return tag;
    }
    public static SettlementWorkTask load(CompoundTag tag) {
        WorkAssignment assignment = new WorkAssignment(tag.getUUID("settlementId"), tag.getString("unitId"),
                WorkType.valueOf(tag.getString("workType")), AssignedLocation.load(tag.getCompound("target")).orElseThrow());
        return new SettlementWorkTask(assignment, Phase.valueOf(tag.getString("phase")), tag.getInt("actionTicks"),
                tag.getInt("pathAge"), tag.getInt("pathRetries"));
    }
}
