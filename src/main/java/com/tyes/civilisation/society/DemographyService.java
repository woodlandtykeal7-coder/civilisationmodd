package com.tyes.civilisation.society;

import com.tyes.civilisation.brain.CitizenTask;
import com.tyes.civilisation.brain.task.BuilderTask;
import com.tyes.civilisation.brain.task.DeliveryTask;
import com.tyes.civilisation.brain.task.ProductionTask;
import com.tyes.civilisation.brain.task.SettlementWorkTask;
import com.tyes.civilisation.citizen.AssignedLocation;
import com.tyes.civilisation.citizen.CitizenRecord;
import com.tyes.civilisation.citizen.RuntimePresence;
import com.tyes.civilisation.construction.building.BuildingRecord;
import com.tyes.civilisation.construction.demand.BuildingDemandService;
import com.tyes.civilisation.persistence.CitizenSavedData;
import com.tyes.civilisation.persistence.ConstructionSavedData;
import com.tyes.civilisation.persistence.DemographySavedData;
import com.tyes.civilisation.persistence.SettlementSavedData;
import com.tyes.civilisation.server.CivilisationServices;
import com.tyes.civilisation.server.CitizenBindingService;
import com.tyes.civilisation.server.SettlementMembershipService;
import com.tyes.civilisation.settlement.SettlementState;
import com.tyes.civilisation.settlement.WorkRole;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

/** Slow/event-driven household, age, education and succession coordinator. */
public final class DemographyService {
    private static final long EVALUATION_INTERVAL = 1200L;
    private static final long GROWTH_COOLDOWN = 7L * 24000L;
    private static final long SPLIT_COOLDOWN = 3L * 24000L;
    private static final List<WorkRole> SUCCESSION_ROLES = List.of(WorkRole.FARMER, WorkRole.LUMBERJACK,
            WorkRole.CRAFTER, WorkRole.BUILDER, WorkRole.HAULER);

    private final MinecraftServer server;
    private final DemographySavedData data;

    public DemographyService(MinecraftServer server) {
        this.server = server;
        this.data = DemographySavedData.get(server);
    }

    public DemographySavedData data() { return data; }

    public void tick() {
        long now = server.overworld().getGameTime();
        if (now % EVALUATION_INTERVAL != 0L) return;
        reconcileAll(now);
    }

    public void reconcileNow() {
        reconcileAll(server.overworld().getGameTime());
    }

    /** Event-driven home allocation after residential completion or invalidation. */
    public void reconcileHomes(UUID settlementId) {
        SettlementSavedData.get(server).registry().find(settlementId)
                .ifPresent(settlement -> assignHomes(settlement, server.overworld().getGameTime()));
        data.setDirty();
        CitizenSavedData.get(server).setDirty();
    }

    private void reconcileAll(long now) {
        for (SettlementState settlement : SettlementSavedData.get(server).registry().all()) {
            bootstrapHouseholds(settlement, now);
            advanceLifeStages(settlement, now);
            reconcileGuardians(settlement, now);
            assignHomes(settlement, now);
            reconcileApprenticeships(settlement, now);
            considerGrowth(settlement, now);
        }
        data.setDirty();
        CitizenSavedData.get(server).setDirty();
    }

    public Optional<LearningOpportunity> learningOpportunity(CitizenRecord learner) {
        UUID settlementId = learner.settlementId().orElse(null);
        if (settlementId == null || learner.development().lifeStage().mayPerformFullWork()) return Optional.empty();
        ApprenticeshipRecord apprenticeship = data.forApprentice(learner.citizenId()).orElse(null);
        CitizenRecord mentor = apprenticeship == null ? chooseMentor(settlementId, null, learner.citizenId())
                : apprenticeship.mentorId().flatMap(CitizenSavedData.get(server).registry()::find).orElse(null);
        if (mentor == null || !isPhysicallyAvailable(mentor)) return Optional.empty();
        AssignedLocation location = mentor.assignments().workplace().orElseGet(() ->
                SettlementSavedData.get(server).registry().find(settlementId).orElseThrow().centre());
        WorkRole focus = apprenticeship == null ? mentor.workRole() : apprenticeship.focus();
        return Optional.of(new LearningOpportunity(location, mentor.citizenId(), focus,
                apprenticeship == null ? null : apprenticeship.apprenticeshipId()));
    }

