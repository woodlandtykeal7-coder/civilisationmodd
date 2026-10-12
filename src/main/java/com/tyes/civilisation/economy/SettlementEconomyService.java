package com.tyes.civilisation.economy;

import com.tyes.civilisation.CivilisationMod;
import com.tyes.civilisation.citizen.AssignedLocation;
import com.tyes.civilisation.citizen.CitizenRecord;
import com.tyes.civilisation.economy.forecast.DemandForecast;
import com.tyes.civilisation.economy.forecast.DemandForecastService;
import com.tyes.civilisation.economy.inventory.InventoryEndpoint;
import com.tyes.civilisation.economy.inventory.InventoryEndpointId;
import com.tyes.civilisation.economy.inventory.InventoryEndpointType;
import com.tyes.civilisation.economy.inventory.InventoryOwnership;
import com.tyes.civilisation.economy.inventory.PhysicalInventoryOps;
import com.tyes.civilisation.economy.item.ItemKey;
import com.tyes.civilisation.economy.logistics.*;
import com.tyes.civilisation.economy.production.*;
import com.tyes.civilisation.economy.reservation.*;
import com.tyes.civilisation.economy.shortage.ShortageService;
import com.tyes.civilisation.economy.workshop.*;
import com.tyes.civilisation.persistence.EconomySavedData;
import com.tyes.civilisation.persistence.SettlementSavedData;
import com.tyes.civilisation.persistence.CitizenSavedData;
import com.tyes.civilisation.brain.task.ProductionTask;
import com.tyes.civilisation.construction.project.ConstructionPriority;
import com.tyes.civilisation.settlement.SettlementState;
import com.tyes.civilisation.settlement.WorkRole;
import com.tyes.civilisation.work.SettlementResourceIndex;
import java.util.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;

/** Shared settlement-level planner/index coordinator. Physical tasks remain CitizenBrain-owned. */
public final class SettlementEconomyService {
    private static final long RESERVATION_TTL = 2400L;
    private final MinecraftServer server;
    private final EconomySavedData data;
    private final SettlementResourceIndex index;
    private final ResourceReservationManager reservations;
    private final DeliveryRequestManager deliveries;
    private final ProductionRequestManager production;
    private final ProductionPlanner planner;
    private final DemandForecastService forecasts = new DemandForecastService();
    private final ShortageService shortages = new ShortageService();
    private final DeadlockMonitor deadlocks = new DeadlockMonitor();

    public SettlementEconomyService(MinecraftServer server, SettlementResourceIndex index) {
        this.server = server; this.index = index; this.data = EconomySavedData.get(server);
        this.reservations = new ResourceReservationManager(data, index);
        this.deliveries = new DeliveryRequestManager(data);
        this.production = new ProductionRequestManager(data);
        this.planner = new ProductionPlanner(server, data, index, new RecipeCache(server));
    }
    public EconomySavedData data() { return data; } public SettlementResourceIndex index() { return index; }
    public ResourceReservationManager reservations() { return reservations; }
    public DeliveryRequestManager deliveries() { return deliveries; }
    public ProductionRequestManager production() { return production; }

    public void tick() {
        long now = server.overworld().getGameTime();
        ensureKnownSettlementStorage();
        for (SettlementEconomyState state : data.all()) for (Workshop workshop : state.workshops()) workshop.expire(now);
        reservations.expire(now);
        reconcileStaggered(now);
        if (now % 100L == 0L) {
            for (SettlementEconomyState state : data.all()) process(state, now);
            updateForecasts(); data.setDirty();
        }
    }

    public InventoryEndpoint registerEndpoint(UUID settlementId, AssignedLocation location,
                                              InventoryEndpointType type, InventoryOwnership ownership) {
        SettlementEconomyState state = data.forSettlement(settlementId);
        InventoryEndpoint existing = state.endpoints().stream().filter(e -> e.location().equals(location) && e.type() == type).findFirst().orElse(null);
        if (existing != null) return existing;
        InventoryEndpoint endpoint = new InventoryEndpoint(InventoryEndpointId.create(), settlementId, location, type, ownership);
        state.putEndpoint(endpoint);
        ServerLevel level = levelFor(location);
        if (level != null) endpoint.resolve(level).ifPresent(container -> index.reconcileEndpoint(
                settlementId, endpoint.id(), PhysicalInventoryOps.snapshot(container)));
        data.setDirty(); return endpoint;
    }

