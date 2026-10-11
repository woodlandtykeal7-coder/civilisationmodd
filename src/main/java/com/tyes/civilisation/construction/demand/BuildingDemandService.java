package com.tyes.civilisation.construction.demand;

import com.tyes.civilisation.CivilisationMod;
import com.tyes.civilisation.citizen.RuntimePresence;
import com.tyes.civilisation.construction.ConstructionService;
import com.tyes.civilisation.construction.building.BuildingRecord;
import com.tyes.civilisation.construction.project.BuildingCondition;
import com.tyes.civilisation.construction.project.ConstructionBlocker;
import com.tyes.civilisation.construction.project.ConstructionPriority;
import com.tyes.civilisation.construction.project.ConstructionProjectState;
import com.tyes.civilisation.persistence.CitizenSavedData;
import com.tyes.civilisation.persistence.ConstructionSavedData;
import com.tyes.civilisation.persistence.DemographySavedData;
import com.tyes.civilisation.persistence.SettlementSavedData;
import com.tyes.civilisation.society.HouseholdState;
import com.tyes.civilisation.settlement.SettlementState;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

/** Slow settlement planner. It creates intent/projects only; builders remain CitizenBrain-owned. */
public final class BuildingDemandService {
    public static final ResourceLocation STARTER_HOUSE = ResourceLocation.fromNamespaceAndPath(
            CivilisationMod.MOD_ID, "starter_house");
    private static final long EVALUATION_INTERVAL = 600L;
    private static final int STARTER_HOUSE_CAPACITY = 2;

    private final MinecraftServer server;
    private final ConstructionService construction;
    private final ConstructionSavedData data;

    public BuildingDemandService(MinecraftServer server, ConstructionService construction) {
        this.server = server;
        this.construction = construction;
        this.data = construction.data();
    }

    public void tick() {
        long now = server.overworld().getGameTime();
        if (now % EVALUATION_INTERVAL != 0L) return;
        for (SettlementState settlement : SettlementSavedData.get(server).registry().all()) evaluate(settlement, now);
    }

    public HousingStatus housingStatus(SettlementState settlement) {
        int population = (int)settlement.members().stream()
                .map(id -> CitizenSavedData.get(server).registry().find(id).orElse(null))
                .filter(citizen -> citizen != null && citizen.presence() != RuntimePresence.DECEASED)
                .count();
        var households = DemographySavedData.get(server).households().forSettlement(settlement.settlementId()).stream()
                .filter(household -> household.state() != HouseholdState.DISSOLVED).toList();
        Map<UUID, BuildingRecord> homes = new HashMap<>();
        data.buildings().stream()
                .filter(building -> building.settlementId().equals(settlement.settlementId()))
                .filter(building -> building.type().equals(STARTER_HOUSE))
                .filter(this::isUsableResidential).forEach(building -> homes.put(building.buildingId(), building));
        int completed = homes.values().stream().mapToInt(BuildingRecord::capacity).sum();
        int occupied = 0;
        int needingHomes = 0;
        int householdCapacity = 0;
        for (var household : households) {
            int members = (int) household.members().stream()
                    .map(id -> CitizenSavedData.get(server).registry().find(id).orElse(null))
                    .filter(citizen -> citizen != null && citizen.presence() != RuntimePresence.DECEASED).count();
            if (members == 0) continue;
            householdCapacity += members;
            BuildingRecord home = household.homeBuildingId().map(homes::get).orElse(null);
            if (home != null && home.capacity() >= members) occupied += members;
            else needingHomes++;
        }
        int requiredCapacity = Math.max(population, householdCapacity);
        int planned = data.projects().stream()
                .filter(project -> project.settlementId().equals(settlement.settlementId()))
                .filter(project -> project.blueprint().buildingType().equals(STARTER_HOUSE))
                .filter(project -> project.state() != ConstructionProjectState.COMPLETE
                        && project.state() != ConstructionProjectState.CANCELLED)
                .filter(project -> project.blocker() != ConstructionBlocker.BLUEPRINT_INVALID
                        && project.blocker() != ConstructionBlocker.INVALID_SITE
                        && project.blocker() != ConstructionBlocker.STOCKPILE_DESTROYED)
                .mapToInt(project -> STARTER_HOUSE_CAPACITY).sum();
        int future = completed + planned;
        return new HousingStatus(population, households.size(), needingHomes, requiredCapacity,
                completed, future, occupied, Math.max(0, completed - occupied),
                Math.max(0, requiredCapacity - future));
    }