    public void completeLearning(CitizenRecord learner, WorkRole focus, int points,
                                 Optional<UUID> apprenticeshipId) {
        EducationStatus status = educationStatus(learner.settlementId().orElseThrow());
        int capacityAdjusted = status.learners() <= status.capacity() || status.learners() == 0
                ? points : Math.max(1, points * status.capacity() / status.learners());
        learner.development().addEducation(Math.max(1, capacityAdjusted / 2));
        learner.development().addExperience(focus, capacityAdjusted);
        apprenticeshipId.flatMap(data::apprenticeship).ifPresent(a -> a.addExperience(capacityAdjusted));
        apprenticeshipId.flatMap(data::apprenticeship).flatMap(ApprenticeshipRecord::mentorId)
                .flatMap(CitizenSavedData.get(server).registry()::find)
                .ifPresent(mentor -> CivilisationServices.knowledge(server)
                        .completeTeaching(learner, mentor, focus, capacityAdjusted));
        CitizenSavedData.get(server).setDirty();
        data.setDirty();
    }

    public EducationStatus educationStatus(UUID settlementId) {
        int learners = 0;
        int teachers = 0;
        for (CitizenRecord citizen : CitizenSavedData.get(server).registry().all()) {
            if (citizen.settlementId().filter(settlementId::equals).isEmpty()
                    || citizen.presence() == RuntimePresence.DECEASED) continue;
            if (citizen.development().lifeStage().requiresGuardian()) learners++;
            else if (isPhysicallyAvailable(citizen)) teachers++;
        }
        return new EducationStatus(learners, teachers, teachers * 4);
    }

    public void recordTaskCompletion(CitizenRecord citizen, CitizenTask task) {
        if (!citizen.development().lifeStage().mayPerformFullWork()) return;
        WorkRole role = task instanceof BuilderTask ? WorkRole.BUILDER
                : task instanceof DeliveryTask ? WorkRole.HAULER
                : task instanceof ProductionTask
                    ? (citizen.workRole() == WorkRole.SMELTER ? WorkRole.SMELTER : WorkRole.CRAFTER)
                : task instanceof SettlementWorkTask ? citizen.workRole() : null;
        if (role == null) return;
        awardExperience(citizen, role, 2);
        CitizenSavedData.get(server).setDirty();
    }

    /** Awarded only after the authoritative world placement and ItemStack consumption succeed. */
    public void recordConstructionPlacement(CitizenRecord builder) {
        awardExperience(builder, WorkRole.BUILDER, 2);
        CareerLevel mentorLevel = CareerLevel.fromExperience(builder.development().experience(WorkRole.BUILDER));
        int apprenticePoints = switch (mentorLevel) {
            case NOVICE, TRAINED -> 1;
            case EXPERIENCED -> 2;
            case MASTER -> 3;
        };
        var mentorBody = activeBody(builder.citizenId());
        if (mentorBody != null) for (ApprenticeshipRecord apprenticeship : data.apprenticeships()) {
            if (apprenticeship.state() != ApprenticeshipState.ACTIVE
                    || apprenticeship.focus() != WorkRole.BUILDER
                    || apprenticeship.mentorId().filter(builder.citizenId()::equals).isEmpty()) continue;
            CitizenRecord apprentice = CitizenSavedData.get(server).registry()
                    .find(apprenticeship.apprenticeId()).orElse(null);
            if (apprentice == null || apprentice.presence() == RuntimePresence.DECEASED) continue;
            var apprenticeBody = activeBody(apprentice.citizenId());
            if (apprenticeBody == null || apprenticeBody.level() != mentorBody.level()
                    || apprenticeBody.distanceToSqr(mentorBody) > 144.0D) continue;
            apprentice.development().addEducation(1);
            apprentice.development().addExperience(WorkRole.BUILDER, apprenticePoints);
            apprenticeship.addExperience(apprenticePoints);
        }
        CitizenSavedData.get(server).setDirty();
        data.setDirty();
    }

    private void awardExperience(CitizenRecord citizen, WorkRole role, int points) {
        citizen.development().addExperience(role, points);
        if (citizen.development().career() == WorkRole.GENERAL_LABOURER
                && citizen.development().experience(role) >= 60) {
            citizen.development().setCareer(role);
            citizen.history().add(server.overworld().getGameTime(), "CAREER",
                    "Became a " + role.displayName());
        }
    }

