package com.tyes.civilisation.brain.task;

import com.tyes.civilisation.brain.*;
import com.tyes.civilisation.citizen.AssignedLocation;
import com.tyes.civilisation.economy.SettlementEconomyService;
import com.tyes.civilisation.economy.inventory.InventoryEndpoint;
import com.tyes.civilisation.economy.inventory.PhysicalInventoryOps;
import com.tyes.civilisation.economy.logistics.*;
import com.tyes.civilisation.entity.CitizenVisualState;
import com.tyes.civilisation.server.CivilisationServices;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/** Carries reserved stacks between two real inventories. Cargo always remains a real citizen stack. */
public final class DeliveryTask implements CitizenTask {
    public static final String TYPE = "economy_delivery";
    private enum Phase { TO_SOURCE, LOAD, TO_DESTINATION, UNLOAD }
    private final UUID settlementId;
    private final UUID deliveryId;
    private Phase phase;
    private int interactionTicks;
    private int pathAge;
    private int retries;
    private Vec3 lastPosition = Vec3.ZERO;

    public DeliveryTask(DeliveryRequest request) {
        this(request.settlementId(), request.id(), request.cargoQuantity() > 0 ? Phase.TO_DESTINATION : Phase.TO_SOURCE, 0, 0, 0);
    }
    private DeliveryTask(UUID settlementId, UUID deliveryId, Phase phase, int interactionTicks, int pathAge, int retries) {
        this.settlementId = settlementId; this.deliveryId = deliveryId; this.phase = phase;
        this.interactionTicks = interactionTicks; this.pathAge = pathAge; this.retries = retries;
    }
    @Override public String type() { return TYPE; }
    @Override public CitizenActivityState activity() { return CitizenActivityState.WORKING; }
    @Override public boolean resumable() { return true; }

    @Override public TaskStartResult start(CitizenBrain brain) {
        DeliveryRequest request = request(brain); if (request == null || !ownedBy(brain, request)) return TaskStartResult.INVALID;
        InventoryEndpoint endpoint = endpoint(brain, phase == Phase.TO_SOURCE || phase == Phase.LOAD ? request.source() : request.destination());
        if (endpoint == null) { if (request.cargoQuantity()==0) economy(brain).failUnloadedDelivery(request); return TaskStartResult.INVALID; }
        lastPosition = brain.entity().position();
        if (phase == Phase.LOAD || phase == Phase.UNLOAD) return TaskStartResult.STARTED;
        if (move(brain, endpoint.location())) return TaskStartResult.STARTED;
        if (request.cargoQuantity()==0) economy(brain).failUnloadedDelivery(request);
        return TaskStartResult.PATH_FAILED;
    }

    @Override public TaskResult tick(CitizenBrain brain) {
        DeliveryRequest request = request(brain); if (request == null || !ownedBy(brain, request)) return TaskResult.FAILED;
        return switch (phase) {
            case TO_SOURCE -> travel(brain, request, request.source(), Phase.LOAD);
            case LOAD -> load(brain, request);
            case TO_DESTINATION -> travel(brain, request, request.destination(), Phase.UNLOAD);
            case UNLOAD -> unload(brain, request);
        };
    }

    private TaskResult travel(CitizenBrain brain, DeliveryRequest request,
                              com.tyes.civilisation.economy.inventory.InventoryEndpointId endpointId, Phase next) {
        InventoryEndpoint endpoint = endpoint(brain, endpointId);
        if (endpoint == null) return next == Phase.UNLOAD ? retryDestination(brain, request) : failUnloaded(brain, request);
        AssignedLocation target = endpoint.location();
        brain.entity().setTaskVisualState(CitizenVisualState.HAULING);
        if (brain.distanceToSqr(target) <= 12.25D) {
            brain.entity().getNavigation().stop(); phase = next; interactionTicks = 0; pathAge = 0; retries = 0;
            return TaskResult.RUNNING;
        }
        if (!target.isIn(brain.level()) || !brain.level().isLoaded(target.pos())) return TaskResult.WAITING;
        if (++pathAge > 1200) return next == Phase.UNLOAD ? retryDestination(brain, request) : failUnloaded(brain, request);
        if (pathAge % 40 == 0) {
            double moved = brain.entity().position().distanceToSqr(lastPosition); lastPosition = brain.entity().position();
            if (moved < 0.04D || brain.entity().getNavigation().isDone()) {
                if (++retries > 3 || !move(brain, target)) return next == Phase.UNLOAD ? retryDestination(brain, request) : failUnloaded(brain, request);
            }
        }
        return TaskResult.RUNNING;
    }

