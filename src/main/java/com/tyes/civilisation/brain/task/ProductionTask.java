package com.tyes.civilisation.brain.task;

import com.tyes.civilisation.brain.*;
import com.tyes.civilisation.citizen.AssignedLocation;
import com.tyes.civilisation.economy.SettlementEconomyService;
import com.tyes.civilisation.economy.inventory.*;
import com.tyes.civilisation.economy.item.ItemKey;
import com.tyes.civilisation.economy.production.*;
import com.tyes.civilisation.economy.workshop.Workshop;
import com.tyes.civilisation.entity.CitizenVisualState;
import com.tyes.civilisation.server.CivilisationServices;
import java.util.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.phys.Vec3;

/** Executes real recipes at a claimed materialised workshop. */
public final class ProductionTask implements CitizenTask {
    public static final String TYPE = "economy_production";
    private final UUID settlementId;
    private final UUID requestId;
    private UUID workshopId;
    private int workTicks;
    private int pathAge;
    private Vec3 lastPosition = Vec3.ZERO;

    public ProductionTask(ProductionRequest request) { this(request.settlementId(), request.requestId(), null, 0, 0); }
    private ProductionTask(UUID settlementId, UUID requestId, UUID workshopId, int workTicks, int pathAge) {
        this.settlementId = settlementId; this.requestId = requestId; this.workshopId = workshopId;
        this.workTicks = workTicks; this.pathAge = pathAge;
    }
    @Override public String type() { return TYPE; }
    @Override public CitizenActivityState activity() { return CitizenActivityState.WORKING; }
    @Override public boolean resumable() { return true; }

    @Override public TaskStartResult start(CitizenBrain brain) {
        ProductionRequest request = request(brain); if (request == null || !ownedBy(brain, request)) return TaskStartResult.INVALID;
        Workshop workshop = workshop(brain, request);
        if (workshop == null || !validWorkstation(brain, workshop, request)) {
            request.block(workshop == null ? ProductionBlocker.MISSING_WORKSTATION : ProductionBlocker.INVALID_WORKSTATION);
            economy(brain).releaseProduction(request, brain.record().citizenId());
            return TaskStartResult.INVALID;
        }
        workshopId = workshop.id(); lastPosition = brain.entity().position();
        if (move(brain, workshop.workstation())) return TaskStartResult.STARTED;
        economy(brain).releaseProduction(request, brain.record().citizenId()); return TaskStartResult.PATH_FAILED;
    }

    @Override public TaskResult tick(CitizenBrain brain) {
        ProductionRequest request = request(brain); Workshop workshop = workshopById(brain);
        if (request == null || workshop == null || !ownedBy(brain, request)) return TaskResult.FAILED;
        if (!validWorkstation(brain, workshop, request)) { request.block(ProductionBlocker.INVALID_WORKSTATION); return TaskResult.BLOCKED; }
        AssignedLocation target = workshop.workstation();
        if (brain.distanceToSqr(target) > 9.0D) {
            brain.entity().setTaskVisualState(CitizenVisualState.WALKING);
            if (!target.isIn(brain.level()) || !brain.level().isLoaded(target.pos())) return TaskResult.WAITING;
            if (++pathAge > 1200) return TaskResult.PATH_FAILED;
            if (pathAge % 40 == 0 && (brain.entity().position().distanceToSqr(lastPosition) < .04D || brain.entity().getNavigation().isDone())) {
                lastPosition = brain.entity().position(); if (!move(brain, target)) return TaskResult.PATH_FAILED;
            }
            return TaskResult.RUNNING;
        }
        brain.entity().getNavigation().stop(); brain.lookAt(target); brain.entity().setTaskVisualState(CitizenVisualState.WORKING);
        if (++workTicks % 10 == 0) brain.entity().swing(InteractionHand.MAIN_HAND);
        if (workTicks < 40) return TaskResult.RUNNING;
        return request.method() == ProductionMethod.SMELT || request.method() == ProductionMethod.SMOKE
                || request.method() == ProductionMethod.BLAST ? smelt(brain, request, workshop) : craft(brain, request, workshop);
    }