    public void onDeath(CitizenRecord deceased) {
        long now = server.overworld().getGameTime();
        deceased.history().add(now, "DEATH", "Died");
        deceased.development().householdId().flatMap(data.households()::find).ifPresent(household -> {
            household.removeMember(deceased.citizenId(), now);
            if (!household.dependants().isEmpty() && household.guardians().isEmpty()) {
                household.setState(HouseholdState.NEEDS_GUARDIAN);
            }
            deceased.development().setHousehold(null);
        });
        for (ApprenticeshipRecord apprenticeship : data.apprenticeships()) {
            if (apprenticeship.mentorId().filter(deceased.citizenId()::equals).isPresent()) apprenticeship.pause();
            if (apprenticeship.apprenticeId().equals(deceased.citizenId())) apprenticeship.complete();
        }
        data.setDirty();
        CitizenSavedData.get(server).setDirty();
    }

    public void detachFromHousehold(CitizenRecord citizen) {
        long now = server.overworld().getGameTime();
        citizen.development().householdId().flatMap(data.households()::find)
                .ifPresent(household -> household.removeMember(citizen.citizenId(), now));
        citizen.development().setHousehold(null);
        data.forApprentice(citizen.citizenId()).ifPresent(ApprenticeshipRecord::complete);
        for (ApprenticeshipRecord apprenticeship : data.apprenticeships()) {
            if (apprenticeship.mentorId().filter(citizen.citizenId()::equals).isPresent()) apprenticeship.pause();
        }
        data.setDirty();
        CitizenSavedData.get(server).setDirty();
    }

    public HouseholdRecord createHousehold(SettlementState settlement, List<CitizenRecord> members, long now) {
        HouseholdRecord household = data.households().create(settlement.settlementId(), now);
        for (CitizenRecord member : members) {
            boolean dependant = member.development().lifeStage().requiresGuardian();
            household.addMember(member.citizenId(), !dependant, dependant, now);
            member.development().setHousehold(household.householdId());
            member.history().add(now, "HOUSEHOLD", "Formed household " + household.readableId());
        }
        data.setDirty();
        CitizenSavedData.get(server).setDirty();
        return household;
    }

    public CitizenRecord createChild(SettlementState settlement, HouseholdRecord household, long now) {
        CitizenSavedData citizens = CitizenSavedData.get(server);
        CitizenRecord child = citizens.registry().create();
        child.development().setStage(LifeStage.CHILD, now);
        child.history().add(now, "BIRTH", "Born");
        SettlementMembershipService.join(server, child, settlement);
        household.addMember(child.citizenId(), false, true, now);
        child.development().setHousehold(household.householdId());
        settlement.primaryStorage().ifPresent(child.assignments()::setFoodStorage);
        household.homeBuildingId().flatMap(id -> building(id)).ifPresent(home ->
                child.assignments().setHome(home.anchor()));
        ServerLevel level = server.getLevel(net.minecraft.resources.ResourceKey.create(
                net.minecraft.core.registries.Registries.DIMENSION, settlement.centre().dimension()));
        if (level != null && level.isLoaded(settlement.centre().pos())) {
            var spawn = household.homeBuildingId().flatMap(this::building).map(BuildingRecord::anchor)
                    .orElse(settlement.centre()).pos();
            CitizenBindingService.materialize(level, child, spawn.getX() + 0.5D, spawn.getY(), spawn.getZ() + 0.5D);
        }
        household.markGrowth(now);
        citizens.setDirty();
        data.setDirty();
        return child;
    }

    public void forceStage(CitizenRecord citizen, LifeStage stage, long now) {
        citizen.development().setStage(stage, now);
        citizen.development().householdId().flatMap(data.households()::find).ifPresent(household ->
                household.addMember(citizen.citizenId(), stage.mayPerformFullWork(), stage.requiresGuardian(), now));
        if (stage == LifeStage.ADULT) selectCareer(citizen);
        data.setDirty();
        CitizenSavedData.get(server).setDirty();
    }

    private void bootstrapHouseholds(SettlementState settlement, long now) {
        List<CitizenRecord> unassigned = livingMembers(settlement).stream()
                .filter(c -> c.development().householdId().isEmpty()).toList();
        for (int i = 0; i < unassigned.size(); i += 2) {
            createHousehold(settlement, unassigned.subList(i, Math.min(i + 2, unassigned.size())), now);
        }
    }

