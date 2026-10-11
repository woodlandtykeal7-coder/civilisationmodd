package com.tyes.civilisation.server;

import com.tyes.civilisation.citizen.AssignedLocation;
import com.tyes.civilisation.citizen.CitizenRecord;
import com.tyes.civilisation.citizen.RuntimePresence;
import com.tyes.civilisation.entity.CivilianEntity;
import com.tyes.civilisation.persistence.CitizenSavedData;
import com.tyes.civilisation.persistence.SettlementSavedData;
import com.tyes.civilisation.settlement.SettlementState;
import java.util.HashSet;
import java.util.Set;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.Container;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import com.tyes.civilisation.citizen.identity.CitizenAppearanceService;
import com.tyes.civilisation.economy.SettlementEconomyState;
import com.tyes.civilisation.economy.logistics.DeliveryState;
import com.tyes.civilisation.economy.production.ProductionState;
import com.tyes.civilisation.persistence.DemographySavedData;
import com.tyes.civilisation.society.ApprenticeshipState;
import com.tyes.civilisation.society.LifeStage;
import com.tyes.civilisation.persistence.KnowledgeSavedData;

public final class CivilisationValidation {
    public record Result(int citizenCount, int activeBodyCount, int duplicateBodyCount,
                         int brokenReferenceCount, int invalidAssignmentCount, int settlementCount,
                         int membershipErrorCount, int storageErrorCount, int activeWorkClaims,
                         int appearanceErrorCount, int economyErrorCount, int demographyErrorCount,
                         int knowledgeErrorCount,
                         List<String> problems) {
        public boolean valid() { return problems.isEmpty(); }
    }

    private CivilisationValidation() {
    }

    public static Result validate(MinecraftServer server) {
        CitizenSavedData data = CitizenSavedData.get(server);
        ActiveCitizenRegistry active = CivilisationServices.activeCitizens(server);
        List<String> problems = new ArrayList<>();
        Map<UUID, UUID> bodiesSeen = new HashMap<>();
        int duplicateBodies = 0;
        int brokenReferences = 0;
        int invalidAssignments = 0;
        int membershipErrors = 0;
        int storageErrors = 0;
        int appearanceErrors = 0;
        int economyErrors = 0;
        int demographyErrors = 0;
        int knowledgeErrors = 0;

        for (ServerLevel level : server.getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                if (!(entity instanceof CivilianEntity civilian)) continue;
                if (civilian.citizenId().isEmpty()) {
                    problems.add("Civilian body " + civilian.getUUID() + " has no citizen ID");
                    brokenReferences++;
                    continue;
                }
                UUID citizenId = civilian.citizenId().orElseThrow();
                UUID previous = bodiesSeen.putIfAbsent(citizenId, civilian.getUUID());
                if (previous != null && !previous.equals(civilian.getUUID())) {
                    problems.add("Citizen " + citizenId + " has bodies " + previous + " and " + civilian.getUUID());
                    duplicateBodies++;
                }
                if (data.registry().find(citizenId).isEmpty()) {
                    problems.add("Body " + civilian.getUUID() + " references missing citizen " + citizenId);
                    brokenReferences++;
                }
            }
        }

        for (Map.Entry<UUID, UUID> entry : active.snapshot().entrySet()) {
            CitizenRecord record = data.registry().find(entry.getKey()).orElse(null);
            if (record == null) {
                problems.add("Active registry references missing citizen " + entry.getKey());
                brokenReferences++;
            } else if (record.presence() == RuntimePresence.DECEASED) {
                problems.add("Deceased citizen " + entry.getKey() + " still has active body " + entry.getValue());
                brokenReferences++;
            }
            if (!entry.getValue().equals(bodiesSeen.get(entry.getKey()))) {
                problems.add("Active mapping for citizen " + entry.getKey() + " does not match a loaded body");
                brokenReferences++;
            }
        }