    public Workshop registerWorkshop(UUID settlementId, WorkshopType type, AssignedLocation workstation,
                                     InventoryEndpoint input, InventoryEndpoint output) {
        SettlementEconomyState state = data.forSettlement(settlementId);
        Workshop existing = state.workshops().stream().filter(w -> w.workstation().equals(workstation) && w.type() == type).findFirst().orElse(null);
        if (existing != null) return existing;
        Workshop workshop = new Workshop(UUID.randomUUID(), settlementId, type, workstation, input.id(), output.id());
        state.putWorkshop(workshop); data.setDirty(); return workshop;
    }

    public Optional<InventoryEndpoint> endpoint(UUID settlementId, InventoryEndpointId id) { return data.forSettlement(settlementId).endpoint(id); }
    public Optional<DeliveryRequest> delivery(UUID settlementId, UUID id) { return data.forSettlement(settlementId).delivery(id); }
    public Optional<ProductionRequest> production(UUID settlementId, UUID id) { return data.forSettlement(settlementId).production(id); }
    public Optional<ResourceReservation> reservation(UUID settlementId, UUID id) { return data.forSettlement(settlementId).reservation(id); }
    public Optional<ResourceReservation> reserveAxe(CitizenRecord citizen) {
        UUID settlement = citizen.settlementId().orElse(null); if (settlement == null) return Optional.empty();
        for (ItemStack stack : List.of(new ItemStack(Items.STONE_AXE), new ItemStack(Items.IRON_AXE), new ItemStack(Items.WOODEN_AXE))) {
            ItemKey key = ItemKey.exact(stack);
            List<ResourceReservation> made = reservations.reservePartial(settlement, citizen.citizenId(), key, 1,
                    ReservationPurpose.TOOL_REPLACEMENT, server.overworld().getGameTime(), RESERVATION_TTL);
            if (!made.isEmpty()) return Optional.of(made.getFirst());
        }
        return Optional.empty();
    }
    public Optional<DeliveryRequest> claimDelivery(CitizenRecord citizen) {
        return citizen.settlementId().flatMap(id -> deliveries.claim(id, citizen.citizenId()));
    }
    public Optional<ProductionRequest> claimProduction(CitizenRecord citizen) {
        return citizen.settlementId().flatMap(id -> production.claimReady(id, citizen.citizenId()));
    }
    public Optional<Workshop> workshopFor(ProductionRequest request, UUID citizenId, long now) {
        WorkshopType type = request.method().usesFurnace() ? WorkshopType.SMELTING : WorkshopType.GENERAL_CRAFTING;
        return data.forSettlement(request.settlementId()).workshops().stream().filter(w -> w.type() == type)
                .filter(w -> w.claim(citizenId, now, 1200L)).findFirst();
    }
    public Optional<ServerLevel> level(InventoryEndpoint endpoint) {
        ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, endpoint.location().dimension());
        return Optional.ofNullable(server.getLevel(key));
    }
    public Optional<InventoryEndpoint> alternateStorage(UUID settlementId, InventoryEndpointId excluded) {
        return data.forSettlement(settlementId).endpoints().stream().filter(e -> !e.id().equals(excluded)
                && e.type() == InventoryEndpointType.SETTLEMENT_STORAGE && e.ownership().permitsAutomaticUse()).findFirst();
    }
    public void reconcile(InventoryEndpoint endpoint, Container container) {
        endpoint.touch();
        index.reconcileEndpoint(endpoint.settlementId(), endpoint.id(), PhysicalInventoryOps.snapshot(container));
        data.setDirty();
    }

    public ProductionRequest request(UUID settlementId, ItemKey output, int amount) {
        return production.create(settlementId, output, amount, ProductionPriority.NORMAL, ProductionReason.MANUAL_TEST);
    }

    /** Bridges a construction requirement into real reservations/deliveries and, for any deficit, production. */
    public void requestConstructionSupply(UUID settlementId, UUID projectId, InventoryEndpointId destination,
                                          ItemKey item, int amount) {
        requestConstructionSupply(settlementId, projectId, destination, item, amount, ConstructionPriority.HIGH);
    }
    public void requestConstructionSupply(UUID settlementId, UUID projectId, InventoryEndpointId destination,
                                          ItemKey item, int amount, ConstructionPriority priority) {
        long now = server.overworld().getGameTime();
        DeliveryPriority deliveryPriority = DeliveryPriority.valueOf(priority.name());
        ProductionPriority productionPriority = ProductionPriority.valueOf(priority.name());
        SettlementEconomyState state = data.forSettlement(settlementId);
        state.deliveryRequests().stream().filter(d -> d.ownerId().equals(projectId)
                && d.state() != DeliveryState.COMPLETE && d.state() != DeliveryState.CANCELLED)
                .forEach(d -> d.promote(deliveryPriority));
        List<ResourceReservation> made = reservations.reservePartial(settlementId, projectId, item, amount,
                ReservationPurpose.CONSTRUCTION, now, 24000L);
        int reserved = 0;
        for (ResourceReservation reservation : made) {
            reserved += reservation.quantity();
            deliveries.create(settlementId, reservation.source(), destination, item, reservation.quantity(),
                    deliveryPriority, DeliveryPurpose.CONSTRUCTION, projectId, reservation.reservationId());
        }
        int productionDeficit = amount - reserved;
        if (productionDeficit > 0 && !isRawGatheredMaterial(item)) {
            ProductionRequest existing = state.productionRequests().stream()
                    .filter(r -> r.output().itemId().equals(item.itemId())
                            && r.reason() == ProductionReason.CONSTRUCTION
                            && r.state() != ProductionState.COMPLETE && r.state() != ProductionState.CANCELLED)
                    .findFirst().orElse(null);
            if (existing == null) production.create(settlementId, item, productionDeficit,
                    productionPriority, ProductionReason.CONSTRUCTION);
            else { existing.promote(productionPriority); existing.ensureRemainingDemand(productionDeficit); data.setDirty(); }
        }
    }

    public void promoteConstructionSupply(UUID settlementId, UUID projectId, ItemKey item,
                                          ConstructionPriority priority) {
        DeliveryPriority deliveryPriority = DeliveryPriority.valueOf(priority.name());
        ProductionPriority productionPriority = ProductionPriority.valueOf(priority.name());
        SettlementEconomyState state = data.forSettlement(settlementId);
        state.deliveryRequests().stream().filter(d -> d.ownerId().equals(projectId)
                && d.state() != DeliveryState.COMPLETE && d.state() != DeliveryState.CANCELLED)
                .forEach(d -> d.promote(deliveryPriority));
        state.productionRequests().stream().filter(r -> r.output().itemId().equals(item.itemId())
                && (r.reason() == ProductionReason.CONSTRUCTION || r.reason() == ProductionReason.WORKSHOP_INPUT)
                && r.state() != ProductionState.COMPLETE && r.state() != ProductionState.CANCELLED)
                .forEach(r -> {
                    r.promote(productionPriority);
                    state.deliveryRequests().stream().filter(d -> d.ownerId().equals(r.requestId())
                            && d.state() != DeliveryState.COMPLETE && d.state() != DeliveryState.CANCELLED)
                            .forEach(d -> d.promote(deliveryPriority));
                });
        data.setDirty();
    }

    private boolean isRawGatheredMaterial(ItemKey item) {
        String path = item.itemId().getPath();
        return path.endsWith("_log") || path.endsWith("_stem") || path.equals("cobblestone")
                || path.equals("sand") || path.equals("red_sand") || path.equals("gravel")
                || path.equals("dirt") || path.equals("clay_ball");
    }

    public void finishDelivery(DeliveryRequest request, int inserted) {
        request.unloaded(inserted);
        if (request.state() == DeliveryState.COMPLETE) reservations.release(request.settlementId(), request.reservationId());
        data.setDirty();
    }
    public void releaseDelivery(DeliveryRequest request) { deliveries.release(request.settlementId(), request.id()); }
    public void failUnloadedDelivery(DeliveryRequest request) {
        if (request.cargoQuantity() != 0) return;
        reservations.release(request.settlementId(), request.reservationId()); request.cancel(); data.setDirty();
    }
    public void onCitizenDeath(CitizenRecord citizen) {
        for (SettlementEconomyState state : data.all()) {
            state.deliveryRequests().stream().filter(d -> d.courierId().filter(citizen.citizenId()::equals).isPresent())
                    .forEach(DeliveryRequest::courierLost);
            state.productionRequests().stream().filter(p -> p.claimedBy().filter(citizen.citizenId()::equals).isPresent())
                    .forEach(ProductionRequest::releaseClaim);
            state.workshops().forEach(w -> w.release(citizen.citizenId()));
        }
        data.setDirty();
    }
    public void releaseProduction(ProductionRequest request, UUID citizenId) {
        request.releaseClaim();
        data.forSettlement(request.settlementId()).workshops().forEach(w -> w.release(citizenId));
        data.setDirty();
    }
    public void awaitMachine(ProductionRequest request, Workshop workshop, UUID citizenId) {
        request.awaitMachine(); workshop.release(citizenId); data.setDirty();
    }

    public void productionCompleted(ProductionRequest request, Workshop workshop, int amount) {
        UUID worker = request.claimedBy().orElse(null);
        request.complete(amount);
        if (worker != null) workshop.release(worker);
        InventoryEndpoint output = data.forSettlement(request.settlementId()).endpoint(workshop.output()).orElse(null);
        InventoryEndpoint destination = data.forSettlement(request.settlementId()).endpoints().stream()
                .filter(e -> e.type() == InventoryEndpointType.SETTLEMENT_STORAGE && e.ownership().permitsAutomaticUse())
                .findFirst().orElse(null);
        if (output != null && destination != null && !output.id().equals(destination.id())) {
            int movable = Math.min(amount, index.observed(request.settlementId(), output.id(), request.output()));
            if (movable > 0) {
                ResourceReservation reservation = reservations.reserveFrom(request.settlementId(), request.requestId(),
                        output.id(), request.output(), movable, ReservationPurpose.PRODUCTION,
                        server.overworld().getGameTime(), RESERVATION_TTL).orElse(null);
                if (reservation != null) deliveries.create(request.settlementId(), output.id(), destination.id(),
                        request.output(), reservation.quantity(), deliveryPriority(request.priority()),
                        DeliveryPurpose.WORKSHOP_OUTPUT, request.requestId(), reservation.reservationId());
            }
        }
        data.setDirty();
    }

    private void process(SettlementEconomyState state, long now) {
        for (ProductionRequest request : state.productionRequests()) {
            if (request.claimedBy().isPresent() && !hasPersistedProductionTask(request)) request.releaseClaim();
            if (request.state() == ProductionState.BLOCKED && request.blocker() == ProductionBlocker.STORAGE_FULL
                    && outputHasSpace(state, request)) request.setState(ProductionState.WAITING_FOR_DELIVERY);
            if (request.state() == ProductionState.BLOCKED
                    && (request.blocker() == ProductionBlocker.INVALID_WORKSTATION
                    || request.blocker() == ProductionBlocker.MISSING_WORKSTATION)) {
                if (!workstationRestored(state, request)) {
                    deadlocks.observe(request, now);
                    continue;
                }
                request.setState(ProductionState.PLANNED);
            }
            // Upgrade legacy saves that still expect all 17+ logs before a single plank craft.
            // Only idle crafting orders are touched; active work and cargo are preserved.
            planner.replanOversizedCraftBatch(request);
            if (request.state() == ProductionState.PLANNED || request.state() == ProductionState.WAITING_FOR_INPUT
                    || request.state() == ProductionState.BLOCKED && request.blocker() != ProductionBlocker.STORAGE_FULL) {
                planner.plan(request);
            }
            if (request.state() == ProductionState.WAITING_FOR_DELIVERY) prepareDeliveries(state, request, now);
            if (request.state() == ProductionState.IN_PROGRESS && request.method().usesFurnace()) {
                smeltingReady(state, request);
            }
            deadlocks.observe(request, now);
        }
    }

    private boolean hasPersistedProductionTask(ProductionRequest request) {
        CitizenRecord citizen = CitizenSavedData.get(server).registry().find(request.claimedBy().orElseThrow()).orElse(null);
        if (citizen == null) return false;
        var brain = citizen.brainPersistence();
        return matchesProductionTask(brain.activeTaskType(), brain.activeTaskData(), request.requestId())
                || matchesProductionTask(brain.suspendedTaskType(), brain.suspendedTaskData(), request.requestId());
    }

    private boolean matchesProductionTask(String type, net.minecraft.nbt.CompoundTag tag, UUID requestId) {
        return ProductionTask.TYPE.equals(type) && tag.hasUUID("request") && requestId.equals(tag.getUUID("request"));
    }

    private boolean workstationRestored(SettlementEconomyState state, ProductionRequest request) {
        WorkshopType type = request.method().usesFurnace() ? WorkshopType.SMELTING : WorkshopType.GENERAL_CRAFTING;
        Workshop workshop = state.workshops().stream().filter(w -> w.type() == type).findFirst().orElse(null);
        if (workshop == null) return false;
        ServerLevel level = levelFor(workshop.workstation());
        if (level == null || !level.isLoaded(workshop.workstation().pos())) return false;
        return request.method().usesFurnace()
                ? level.getBlockEntity(workshop.workstation().pos()) instanceof AbstractFurnaceBlockEntity
                : level.getBlockState(workshop.workstation().pos()).is(Blocks.CRAFTING_TABLE);
    }

    private boolean outputHasSpace(SettlementEconomyState state, ProductionRequest request) {
        WorkshopType type = request.method().usesFurnace() ? WorkshopType.SMELTING : WorkshopType.GENERAL_CRAFTING;
        Workshop workshop = state.workshops().stream().filter(w -> w.type() == type).findFirst().orElse(null);
        InventoryEndpoint output = workshop == null ? null : state.endpoint(workshop.output()).orElse(null);
        if (output == null) return false;
        ServerLevel level = levelFor(output.location()); Container container = level == null ? null : output.resolve(level).orElse(null);
        if (container == null) return false;
        return PhysicalInventoryOps.capacity(container, new ItemStack(BuiltInRegistries.ITEM.get(request.output().itemId()))) > 0;
    }

    private void prepareDeliveries(SettlementEconomyState state, ProductionRequest request, long now) {
        WorkshopType type = request.method().usesFurnace() ? WorkshopType.SMELTING : WorkshopType.GENERAL_CRAFTING;
        Workshop workshop = state.workshops().stream().filter(w -> w.type() == type).findFirst().orElse(null);
        if (workshop == null) { request.block(ProductionBlocker.MISSING_WORKSTATION); return; }
        InventoryEndpoint input = state.endpoint(workshop.input()).orElse(null);
        if (input == null) { request.block(ProductionBlocker.INVALID_DESTINATION); return; }
        boolean complete = true;
        for (Map.Entry<ItemKey, Integer> need : request.needs().entrySet()) {
            int present = index.observed(state.settlementId(), input.id(), need.getKey());
            int outstanding = Math.max(0, need.getValue() - present);
            // Count only deliveries headed to THIS workshop input for THIS ingredient.
            // Retire unloaded expired, blocked, or now-oversized requests left by old saves.
            // Never cancel physical cargo that is already in a citizen's inventory.
            List<DeliveryRequest> matching = state.deliveryRequests().stream()
                    .filter(d -> WorkshopSupplyPolicy.matchesInput(d, request, input.id(), need.getKey())).toList();
            int incoming = matching.stream().mapToInt(WorkshopSupplyPolicy::incoming).sum();
            for (DeliveryRequest delivery : matching) {
                boolean reservationExists = state.reservation(delivery.reservationId()).isPresent();
                if (WorkshopSupplyPolicy.shouldRetire(delivery, reservationExists, incoming, outstanding)) {
                    incoming -= WorkshopSupplyPolicy.incoming(delivery);
                    reservations.release(state.settlementId(), delivery.reservationId());
                    delivery.cancel();
                    data.setDirty();
                }
            }
            if (outstanding == 0) continue;
            complete = false;
            int deficit = Math.max(0, outstanding - incoming);
            if (deficit == 0) continue;
            List<ResourceReservation> made = reservations.reservePartial(state.settlementId(), request.requestId(),
                    need.getKey(), deficit, ReservationPurpose.WORKSHOP_INPUT, now, RESERVATION_TTL);
            for (ResourceReservation reservation : made) deliveries.create(state.settlementId(), reservation.source(),
                    input.id(), need.getKey(), reservation.quantity(), deliveryPriority(request.priority()),
                    DeliveryPurpose.WORKSHOP_INPUT, request.requestId(), reservation.reservationId());
            if (made.stream().mapToInt(ResourceReservation::quantity).sum() < deficit)
                request.block(ProductionBlocker.MISSING_RAW_RESOURCE);
        }
        if (complete) request.setState(ProductionState.READY);
    }

    private void smeltingReady(SettlementEconomyState state, ProductionRequest request) {
        Workshop workshop = state.workshops().stream().filter(w -> w.type() == WorkshopType.SMELTING).findFirst().orElse(null);
        if (workshop == null) return;
        ServerLevel level = levelFor(workshop.workstation());
        if (level == null || !(level.getBlockEntity(workshop.workstation().pos()) instanceof AbstractFurnaceBlockEntity furnace)) return;
        if (request.output().matches(furnace.getItem(2)) && furnace.getItem(2).getCount() > 0) request.setState(ProductionState.READY);
    }

    private void reconcileStaggered(long now) {
        for (SettlementEconomyState state : data.all()) for (InventoryEndpoint endpoint : state.endpoints()) {
            if (Math.floorMod(now + endpoint.id().value().hashCode(), 100L) != 0L) continue;
            ServerLevel level = levelFor(endpoint.location());
            if (level == null) continue;
            Optional<Container> container = endpoint.resolve(level);
            if (container.isEmpty()) { index.removeEndpoint(state.settlementId(), endpoint.id()); continue; }
            index.reconcileEndpoint(state.settlementId(), endpoint.id(), PhysicalInventoryOps.snapshot(container.get()));
        }
    }

    private void ensureKnownSettlementStorage() {
        for (SettlementState settlement : SettlementSavedData.get(server).registry().all()) {
            for (AssignedLocation storage : settlement.storageLocations()) registerEndpoint(settlement.settlementId(), storage,
                    InventoryEndpointType.SETTLEMENT_STORAGE, InventoryOwnership.SETTLEMENT);
        }
    }

    private void updateForecasts() {
        for (SettlementState settlement : SettlementSavedData.get(server).registry().all()) {
            SettlementEconomyState state = data.forSettlement(settlement.settlementId());
            int axes = observed(state, new ItemStack(Items.WOODEN_AXE)) + observed(state, new ItemStack(Items.STONE_AXE))
                    + observed(state, new ItemStack(Items.IRON_AXE));
            axes += com.tyes.civilisation.persistence.CitizenSavedData.get(server).registry().all().stream()
                    .filter(c -> c.settlementId().filter(settlement.settlementId()::equals).isPresent())
                    .mapToInt(c -> c.inventory().count(ItemKey.bulk(new ItemStack(Items.WOODEN_AXE)))
                            + c.inventory().count(ItemKey.bulk(new ItemStack(Items.STONE_AXE)))
                            + c.inventory().count(ItemKey.bulk(new ItemStack(Items.IRON_AXE)))).sum();
            boolean smelting = state.productionRequests().stream().anyMatch(r -> r.method() == ProductionMethod.SMELT
                    && r.state() != ProductionState.COMPLETE && r.state() != ProductionState.CANCELLED);
            DemandForecast forecast = forecasts.forecast(server, settlement, axes, smelting);
            int food = observed(state, new ItemStack(Items.BREAD)) + observed(state, new ItemStack(Items.CARROT));
            int seeds = observed(state, new ItemStack(Items.WHEAT_SEEDS)) + observed(state, new ItemStack(Items.CARROT));
            int fuel = observed(state, new ItemStack(Items.COAL)) + observed(state, new ItemStack(Items.CHARCOAL));
            state.setProtected(ItemKey.bulk(new ItemStack(Items.BREAD)), forecast.protectedFood());
            state.setProtected(ItemKey.bulk(new ItemStack(Items.WHEAT_SEEDS)), forecast.seedReserve());
            shortages.update(state, forecast, food, seeds, fuel);
            if (forecast.usableAxes() < forecast.lumberjacks() && state.productionRequests().stream().noneMatch(r ->
                    r.output().itemId().equals(ItemKey.bulk(new ItemStack(Items.STONE_AXE)).itemId())
                            && r.state() != ProductionState.COMPLETE && r.state() != ProductionState.CANCELLED)) {
                production.create(settlement.settlementId(), ItemKey.bulk(new ItemStack(Items.STONE_AXE)),
                        forecast.lumberjacks() - forecast.usableAxes(), ProductionPriority.HIGH, ProductionReason.TOOL_SHORTAGE);
            }
        }
    }
    private int observed(SettlementEconomyState state, ItemStack stack) { return index.observed(state.settlementId(), ItemKey.bulk(stack)); }
    private DeliveryPriority deliveryPriority(ProductionPriority priority) { return DeliveryPriority.valueOf(priority.name()); }
    private ServerLevel levelFor(AssignedLocation location) { return server.getLevel(ResourceKey.create(Registries.DIMENSION, location.dimension())); }
}
