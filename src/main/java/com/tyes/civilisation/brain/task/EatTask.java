package com.tyes.civilisation.brain.task;

import com.tyes.civilisation.brain.CitizenActivityState;
import com.tyes.civilisation.brain.CitizenBrain;
import com.tyes.civilisation.brain.CitizenTask;
import com.tyes.civilisation.brain.TaskCancelReason;
import com.tyes.civilisation.brain.TaskResult;
import com.tyes.civilisation.brain.TaskStartResult;
import com.tyes.civilisation.citizen.AssignedLocation;
import com.tyes.civilisation.citizen.CitizenInventory;
import java.util.OptionalInt;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.Container;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;

public final class EatTask implements CitizenTask {
    public static final String TYPE = "eat";
    private static final int TRANSFER_TICK = 10;
    private static final int CLOSE_CHEST_TICK = 20;
    private int foodSlot = -1;
    private int remainingTicks;
    private boolean acquiringFromChest;
    private int acquisitionTicks;
    private boolean chestVisualOpen;

    public EatTask() { }
    private EatTask(int foodSlot, int remainingTicks, boolean acquiringFromChest, int acquisitionTicks) {
        this.foodSlot = foodSlot;
        this.remainingTicks = remainingTicks;
        this.acquiringFromChest = acquiringFromChest;
        this.acquisitionTicks = acquisitionTicks;
    }

    @Override public String type() { return TYPE; }
    @Override public CitizenActivityState activity() { return CitizenActivityState.EATING; }

    @Override
    public TaskStartResult start(CitizenBrain brain) {
        OptionalInt existing = brain.record().inventory().findFood();
        brain.entity().getNavigation().stop();
        if (existing.isPresent()) {
            foodSlot = existing.getAsInt();
            acquiringFromChest = false;
            chestVisualOpen = false;
            return beginEating(brain) ? TaskStartResult.STARTED : TaskStartResult.INVALID;
        }
        AssignedLocation storage = assignedStorageInReach(brain);
        if (storage == null || !brain.hasFood(storage)) return TaskStartResult.INVALID;
        acquiringFromChest = true;
        acquisitionTicks = Math.max(0, acquisitionTicks);
        chestVisualOpen = brain.openChestVisual(storage);
        brain.lookAt(storage);
        return TaskStartResult.STARTED;
    }

    private boolean takeFoodFromAssignedStorage(CitizenBrain brain) {
        AssignedLocation storage = brain.record().assignments().foodStorage().orElse(null);
        if (storage == null || brain.distanceToSqr(storage) > 16.0D) return false;
        Container container = brain.foodContainer(storage).orElse(null);
        if (container == null) return false;
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack source = container.getItem(i);
            if (source.isEmpty() || !source.has(DataComponents.FOOD)) continue;
            ItemStack one = container.removeItem(i, 1);
            if (brain.record().inventory().add(one)) {
                container.setChanged();
                brain.markDirty();
                return true;
            }
            if (!one.isEmpty()) {
                ItemStack remainderSlot = container.getItem(i);
                if (remainderSlot.isEmpty()) container.setItem(i, one);
                else remainderSlot.grow(one.getCount());
            }
            return false;
        }
        return false;
    }

    @Override
    public TaskResult tick(CitizenBrain brain) {
        if (acquiringFromChest) return tickAcquisition(brain);
        if (foodSlot < 0 || foodSlot >= CitizenInventory.SIZE) return TaskResult.FAILED;
        ItemStack stack = brain.record().inventory().get(foodSlot);
        FoodProperties food = stack.get(DataComponents.FOOD);
        if (stack.isEmpty() || food == null) return TaskResult.FAILED;
        brain.entity().setVisualItem(stack);
        if (--remainingTicks > 0) return TaskResult.RUNNING;
        ItemStack eaten = brain.record().inventory().consumeOne(foodSlot);
        brain.record().needs().eat(food.nutrition());
        food.usingConvertsTo().ifPresent(container -> brain.record().inventory().add(container.copy()));
        brain.entity().clearVisualItem();
        brain.markDirty();
        return eaten.isEmpty() ? TaskResult.FAILED : TaskResult.COMPLETE;
    }

    private TaskResult tickAcquisition(CitizenBrain brain) {
        AssignedLocation storage = assignedStorageInReach(brain);
        if (storage == null) {
            closeChest(brain);
            return TaskResult.BLOCKED;
        }
        brain.lookAt(storage);
        acquisitionTicks++;
        if (acquisitionTicks == TRANSFER_TICK && !takeFoodFromAssignedStorage(brain)) {
            closeChest(brain);
            return TaskResult.BLOCKED;
        }
        if (acquisitionTicks < CLOSE_CHEST_TICK) return TaskResult.RUNNING;
        closeChest(brain);
        acquiringFromChest = false;
        foodSlot = brain.record().inventory().findFood().orElse(-1);
        return beginEating(brain) ? TaskResult.RUNNING : TaskResult.FAILED;
    }

    private boolean beginEating(CitizenBrain brain) {
        if (foodSlot < 0) return false;
        ItemStack stack = brain.record().inventory().get(foodSlot);
        FoodProperties food = stack.get(DataComponents.FOOD);
        if (food == null) return false;
        remainingTicks = remainingTicks > 0 ? remainingTicks : Math.max(1, food.eatDurationTicks());
        brain.entity().setVisualItem(stack);
        return true;
    }

    private AssignedLocation assignedStorageInReach(CitizenBrain brain) {
        AssignedLocation storage = brain.record().assignments().foodStorage().orElse(null);
        return storage != null && brain.distanceToSqr(storage) <= 16.0D ? storage : null;
    }

    private void closeChest(CitizenBrain brain) {
        AssignedLocation storage = brain.record().assignments().foodStorage().orElse(null);
        if (chestVisualOpen && storage != null) brain.closeChestVisual(storage);
        chestVisualOpen = false;
    }

    @Override
    public void cancel(CitizenBrain brain, TaskCancelReason reason) {
        closeChest(brain);
        brain.entity().clearVisualItem();
    }

    @Override
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("foodSlot", foodSlot);
        tag.putInt("remainingTicks", remainingTicks);
        tag.putBoolean("acquiringFromChest", acquiringFromChest);
        tag.putInt("acquisitionTicks", acquisitionTicks);
        return tag;
    }

    public static EatTask load(CompoundTag tag) {
        return new EatTask(tag.getInt("foodSlot"), Math.max(0, tag.getInt("remainingTicks")),
                tag.getBoolean("acquiringFromChest"), tag.getInt("acquisitionTicks"));
    }
}