    private void advanceLifeStages(SettlementState settlement, long now) {
        for (CitizenRecord citizen : livingMembers(settlement)) {
            LifeStage before = citizen.development().lifeStage();
            if (!citizen.development().advanceIfDue(now)) continue;
            LifeStage after = citizen.development().lifeStage();
            citizen.development().householdId().flatMap(data.households()::find).ifPresent(household ->
                    household.addMember(citizen.citizenId(), after.mayPerformFullWork(), after.requiresGuardian(), now));
            if (before == LifeStage.ADOLESCENT && after == LifeStage.ADULT) {
                selectCareer(citizen);
                considerHouseholdSplit(settlement, citizen, now);
            }
        }
    }

    private void selectCareer(CitizenRecord citizen) {
        WorkRole selected = data.forApprentice(citizen.citizenId()).map(ApprenticeshipRecord::focus)
                .orElseGet(() -> citizen.development().experience().entrySet().stream()
                        .max(Map.Entry.comparingByValue()).map(Map.Entry::getKey)
                        .orElse(neededCareer(citizen.settlementId().orElseThrow())));
        citizen.development().setCareer(selected);
        citizen.setWorkRole(selected);
        data.forApprentice(citizen.citizenId()).ifPresent(ApprenticeshipRecord::complete);
    }

    private void reconcileGuardians(SettlementState settlement, long now) {
        for (HouseholdRecord household : data.households().forSettlement(settlement.settlementId())) {
            if (household.state() == HouseholdState.DISSOLVED || household.dependants().isEmpty()) continue;
            boolean validGuardian = household.guardians().stream()
                    .map(id -> CitizenSavedData.get(server).registry().find(id).orElse(null))
                    .anyMatch(c -> c != null && c.presence() != RuntimePresence.DECEASED
                            && c.development().lifeStage().mayPerformFullWork());
            if (validGuardian) continue;
            CitizenRecord fallback = livingMembers(settlement).stream()
                    .filter(c -> c.development().lifeStage().mayPerformFullWork())
                    .filter(c -> !household.dependants().contains(c.citizenId())).findFirst().orElse(null);
            if (fallback == null) {
                household.setState(HouseholdState.NEEDS_GUARDIAN);
                continue;
            }
            UUID oldHousehold = fallback.development().householdId().orElse(null);
            if (oldHousehold != null && !oldHousehold.equals(household.householdId())) {
                data.households().find(oldHousehold).ifPresent(h -> h.removeMember(fallback.citizenId(), now));
            }
            household.addMember(fallback.citizenId(), true, false, now);
            fallback.development().setHousehold(household.householdId());
            household.setState(HouseholdState.STABLE);
        }
    }

    private void assignHomes(SettlementState settlement, long now) {
        List<BuildingRecord> homes = ConstructionSavedData.get(server).buildings().stream()
                .filter(b -> b.settlementId().equals(settlement.settlementId()))
                .filter(b -> b.type().equals(BuildingDemandService.STARTER_HOUSE))
                .filter(CivilisationServices.buildingDemand(server)::isUsableResidential).toList();
        Map<UUID, Integer> occupied = new HashMap<>();
        for (HouseholdRecord household : data.households().forSettlement(settlement.settlementId())) {
            household.homeBuildingId().ifPresent(id -> occupied.merge(id, household.members().size(), Integer::sum));
        }
        for (HouseholdRecord household : data.households().forSettlement(settlement.settlementId())) {
            if (household.state() == HouseholdState.DISSOLVED) continue;
            BuildingRecord current = household.homeBuildingId().flatMap(this::building).orElse(null);
            if (current != null && current.settlementId().equals(settlement.settlementId())
                    && CivilisationServices.buildingDemand(server).isUsableResidential(current)
                    && occupied.getOrDefault(current.buildingId(), 0) <= current.capacity()) {
                applyHome(household, current);
                continue;
            }
            if (current != null) occupied.merge(current.buildingId(), -household.members().size(), Integer::sum);
            BuildingRecord chosen = homes.stream().filter(home -> home.capacity()
                    - occupied.getOrDefault(home.buildingId(), 0) >= household.members().size())
                    .min(Comparator.comparingDouble(home -> home.anchor().pos().distSqr(settlement.centre().pos())))
                    .orElse(null);
            if (chosen == null) {
                household.setHome(null, now);
                household.members().forEach(id -> CitizenSavedData.get(server).registry().find(id)
                        .ifPresent(citizen -> citizen.assignments().clearHome()));
            } else {
                household.setHome(chosen.buildingId(), now);
                occupied.merge(chosen.buildingId(), household.members().size(), Integer::sum);
                applyHome(household, chosen);
            }
        }
    }