    private TaskResult craft(CitizenBrain brain, ProductionRequest request, Workshop workshop) {
        RecipeHolder<?> recipe = recipe(brain, request); Containers c = containers(brain, workshop);
        if (recipe == null || c == null) { request.block(recipe == null ? ProductionBlocker.MISSING_RECIPE : ProductionBlocker.INVALID_DESTINATION); return TaskResult.BLOCKED; }
        ItemStack output = recipe.value().getResultItem(brain.level().registryAccess()).copy();
        if (output.isEmpty() || PhysicalInventoryOps.capacity(c.output, output) < output.getCount()) { request.block(ProductionBlocker.STORAGE_FULL); return TaskResult.BLOCKED; }
        List<ItemStack> consumed = new ArrayList<>();
        for (Ingredient ingredient : recipe.value().getIngredients()) {
            if (ingredient.isEmpty()) continue;
            ItemStack taken = extractIngredient(c.input, ingredient);
            if (taken.isEmpty()) { consumed.forEach(s -> PhysicalInventoryOps.insert(c.input, s)); request.block(ProductionBlocker.MISSING_INPUT); return TaskResult.BLOCKED; }
            consumed.add(taken);
        }
        ItemStack remainder = output.copy(); int inserted = PhysicalInventoryOps.insert(c.output, remainder);
        if (inserted != output.getCount()) { // preflight should make this unreachable; rollback without creating items
            consumed.forEach(s -> PhysicalInventoryOps.insert(c.input, s));
            if (inserted > 0) PhysicalInventoryOps.extract(c.output, ItemKey.exact(output), inserted);
            request.block(ProductionBlocker.STORAGE_FULL); return TaskResult.BLOCKED;
        }
        economy(brain).index().reconcileEndpoint(settlementId, workshop.input(), PhysicalInventoryOps.snapshot(c.input));
        economy(brain).index().reconcileEndpoint(settlementId, workshop.output(), PhysicalInventoryOps.snapshot(c.output));
        CivilisationServices.knowledge(brain.level().getServer()).recordRecipeUse(brain.record(), recipe.id());
        economy(brain).productionCompleted(request, workshop, inserted); return TaskResult.COMPLETE;
    }

    private TaskResult smelt(CitizenBrain brain, ProductionRequest request, Workshop workshop) {
        RecipeHolder<?> recipe = recipe(brain, request); Containers c = containers(brain, workshop);
        if (recipe == null || c == null || !(brain.level().getBlockEntity(workshop.workstation().pos()) instanceof AbstractFurnaceBlockEntity furnace)) {
            request.block(ProductionBlocker.MISSING_WORKSTATION); return TaskResult.BLOCKED;
        }
        ItemStack expected = recipe.value().getResultItem(brain.level().registryAccess());
        if (request.output().matches(furnace.getItem(2)) && !furnace.getItem(2).isEmpty()) {
            if (PhysicalInventoryOps.capacity(c.output, furnace.getItem(2)) <= 0) { request.block(ProductionBlocker.STORAGE_FULL); return TaskResult.BLOCKED; }
            ItemStack made = furnace.removeItem(2, Math.min(request.remainingQuantity(), furnace.getItem(2).getCount()));
            int amount = PhysicalInventoryOps.insert(c.output, made); furnace.setChanged();
            economy(brain).index().reconcileEndpoint(settlementId, workshop.output(), PhysicalInventoryOps.snapshot(c.output));
            CivilisationServices.knowledge(brain.level().getServer()).recordRecipeUse(brain.record(), recipe.id());
            economy(brain).productionCompleted(request, workshop, amount); return TaskResult.COMPLETE;
        }
        if (!furnace.getItem(0).isEmpty() || !furnace.getItem(2).isEmpty()) { request.block(ProductionBlocker.WORKSTATION_BUSY); return TaskResult.BLOCKED; }
        Ingredient raw = recipe.value().getIngredients().isEmpty() ? Ingredient.EMPTY : recipe.value().getIngredients().getFirst();
        ItemStack input = extractIngredient(c.input, raw);
        ItemStack fuel = extractFuel(c.input);
        if (input.isEmpty() || fuel.isEmpty()) {
            if (!input.isEmpty()) PhysicalInventoryOps.insert(c.input, input);
            if (!fuel.isEmpty()) PhysicalInventoryOps.insert(c.input, fuel);
            request.block(fuel.isEmpty() ? ProductionBlocker.MISSING_FUEL : ProductionBlocker.MISSING_INPUT); return TaskResult.BLOCKED;
        }
        furnace.setItem(0, input); furnace.setItem(1, fuel); furnace.setChanged();
        economy(brain).awaitMachine(request, workshop, brain.record().citizenId()); return TaskResult.COMPLETE;
    }