        for (CitizenRecord record : data.registry().all()) {
            if (!CitizenAppearanceService.isKnown(record.appearance())) {
                problems.add("Citizen " + record.readableId() + " has unknown appearance "
                        + record.appearance().profileId());
                appearanceErrors++;
            }
            if (record.knowledge().concepts().size() > 256 || record.knowledge().recipes().size() > 256
                    || record.history().events().size() > com.tyes.civilisation.history.CitizenHistory.MAX_EVENTS) {
                problems.add("Citizen " + record.readableId() + " exceeds bounded knowledge/history limits");
                knowledgeErrors++;
            }
            invalidAssignments += validateAssignment(server, record, "home", record.assignments().home().orElse(null),
                    level -> true, problems);
            invalidAssignments += validateAssignment(server, record, "bed", record.assignments().bed().orElse(null),
                    level -> level.getBlockState(record.assignments().bed().orElseThrow().pos()).isBed(
                            level, record.assignments().bed().orElseThrow().pos(), null), problems);
            invalidAssignments += validateAssignment(server, record, "food storage",
                    record.assignments().foodStorage().orElse(null),
                    level -> level.getBlockEntity(record.assignments().foodStorage().orElseThrow().pos()) instanceof Container,
                    problems);
            invalidAssignments += validateAssignment(server, record, "workplace",
                    record.assignments().workplace().orElse(null),
                    level -> level.getBlockState(record.assignments().workplace().orElseThrow().pos()).is(Blocks.CRAFTING_TABLE),
                    problems);
        }
        SettlementSavedData settlementData = SettlementSavedData.get(server);
        Set<String> readableSettlementIds = new HashSet<>();
        for (SettlementState settlement : settlementData.registry().all()) {
            if (!readableSettlementIds.add(settlement.readableId().toLowerCase())) {
                problems.add("Duplicate settlement ID " + settlement.readableId());
                membershipErrors++;
            }
            for (UUID citizenId : settlement.members()) {
                CitizenRecord citizen = data.registry().find(citizenId).orElse(null);
                if (citizen == null || citizen.settlementId().filter(settlement.settlementId()::equals).isEmpty()) {
                    problems.add("Settlement " + settlement.readableId() + " has inconsistent member " + citizenId);
                    membershipErrors++;
                }
            }
            for (AssignedLocation storage : settlement.storageLocations()) {
                ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION, storage.dimension());
                ServerLevel level = server.getLevel(dimension);
                if (level == null || level.isLoaded(storage.pos()) && !(level.getBlockEntity(storage.pos()) instanceof Container)) {
                    problems.add("Settlement " + settlement.readableId() + " has invalid storage " + storage);
                    storageErrors++;
                }
            }
        }
        for (CitizenRecord citizen : data.registry().all()) {
            UUID settlementId = citizen.settlementId().orElse(null);
            if (settlementId == null) continue;
            SettlementState settlement = settlementData.registry().find(settlementId).orElse(null);
            if (settlement == null || !settlement.members().contains(citizen.citizenId())) {
                problems.add("Citizen " + citizen.readableId() + " has missing/inconsistent settlement reference " + settlementId);
                membershipErrors++;
            }
        }
        var economy = CivilisationServices.economy(server).data();
        for (SettlementEconomyState state : economy.all()) {
            SettlementState settlement = settlementData.registry().find(state.settlementId()).orElse(null);
            if (settlement == null) { problems.add("Economy state references missing settlement " + state.settlementId()); economyErrors++; }
            Set<UUID> endpointIds = new HashSet<>();
            for (var endpoint : state.endpoints()) {
                if (!endpointIds.add(endpoint.id().value())) { problems.add("Duplicate economy endpoint " + endpoint.id()); economyErrors++; }
                if (!endpoint.settlementId().equals(state.settlementId())) { problems.add("Endpoint " + endpoint.id() + " has wrong settlement"); economyErrors++; }
            }
            Set<UUID> reservationIds = new HashSet<>();
            for (var reservation : state.reservations()) {
                if (!reservationIds.add(reservation.reservationId()) || reservation.quantity() <= 0) { problems.add("Invalid reservation " + reservation.reservationId()); economyErrors++; }
                if (state.endpoint(reservation.source()).isEmpty()) { problems.add("Reservation " + reservation.reservationId() + " has missing source"); economyErrors++; }
                boolean ownerExists = state.production(reservation.ownerId()).isPresent() || state.delivery(reservation.ownerId()).isPresent()
                        || data.registry().find(reservation.ownerId()).isPresent()
                        || CivilisationServices.construction(server).data().project(reservation.ownerId()).isPresent();
                if (!ownerExists) { problems.add("Reservation " + reservation.reservationId() + " has missing owner " + reservation.ownerId()); economyErrors++; }
            }
            for (var delivery : state.deliveryRequests()) {
                if (delivery.quantity() <= 0 || delivery.cargoQuantity() < 0 || delivery.cargoQuantity() > delivery.quantity()) { problems.add("Delivery " + delivery.id() + " has invalid quantity/cargo"); economyErrors++; }
                if (state.endpoint(delivery.source()).isEmpty() || state.endpoint(delivery.destination()).isEmpty()) { problems.add("Delivery " + delivery.id() + " has missing endpoint"); economyErrors++; }
                if (delivery.state() == DeliveryState.COMPLETE && delivery.cargoQuantity() != 0) { problems.add("Completed delivery " + delivery.id() + " retains cargo"); economyErrors++; }
                if (delivery.courierId().isPresent() && data.registry().find(delivery.courierId().orElseThrow()).isEmpty()) { problems.add("Delivery " + delivery.id() + " has missing courier"); economyErrors++; }
            }
            for (var request : state.productionRequests()) {
                if (request.desiredQuantity() <= 0 || request.completedQuantity() < 0 || request.completedQuantity() > request.desiredQuantity()) { problems.add("Production request " + request.requestId() + " has invalid quantity"); economyErrors++; }
                if (request.state() == ProductionState.COMPLETE && request.remainingQuantity() != 0) { problems.add("Completed production request " + request.requestId() + " is incomplete"); economyErrors++; }
                if (request.claimedBy().isPresent() && data.registry().find(request.claimedBy().orElseThrow()).isEmpty()) { problems.add("Production request " + request.requestId() + " has missing worker"); economyErrors++; }
            }
            for (var workshop : state.workshops()) {
                if (state.endpoint(workshop.input()).isEmpty() || state.endpoint(workshop.output()).isEmpty()) { problems.add("Workshop " + workshop.id() + " has missing buffer endpoint"); economyErrors++; }
                if (!workshop.settlementId().equals(state.settlementId())) { problems.add("Workshop " + workshop.id() + " has wrong settlement"); economyErrors++; }
            }
        }
        var constructionData=CivilisationServices.construction(server).data();
        Set<UUID> projectIds=new HashSet<>(),buildingProjects=new HashSet<>();
        for(var building:constructionData.buildings())if(!buildingProjects.add(building.projectId())){problems.add("Construction project "+building.projectId()+" has duplicate registered buildings");economyErrors++;}
        for(var project:constructionData.projects()){
            if(!projectIds.add(project.projectId())){problems.add("Duplicate construction project "+project.projectId());economyErrors++;}
            if(settlementData.registry().find(project.settlementId()).isEmpty()){problems.add("Construction project "+project.readableId()+" has missing settlement");economyErrors++;}
            if(project.completedPlacements()<0||project.completedPlacements()>project.totalPlacements()||project.totalPlacements()==0){problems.add("Construction project "+project.readableId()+" has invalid progress/blueprint");economyErrors++;}
            if(project.priority()==null||project.priorityReason()==null||project.priorityReason().isBlank()||project.priorityChangedTick()<0){problems.add("Construction project "+project.readableId()+" has invalid priority data");economyErrors++;}
            var estate=CivilisationServices.economy(server).data().forSettlement(project.settlementId());
            if(estate.endpoint(project.stockpileEndpoint()).isEmpty()){problems.add("Construction project "+project.readableId()+" has missing stockpile endpoint");economyErrors++;}
            if(project.state()==com.tyes.civilisation.construction.project.ConstructionProjectState.COMPLETE){
                if(project.completedPlacements()!=project.totalPlacements()){problems.add("Completed construction project "+project.readableId()+" is incomplete");economyErrors++;}
                if(project.registeredBuildingId().isEmpty()||!buildingProjects.contains(project.projectId())){problems.add("Completed construction project "+project.readableId()+" has no registered building");economyErrors++;}
                if(project.heldTotal()!=0){problems.add("Completed construction project "+project.readableId()+" retains builder cargo");economyErrors++;}
                if(!project.temporaryAccess().isEmpty()){problems.add("Completed construction project "+project.readableId()+" retains temporary access");economyErrors++;}
            }else if(project.registeredBuildingId().isPresent()){problems.add("Incomplete construction project "+project.readableId()+" already has a registered building");economyErrors++;}
        }
        for (var demand : constructionData.demands()) {
            if (settlementData.registry().find(demand.settlementId()).isEmpty()) {
                problems.add("Building demand references missing settlement " + demand.settlementId());
                economyErrors++;
            }
            if (demand.activeProjectId().isPresent()) {
                var linked = constructionData.project(demand.activeProjectId().orElseThrow()).orElse(null);
                if (linked == null) {
                    problems.add("Building demand references missing project " + demand.activeProjectId().orElseThrow());
                    economyErrors++;
                } else if (!linked.settlementId().equals(demand.settlementId())
                        || !linked.blueprint().buildingType().equals(demand.buildingType())) {
                    problems.add("Building demand has invalid project linkage " + demand.activeProjectId().orElseThrow());
                    economyErrors++;
                }
            }
        }

        DemographySavedData demography = DemographySavedData.get(server);
        Set<UUID> householdMembers = new HashSet<>();
        Map<UUID, Integer> homeOccupancy = new HashMap<>();
        Map<UUID, com.tyes.civilisation.construction.building.BuildingRecord> buildings = new HashMap<>();
        constructionData.buildings().forEach(building -> buildings.put(building.buildingId(), building));
        for (var household : demography.households().all()) {
            if (settlementData.registry().find(household.settlementId()).isEmpty()) {
                problems.add("Household " + household.readableId() + " references missing settlement");
                demographyErrors++;
            }
            for (UUID memberId : household.members()) {
                CitizenRecord member = data.registry().find(memberId).orElse(null);
                if (member == null || member.development().householdId().filter(household.householdId()::equals).isEmpty()) {
                    problems.add("Household " + household.readableId() + " has inconsistent member " + memberId);
                    demographyErrors++;
                }
                if (!householdMembers.add(memberId)) {
                    problems.add("Citizen " + memberId + " belongs to more than one household");
                    demographyErrors++;
                }
            }
            for (UUID guardianId : household.guardians()) {
                CitizenRecord guardian = data.registry().find(guardianId).orElse(null);
                if (!household.members().contains(guardianId) || guardian == null
                        || !guardian.development().lifeStage().mayPerformFullWork()) {
                    problems.add("Household " + household.readableId() + " has invalid guardian " + guardianId);
                    demographyErrors++;
                }
            }
            for (UUID dependantId : household.dependants()) {
                CitizenRecord dependant = data.registry().find(dependantId).orElse(null);
                if (!household.members().contains(dependantId) || dependant == null
                        || !dependant.development().lifeStage().requiresGuardian()) {
                    problems.add("Household " + household.readableId() + " has invalid dependant " + dependantId);
                    demographyErrors++;
                }
            }
            if (!household.dependants().isEmpty() && household.guardians().isEmpty()
                    && household.state() != com.tyes.civilisation.society.HouseholdState.NEEDS_GUARDIAN) {
                problems.add("Household " + household.readableId() + " has dependants without guardian state");
                demographyErrors++;
            }
            UUID homeId = household.homeBuildingId().orElse(null);
            if (homeId != null) {
                var home = buildings.get(homeId);
                if (home == null || !home.settlementId().equals(household.settlementId())
                        || !CivilisationServices.buildingDemand(server).isUsableResidential(home)) {
                    problems.add("Household " + household.readableId() + " references invalid home " + homeId);
                    demographyErrors++;
                } else {
                    homeOccupancy.merge(homeId, household.members().size(), Integer::sum);
                }
            }
        }
        for (CitizenRecord citizen : data.registry().all()) {
            if (citizen.presence() == RuntimePresence.DECEASED || citizen.settlementId().isEmpty()) continue;
            UUID householdId = citizen.development().householdId().orElse(null);
            if (householdId == null || demography.households().find(householdId).isEmpty()) {
                problems.add("Living settlement citizen " + citizen.readableId() + " has no valid household");
                demographyErrors++;
            }
        }
        for (var entry : homeOccupancy.entrySet()) {
            var building = buildings.get(entry.getKey());
            if (building != null && entry.getValue() > building.capacity()) {
                problems.add("Home " + entry.getKey() + " exceeds capacity: " + entry.getValue() + "/" + building.capacity());
                demographyErrors++;
            }
        }
        for (var apprenticeship : demography.apprenticeships()) {
            CitizenRecord apprentice = data.registry().find(apprenticeship.apprenticeId()).orElse(null);
            if (apprentice == null) {
                problems.add("Apprenticeship " + apprenticeship.apprenticeshipId() + " has missing apprentice");
                demographyErrors++;
            }
            if (apprenticeship.state() == ApprenticeshipState.ACTIVE) {
                CitizenRecord mentor = apprenticeship.mentorId().flatMap(data.registry()::find).orElse(null);
                if (apprentice == null || mentor == null || mentor.presence() == RuntimePresence.DECEASED
                        || mentor.settlementId().filter(apprenticeship.settlementId()::equals).isEmpty()
                        || apprentice.settlementId().filter(apprenticeship.settlementId()::equals).isEmpty()) {
                    problems.add("Active apprenticeship " + apprenticeship.apprenticeshipId() + " has invalid mentor");
                    demographyErrors++;
                }
            }
        }
        Set<UUID> researchIds = new HashSet<>();
        for (var research : KnowledgeSavedData.get(server).research()) {
            if (!researchIds.add(research.id())) {
                problems.add("Duplicate research project " + research.id()); knowledgeErrors++;
            }
            if (settlementData.registry().find(research.settlementId()).isEmpty()) {
                problems.add("Research " + research.readableId() + " references missing settlement"); knowledgeErrors++;
            }
            if (research.questionId() == null || research.targetConcept() == null || research.state() == null) {
                problems.add("Research " + research.readableId() + " has invalid knowledge data"); knowledgeErrors++;
            }
        }
        return new Result(data.registry().size(), active.size(), duplicateBodies,
                brokenReferences, invalidAssignments, settlementData.registry().size(), membershipErrors,
                storageErrors, CivilisationServices.workBoard(server).activeClaims(), appearanceErrors,
                economyErrors, demographyErrors, knowledgeErrors, List.copyOf(problems));
    }

    private static int validateAssignment(MinecraftServer server, CitizenRecord record, String name,
                                          AssignedLocation location, Predicate<ServerLevel> blockCheck,
                                          List<String> problems) {
        if (location == null) return 0;
        ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION, location.dimension());
        ServerLevel level = server.getLevel(dimension);
        if (level == null) {
            problems.add("Citizen " + record.citizenId() + " has " + name
                    + " in missing dimension " + location.dimension());
            return 1;
        }
        // Never load a chunk merely to validate it; it will be checked next time it is loaded.
        if (!level.isLoaded(location.pos())) return 0;
        if (blockCheck.test(level)) return 0;
        problems.add("Citizen " + record.citizenId() + " has invalid " + name + " at "
                + location.dimension() + " " + location.pos().toShortString());
        return 1;
    }
}