    private void applyHome(HouseholdRecord household, BuildingRecord home) {
        household.members().forEach(id -> CitizenSavedData.get(server).registry().find(id)
                .ifPresent(citizen -> citizen.assignments().setHome(home.anchor())));
    }

    private void reconcileApprenticeships(SettlementState settlement, long now) {
        for (ApprenticeshipRecord apprenticeship : data.apprenticeships()) {
            if (!apprenticeship.settlementId().equals(settlement.settlementId())
                    || apprenticeship.state() == ApprenticeshipState.COMPLETE) continue;
            CitizenRecord apprentice = CitizenSavedData.get(server).registry().find(apprenticeship.apprenticeId()).orElse(null);
            if (apprentice == null || apprentice.presence() == RuntimePresence.DECEASED
                    || apprentice.development().lifeStage() == LifeStage.ADULT) {
                apprenticeship.complete();
                continue;
            }
            CitizenRecord mentor = apprenticeship.mentorId()
                    .flatMap(CitizenSavedData.get(server).registry()::find).orElse(null);
            if (mentor == null || mentor.presence() == RuntimePresence.DECEASED
                    || mentor.settlementId().filter(settlement.settlementId()::equals).isEmpty()) apprenticeship.pause();
            if (apprenticeship.state() == ApprenticeshipState.PAUSED_NO_MENTOR) {
                CitizenRecord replacement = chooseMentor(settlement.settlementId(), apprenticeship.focus(),
                        apprenticeship.apprenticeId());
                if (replacement != null) apprenticeship.assignMentor(replacement.citizenId());
            }
        }
        for (CitizenRecord adolescent : livingMembers(settlement)) {
            if (adolescent.development().lifeStage() != LifeStage.ADOLESCENT
                    || data.forApprentice(adolescent.citizenId()).isPresent()) continue;
            WorkRole focus = neededCareer(settlement.settlementId());
            CitizenRecord mentor = chooseMentor(settlement.settlementId(), focus, adolescent.citizenId());
            if (mentor == null) mentor = chooseMentor(settlement.settlementId(), null, adolescent.citizenId());
            if (mentor != null) {
                data.add(new ApprenticeshipRecord(UUID.randomUUID(), settlement.settlementId(),
                        adolescent.citizenId(), mentor.citizenId(), focus, now));
                adolescent.history().add(now, "APPRENTICESHIP", "Became " + mentor.displayName()
                        + "'s " + focus.displayName() + " apprentice");
            }
        }
    }

    private CitizenRecord chooseMentor(UUID settlementId, WorkRole requested, UUID excluded) {
        return CitizenSavedData.get(server).registry().all().stream()
                .filter(c -> c.settlementId().filter(settlementId::equals).isPresent())
                .filter(c -> !c.citizenId().equals(excluded) && c.presence() != RuntimePresence.DECEASED)
                .filter(c -> c.development().lifeStage().mayPerformFullWork())
                .filter(c -> requested == null || c.workRole() == requested || c.development().career() == requested)
                .filter(this::isPhysicallyAvailable)
                .max(Comparator.comparingInt(c -> c.development()
                        .experience(requested == null ? c.workRole() : requested))).orElse(null);
    }

    private boolean isPhysicallyAvailable(CitizenRecord citizen) {
        return CivilisationServices.activeCitizens(server).bodyFor(citizen.citizenId()).isPresent();
    }

    private WorkRole neededCareer(UUID settlementId) {
        Map<WorkRole, Integer> adults = new EnumMap<>(WorkRole.class);
        Map<WorkRole, Integer> elders = new EnumMap<>(WorkRole.class);
        CitizenSavedData.get(server).registry().all().stream()
                .filter(c -> c.settlementId().filter(settlementId::equals).isPresent())
                .filter(c -> c.presence() != RuntimePresence.DECEASED).forEach(c -> {
                    if (c.development().lifeStage() == LifeStage.ELDER) elders.merge(c.workRole(), 1, Integer::sum);
                    if (c.development().lifeStage() == LifeStage.ADULT) adults.merge(c.workRole(), 1, Integer::sum);
                });
        int desiredBuilders = CivilisationServices.construction(server).desiredBuilders(settlementId);
        int builderCareers = (int) CitizenSavedData.get(server).registry().all().stream()
                .filter(c -> c.settlementId().filter(settlementId::equals).isPresent())
                .filter(c -> c.presence() != RuntimePresence.DECEASED
                        && c.development().lifeStage() == LifeStage.ADULT)
                .filter(c -> c.workRole() == WorkRole.BUILDER
                        || c.development().career() == WorkRole.BUILDER)
                .count();
        if (desiredBuilders > builderCareers) return WorkRole.BUILDER;
        return SUCCESSION_ROLES.stream().max(Comparator.comparingInt(role ->
                elders.getOrDefault(role, 0) * 3 - adults.getOrDefault(role, 0))).orElse(WorkRole.FARMER);
    }