    private ItemStack extractIngredient(Container input, Ingredient ingredient) {
        for (int i = 0; i < input.getContainerSize(); i++) if (ingredient.test(input.getItem(i)))
            return PhysicalInventoryOps.extract(input, ItemKey.exact(input.getItem(i)), 1);
        return ItemStack.EMPTY;
    }
    private ItemStack extractFuel(Container input) {
        for (int i = 0; i < input.getContainerSize(); i++) if (AbstractFurnaceBlockEntity.isFuel(input.getItem(i)))
            return PhysicalInventoryOps.extract(input, ItemKey.exact(input.getItem(i)), 1);
        return ItemStack.EMPTY;
    }
    private RecipeHolder<?> recipe(CitizenBrain brain, ProductionRequest request) {
        ResourceLocation id = request.recipeId().orElse(null); if (id == null) return null;
        return brain.level().getServer().getRecipeManager().byKey(id).orElse(null);
    }
    private Containers containers(CitizenBrain brain, Workshop w) {
        InventoryEndpoint in = economy(brain).endpoint(settlementId, w.input()).orElse(null);
        InventoryEndpoint out = economy(brain).endpoint(settlementId, w.output()).orElse(null);
        if (in == null || out == null) return null;
        Container a = in.resolve(brain.level()).orElse(null), b = out.resolve(brain.level()).orElse(null);
        return a == null || b == null ? null : new Containers(a, b);
    }
    private record Containers(Container input, Container output) { }
    private boolean validWorkstation(CitizenBrain brain, Workshop w, ProductionRequest r) {
        if (!w.workstation().isIn(brain.level()) || !brain.level().isLoaded(w.workstation().pos())) return false;
        return r.method() == ProductionMethod.CRAFT ? brain.level().getBlockState(w.workstation().pos()).is(Blocks.CRAFTING_TABLE)
                : brain.level().getBlockEntity(w.workstation().pos()) instanceof AbstractFurnaceBlockEntity;
    }
    private boolean move(CitizenBrain brain, AssignedLocation target) { return brain.entity().getNavigation().moveTo(target.pos().getX()+.5D, target.pos().getY(), target.pos().getZ()+.5D, 2, 1D); }
    private SettlementEconomyService economy(CitizenBrain brain) { return CivilisationServices.economy(brain.level().getServer()); }
    private ProductionRequest request(CitizenBrain brain) { return economy(brain).production(settlementId, requestId).orElse(null); }
    private Workshop workshop(CitizenBrain brain, ProductionRequest r) { return workshopId == null ? economy(brain).workshopFor(r, brain.record().citizenId(), brain.level().getGameTime()).orElse(null) : workshopById(brain); }
    private Workshop workshopById(CitizenBrain brain) { return workshopId == null ? null : economy(brain).data().forSettlement(settlementId).workshop(workshopId).orElse(null); }
    private boolean ownedBy(CitizenBrain brain, ProductionRequest r) { return r.claimedBy().filter(brain.record().citizenId()::equals).isPresent(); }
    @Override public boolean stillValid(CitizenBrain brain) { ProductionRequest r=request(brain); return r!=null && ownedBy(brain,r) && r.state()==ProductionState.IN_PROGRESS; }
    @Override public void resume(CitizenBrain brain) { }
    @Override public void cancel(CitizenBrain brain, TaskCancelReason reason) { brain.entity().getNavigation().stop(); ProductionRequest r=request(brain); if(r!=null) economy(brain).releaseProduction(r,brain.record().citizenId()); }
    @Override public CompoundTag save() { CompoundTag t=new CompoundTag(); t.putUUID("settlement",settlementId); t.putUUID("request",requestId); if(workshopId!=null)t.putUUID("workshop",workshopId);t.putInt("workTicks",workTicks);t.putInt("pathAge",pathAge);return t; }
    public static ProductionTask load(CompoundTag t){return new ProductionTask(t.getUUID("settlement"),t.getUUID("request"),t.hasUUID("workshop")?t.getUUID("workshop"):null,t.getInt("workTicks"),t.getInt("pathAge"));}
}