    public boolean isUsableResidential(BuildingRecord building) {
        if (!building.type().equals(STARTER_HOUSE) || building.condition() != BuildingCondition.INTACT) return false;
        var project = data.project(building.projectId()).orElse(null);
        if (project == null || project.state() != ConstructionProjectState.COMPLETE
                || project.registeredBuildingId().filter(building.buildingId()::equals).isEmpty()) return false;
        ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, building.anchor().dimension()));
        if (level == null) return false;
        if (!level.isLoaded(building.anchor().pos())) return true;
        return project.blueprint().blocks().stream().allMatch(planned -> level.getBlockState(
                planned.relativePos().resolve(project.anchor().pos())).is(BuiltInRegistries.BLOCK.get(planned.blockId())));
    }

    public void evaluateNow(SettlementState settlement) {
        evaluate(settlement, server.overworld().getGameTime());
    }

    private void evaluate(SettlementState settlement, long now) {
        HousingStatus housing = housingStatus(settlement);
        BuildingDemand demand = data.demand(settlement.settlementId()).orElse(null);
        if (housing.deficit() <= 0) {
            // Hysteresis: never cancel meaningful construction. Clear only intent with no active valid project.
            if (demand != null && demand.activeProjectId().isEmpty()) data.clearDemand(settlement.settlementId());
            return;
        }

        DemandSeverity severity = housing.householdsNeedingHomes() >= 4 || housing.deficit() >= 6
                ? DemandSeverity.CRITICAL
                : housing.householdsNeedingHomes() > 0 || housing.deficit() >= 2
                ? DemandSeverity.HIGH : DemandSeverity.NORMAL;
        if (demand == null) {
            demand = new BuildingDemand(settlement.settlementId(), STARTER_HOUSE,
                    BuildingDemandReason.HOUSING_SHORTAGE, severity, housing.requiredHouseholdCapacity(),
                    housing.completedCapacity(), now);
            data.putDemand(demand);
        } else {
            demand.update(severity, housing.requiredHouseholdCapacity(), housing.completedCapacity());
        }

        var activeProject = demand.activeProjectId().flatMap(data::project)
                .filter(project -> project.state() != ConstructionProjectState.COMPLETE
                        && project.state() != ConstructionProjectState.CANCELLED).orElse(null);
        if (activeProject != null) {
            activeProject.updatePriority(priority(severity), priorityReason(housing), now);
            data.setDirty();
            return;
        }

        // Queue one house at a time, then re-evaluate after it completes.
        try {
            var project = construction.createStarterHouse(settlement, priority(severity), priorityReason(housing), now);
            demand.attachProject(project.projectId());
            CivilisationMod.LOGGER.info("{} autonomously created {} for {} (population={}, future capacity={})",
                    settlement.readableId(), project.readableId(), demand.reason(), housing.population(),
                    housing.futureCapacity() + STARTER_HOUSE_CAPACITY);
        } catch (IllegalStateException unavailableSite) {
            CivilisationMod.LOGGER.debug("{} housing demand is waiting for a valid site: {}",
                    settlement.readableId(), unavailableSite.getMessage());
        }
        data.setDirty();
    }

    private ConstructionPriority priority(DemandSeverity severity) {
        return ConstructionPriority.valueOf(severity.name());
    }

    private String priorityReason(HousingStatus housing) {
        return housing.householdsNeedingHomes() + " household(s) need homes; remaining capacity deficit="
                + housing.deficit();
    }
}