    private com.tyes.civilisation.entity.CivilianEntity activeBody(UUID citizenId) {
        UUID entityId = CivilisationServices.activeCitizens(server).bodyFor(citizenId).orElse(null);
        if (entityId == null) return null;
        for (ServerLevel level : server.getAllLevels()) {
            if (level.getEntity(entityId) instanceof com.tyes.civilisation.entity.CivilianEntity civilian) return civilian;
        }
        return null;
    }

    private void considerHouseholdSplit(SettlementState settlement, CitizenRecord adult, long now) {
        HouseholdRecord household = adult.development().householdId().flatMap(data.households()::find).orElse(null);
        if (household == null || household.members().size() <= 2 || now - household.lastChangedTick() < SPLIT_COOLDOWN) return;
        boolean freeHome = ConstructionSavedData.get(server).buildings().stream()
                .filter(b -> b.settlementId().equals(settlement.settlementId()))
                .filter(b -> b.type().equals(BuildingDemandService.STARTER_HOUSE))
                .anyMatch(b -> data.households().forSettlement(settlement.settlementId()).stream()
                        .noneMatch(h -> h.homeBuildingId().filter(b.buildingId()::equals).isPresent()));
        if (!freeHome) return;
        household.removeMember(adult.citizenId(), now);
        adult.development().setHousehold(null);
        createHousehold(settlement, List.of(adult), now);
    }

    private void considerGrowth(SettlementState settlement, long now) {
        int population = livingMembers(settlement).size();
        int capacity = CivilisationServices.buildingDemand(server).housingStatus(settlement).completedCapacity();
        if (capacity <= population || !hasFoodOutlook(settlement, population)) return;
        HouseholdRecord household = data.households().forSettlement(settlement.settlementId()).stream()
                .filter(h -> h.guardians().size() >= 2 && h.members().size() < 4)
                .filter(h -> now - h.lastGrowthTick() >= GROWTH_COOLDOWN)
                .filter(h -> h.homeBuildingId().flatMap(this::building)
                        .map(home -> home.capacity() > h.members().size()).orElse(false))
                .findFirst().orElse(null);
        if (household != null) createChild(settlement, household, now);
    }

    private boolean hasFoodOutlook(SettlementState settlement, int population) {
        int food = 0;
        for (AssignedLocation storage : settlement.storageLocations()) {
            ServerLevel level = server.getLevel(net.minecraft.resources.ResourceKey.create(
                    net.minecraft.core.registries.Registries.DIMENSION, storage.dimension()));
            if (level == null || !level.isLoaded(storage.pos())
                    || !(level.getBlockEntity(storage.pos()) instanceof Container container)) continue;
            for (int slot = 0; slot < container.getContainerSize(); slot++) {
                ItemStack stack = container.getItem(slot);
                if (stack.has(DataComponents.FOOD)) food += stack.getCount();
            }
        }
        return food >= Math.max(8, population * 2);
    }

    private Optional<BuildingRecord> building(UUID id) {
        return ConstructionSavedData.get(server).buildings().stream().filter(b -> b.buildingId().equals(id)).findFirst();
    }

    private List<CitizenRecord> livingMembers(SettlementState settlement) {
        List<CitizenRecord> result = new ArrayList<>();
        for (UUID id : settlement.members()) CitizenSavedData.get(server).registry().find(id)
                .filter(c -> c.presence() != RuntimePresence.DECEASED).ifPresent(result::add);
        return result;
    }

    public record LearningOpportunity(AssignedLocation location, UUID mentorId, WorkRole focus,
                                      UUID apprenticeshipId) {
        public Optional<UUID> apprenticeship() { return Optional.ofNullable(apprenticeshipId); }
    }
    public record EducationStatus(int learners, int teachers, int capacity) {
        public double effectiveness() {
            return learners == 0 || learners <= capacity ? 1.0D : Math.max(0.1D, (double)capacity / learners);
        }
    }
}