    private TaskResult load(CitizenBrain brain, DeliveryRequest request) {
        InventoryEndpoint source = endpoint(brain, request.source());
        Container container = source == null ? null : source.resolve(brain.level()).orElse(null);
        if (container == null || brain.distanceToSqr(source.location()) > 16.0D) return failUnloaded(brain, request);
        if (economy(brain).reservation(settlementId, request.reservationId()).isEmpty()) {
            return failUnloaded(brain, request);
        }
        brain.lookAt(source.location()); brain.entity().setTaskVisualState(CitizenVisualState.HAULING);
        if (++interactionTicks == 1) brain.openChestVisual(source.location());
        if (interactionTicks < 15) return TaskResult.RUNNING;
        int wanted = Math.min(request.quantity(), brain.record().inventory().capacityFor(sample(container, request)));
        ItemStack extracted = PhysicalInventoryOps.extract(container, request.item(), wanted);
        brain.closeChestVisual(source.location());
        if (extracted.isEmpty()) return failUnloaded(brain, request);
        int extractedAmount = extracted.getCount();
        brain.record().inventory().add(extracted);
        int carried = extractedAmount - extracted.getCount();
        if (!extracted.isEmpty()) PhysicalInventoryOps.insert(container, extracted); // conservative rollback
        if (carried <= 0) return failUnloaded(brain, request);
        request.loaded(carried); economy(brain).reservations().release(settlementId, request.reservationId());
        economy(brain).reconcile(source, container); brain.markDirty();
        phase = Phase.TO_DESTINATION; interactionTicks = 0; pathAge = 0; retries = 0;
        InventoryEndpoint destination = endpoint(brain, request.destination());
        return destination != null && move(brain, destination.location()) ? TaskResult.RUNNING : retryDestination(brain, request);
    }

    private TaskResult unload(CitizenBrain brain, DeliveryRequest request) {
        InventoryEndpoint destination = endpoint(brain, request.destination());
        Container container = destination == null ? null : destination.resolve(brain.level()).orElse(null);
        if (container == null || brain.distanceToSqr(destination.location()) > 16.0D) return retryDestination(brain, request);
        brain.lookAt(destination.location()); brain.entity().setTaskVisualState(CitizenVisualState.HAULING);
        if (++interactionTicks == 1) brain.openChestVisual(destination.location());
        if (interactionTicks < 15) return TaskResult.RUNNING;
        int moved = brain.record().inventory().transferMatchingTo(container, request.item(), request.cargoQuantity());
        brain.closeChestVisual(destination.location());
        if (moved <= 0) return retryDestination(brain, request);
        economy(brain).reconcile(destination, container);
        economy(brain).finishDelivery(request, moved); brain.markDirty();
        return request.state() == DeliveryState.COMPLETE ? TaskResult.COMPLETE : TaskResult.BLOCKED;
    }

    private TaskResult retryDestination(CitizenBrain brain, DeliveryRequest request) {
        InventoryEndpoint alternate = economy(brain).alternateStorage(settlementId, request.destination()).orElse(null);
        if (alternate != null) {
            request.reroute(alternate.id()); phase = Phase.TO_DESTINATION; pathAge = retries = 0;
            move(brain, alternate.location()); return TaskResult.RUNNING;
        }
        request.setState(DeliveryState.BLOCKED); return TaskResult.WAITING; // retain physical cargo and ownership
    }
    private TaskResult failUnloaded(CitizenBrain brain, DeliveryRequest request) { economy(brain).failUnloadedDelivery(request); return TaskResult.BLOCKED; }

    private ItemStack sample(Container container, DeliveryRequest request) {
        for (int i = 0; i < container.getContainerSize(); i++) if (request.item().matches(container.getItem(i))) return container.getItem(i);
        return ItemStack.EMPTY;
    }
    private boolean move(CitizenBrain brain, AssignedLocation target) { return brain.entity().getNavigation().moveTo(
            target.pos().getX() + .5D, target.pos().getY(), target.pos().getZ() + .5D, 2, 1.0D); }
    private SettlementEconomyService economy(CitizenBrain brain) { return CivilisationServices.economy(brain.level().getServer()); }
    private DeliveryRequest request(CitizenBrain brain) { return economy(brain).delivery(settlementId, deliveryId).orElse(null); }
    private InventoryEndpoint endpoint(CitizenBrain brain, com.tyes.civilisation.economy.inventory.InventoryEndpointId id) {
        return economy(brain).endpoint(settlementId, id).orElse(null);
    }
    private boolean ownedBy(CitizenBrain brain, DeliveryRequest request) { return request.courierId().filter(brain.record().citizenId()::equals).isPresent(); }
    @Override public boolean stillValid(CitizenBrain brain) { DeliveryRequest r = request(brain); return r != null && ownedBy(brain, r) && r.state() != DeliveryState.COMPLETE && r.state() != DeliveryState.CANCELLED; }
    @Override public void resume(CitizenBrain brain) { DeliveryRequest r = request(brain); if (r != null) r.assign(brain.record().citizenId()); }
    @Override public void cancel(CitizenBrain brain, TaskCancelReason reason) {
        brain.entity().getNavigation().stop(); DeliveryRequest r = request(brain);
        if (r != null && r.cargoQuantity() == 0) economy(brain).releaseDelivery(r);
    }
    @Override public CompoundTag save() { CompoundTag t = new CompoundTag(); t.putUUID("settlement", settlementId); t.putUUID("delivery", deliveryId); t.putString("phase", phase.name()); t.putInt("interaction", interactionTicks); t.putInt("pathAge", pathAge); t.putInt("retries", retries); return t; }
    public static DeliveryTask load(CompoundTag t) { return new DeliveryTask(t.getUUID("settlement"), t.getUUID("delivery"), Phase.valueOf(t.getString("phase")), t.getInt("interaction"), t.getInt("pathAge"), t.getInt("retries")); }
}
