package com.tyes.civilisation.dashboard;

import com.tyes.civilisation.citizen.CitizenRecord;
import com.tyes.civilisation.construction.building.BuildingRecord;
import com.tyes.civilisation.construction.project.ConstructionProject;
import com.tyes.civilisation.entity.CivilianEntity;
import com.tyes.civilisation.knowledge.KnowledgeConcepts;
import com.tyes.civilisation.knowledge.KnowledgeLevel;
import com.tyes.civilisation.persistence.CitizenSavedData;
import com.tyes.civilisation.persistence.ConstructionSavedData;
import com.tyes.civilisation.persistence.DemographySavedData;
import com.tyes.civilisation.persistence.SettlementSavedData;
import com.tyes.civilisation.server.CivilisationServices;
import com.tyes.civilisation.settlement.SettlementState;
import com.tyes.civilisation.society.HouseholdRecord;
import java.util.Comparator;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;

/** Creates compact immutable dashboard DTOs. Never sends a full CitizenRecord. */
public final class DashboardSnapshotFactory {
    private DashboardSnapshotFactory() {}

    public static CompoundTag citizen(MinecraftServer server, CitizenRecord citizen) {
        CompoundTag out = base(DashboardView.CITIZEN, citizen.citizenId());
        out.putString("name", citizen.displayName());
        out.putString("readable", citizen.readableId());
        out.putString("stage", citizen.development().lifeStage().name());
        out.putString("career", citizen.development().career().displayName());
        out.putString("careerLevel", citizen.development().careerLevel().name());
        out.putString("workRole", citizen.workRole().displayName());
        out.putDouble("hunger", citizen.needs().hunger());
        out.putDouble("fatigue", citizen.needs().fatigue());
        out.putString("task", citizen.brainPersistence().activeTaskType());
        activeBody(server, citizen.citizenId()).ifPresent(body -> out.putString("activity", body.visualState().name()));

        citizen.settlementId().flatMap(SettlementSavedData.get(server).registry()::find).ifPresent(settlement -> {
            out.putUUID("settlementId", settlement.settlementId());
            out.putString("settlement", settlement.name());
            out.putString("settlementReadable", settlement.readableId());
        });

        citizen.development().householdId().flatMap(DemographySavedData.get(server).households()::find)
                .ifPresent(household -> addHousehold(server, out, household));
        addWork(server, out, citizen);
        addSkills(out, citizen);
        addKnowledge(out, citizen);
        addInventory(server, out, citizen);
        ListTag history = new ListTag();
        citizen.history().events().forEach(event -> history.add(event.save()));
        out.put("history", history);
        DemographySavedData.get(server).forApprentice(citizen.citizenId()).ifPresent(apprenticeship -> {
            out.putString("apprenticeship", apprenticeship.focus().displayName());
            apprenticeship.mentorId().flatMap(CitizenSavedData.get(server).registry()::find)
                    .ifPresent(mentor -> out.putString("mentor", mentor.displayName() + " — " + mentor.readableId()));
        });
        ListTag apprentices = new ListTag();
        DemographySavedData.get(server).apprenticeships().stream()
                .filter(a -> a.mentorId().filter(citizen.citizenId()::equals).isPresent()
                        && a.state() == com.tyes.civilisation.society.ApprenticeshipState.ACTIVE)
                .forEach(a -> CitizenSavedData.get(server).registry().find(a.apprenticeId()).ifPresent(apprentice -> {
                    CompoundTag row = new CompoundTag(); row.putString("name", apprentice.displayName());
                    row.putString("readable", apprentice.readableId()); row.putString("focus", a.focus().displayName());
                    apprentices.add(row);
                }));
        out.put("apprentices", apprentices);
        return out;
    }

    public static CompoundTag settlement(MinecraftServer server, SettlementState settlement) {
        CompoundTag out = base(DashboardView.SETTLEMENT, settlement.settlementId());
        out.putString("name", settlement.name());
        out.putString("readable", settlement.readableId());
        out.putString("stage", settlement.stage().name());
        var citizens = CitizenSavedData.get(server).registry();
        ListTag people = new ListTag();
        settlement.members().stream().map(citizens::find).flatMap(java.util.Optional::stream)
                .sorted(Comparator.comparing(CitizenRecord::displayName)).forEach(citizen -> {
                    CompoundTag row = new CompoundTag();
                    row.putUUID("id", citizen.citizenId()); row.putString("name", citizen.displayName());
                    row.putString("readable", citizen.readableId());
                    row.putString("stage", citizen.development().lifeStage().name());
                    row.putString("career", citizen.development().career().displayName());
                    row.putString("activity", activeBody(server, citizen.citizenId())
                            .map(body -> body.visualState().name()).orElse("UNLOADED"));
                    people.add(row);
                });
        out.put("citizens", people);
        out.putInt("population", people.size());
        var households = DemographySavedData.get(server).households().forSettlement(settlement.settlementId());
        out.putInt("households", households.size());
        out.putInt("needsHomes", (int) households.stream().filter(h -> h.state().name().equals("NEEDS_HOME")).count());
        ListTag householdRows = new ListTag();
        households.forEach(h -> { CompoundTag row = new CompoundTag(); row.putString("readable", h.readableId());
            row.putString("state", h.state().name()); row.putInt("members", h.members().size());
            row.putString("home", h.homeBuildingId().map(DashboardSnapshotFactory::shortBuilding).orElse("none"));
            householdRows.add(row); });
        out.put("householdRows", householdRows);
        var housing = CivilisationServices.buildingDemand(server).housingStatus(settlement);
        out.putInt("housingCapacity", housing.completedCapacity());
        out.putInt("futureCapacity", housing.futureCapacity());
        var economy = CivilisationServices.economy(server).data().forSettlement(settlement.settlementId());
        ListTag shortages = new ListTag();
        economy.shortages().forEach((type, severity) -> {
            if (!severity.name().equals("NONE")) { CompoundTag row = new CompoundTag(); row.putString("type", type.name());
                row.putString("severity", severity.name()); shortages.add(row); }
        });
        out.put("shortages", shortages);
        ListTag buildings = new ListTag();
        ConstructionSavedData construction = ConstructionSavedData.get(server);
        construction.buildings().stream().filter(b -> b.settlementId().equals(settlement.settlementId()))
                .forEach(b -> buildings.add(buildingRow(b)));
        construction.projects().stream().filter(p -> p.settlementId().equals(settlement.settlementId()))
                .forEach(p -> buildings.add(projectRow(server, p)));
        out.put("buildings", buildings);
        addKnowledgeSummary(server, settlement, out);
        return out;
    }

    public static CompoundTag building(MinecraftServer server, BuildingRecord building) {
        CompoundTag out = base(DashboardView.BUILDING, building.buildingId());
        out.merge(buildingRow(building));
        out.putString("style", building.style().value().toString());
        out.putString("anchor", building.anchor().pos().toShortString());
        ListTag residents = new ListTag();
        for (HouseholdRecord household : DemographySavedData.get(server).households().forSettlement(building.settlementId())) {
            if (household.homeBuildingId().filter(building.buildingId()::equals).isEmpty()) continue;
            household.members().stream().map(CitizenSavedData.get(server).registry()::find)
                    .flatMap(java.util.Optional::stream).forEach(c -> {
                        CompoundTag row = new CompoundTag(); row.putUUID("id", c.citizenId());
                        row.putString("name", c.displayName()); row.putString("readable", c.readableId()); residents.add(row);
                    });
        }
        out.put("residents", residents);
        ConstructionSavedData.get(server).project(building.projectId()).ifPresent(project -> {
            out.putLong("completedTick", building.constructionTick());
            out.putString("projectReadable", project.readableId());
        });
        return out;
    }

    private static CompoundTag base(DashboardView view, UUID target) {
        CompoundTag out = new CompoundTag(); out.putString("view", view.name()); out.putUUID("target", target);
        return out;
    }

    private static void addHousehold(MinecraftServer server, CompoundTag out, HouseholdRecord household) {
        out.putUUID("householdId", household.householdId()); out.putString("household", household.readableId());
        out.putString("householdState", household.state().name());
        household.homeBuildingId().ifPresent(id -> out.putString("home", shortBuilding(id)));
        ListTag members = new ListTag();
        household.members().stream().map(CitizenSavedData.get(server).registry()::find)
                .flatMap(java.util.Optional::stream).forEach(member -> {
                    CompoundTag row = new CompoundTag(); row.putString("name", member.displayName());
                    row.putString("readable", member.readableId()); row.putString("stage", member.development().lifeStage().name());
                    row.putBoolean("guardian", household.guardians().contains(member.citizenId()));
                    row.putBoolean("dependant", household.dependants().contains(member.citizenId())); members.add(row);
                });
        out.put("householdMembers", members);
    }

    private static void addWork(MinecraftServer server, CompoundTag out, CitizenRecord citizen) {
        CompoundTag task = citizen.brainPersistence().activeTaskData();
        if (task.hasUUID("project")) ConstructionSavedData.get(server).project(task.getUUID("project")).ifPresent(p -> {
            out.putString("work", "Building " + p.blueprint().buildingType().getPath().replace('_', ' '));
            out.putString("workTarget", p.readableId()); out.putString("workReason", p.priorityReason());
            out.putString("workPriority", p.priority().name()); out.putInt("workDone", p.completedPlacements());
            out.putInt("workTotal", p.totalPlacements());
        });
        if (!out.contains("work")) out.putString("work", friendlyTask(citizen.brainPersistence().activeTaskType()));
    }

    private static void addSkills(CompoundTag out, CitizenRecord citizen) {
        ListTag list = new ListTag(); citizen.development().experience().forEach((role, xp) -> {
            CompoundTag row = new CompoundTag(); row.putString("name", role.displayName()); row.putInt("xp", xp);
            row.putString("level", com.tyes.civilisation.society.CareerLevel.fromExperience(xp).name()); list.add(row);
        }); out.put("skills", list);
    }

    private static void addKnowledge(CompoundTag out, CitizenRecord citizen) {
        ListTag concepts = new ListTag(); citizen.knowledge().concepts().forEach((id, points) -> {
            CompoundTag row = new CompoundTag(); row.putString("id", id.toString());
            row.putString("name", KnowledgeConcepts.displayName(id)); row.putInt("points", points);
            row.putString("level", KnowledgeLevel.fromPoints(points).name());
            row.putBoolean("canTeach", KnowledgeLevel.fromPoints(points).ordinal() >= KnowledgeLevel.UNDERSTOOD.ordinal()); concepts.add(row);
        }); out.put("concepts", concepts);
        ListTag recipes = new ListTag(); citizen.knowledge().recipes().forEach((id, points) -> {
            CompoundTag row = new CompoundTag(); row.putString("id", id.toString()); row.putInt("points", points);
            row.putString("level", KnowledgeLevel.fromPoints(points).name());
            row.putBoolean("canTeach", KnowledgeLevel.fromPoints(points).ordinal() >= KnowledgeLevel.UNDERSTOOD.ordinal()); recipes.add(row);
        }); out.put("recipes", recipes);
    }

    private static void addInventory(MinecraftServer server, CompoundTag out, CitizenRecord citizen) {
        ListTag items = new ListTag();
        for (int slot = 0; slot < com.tyes.civilisation.citizen.CitizenInventory.SIZE; slot++) {
            ItemStack stack = citizen.inventory().get(slot);
            CompoundTag row = new CompoundTag(); row.putInt("slot", slot);
            if (!stack.isEmpty()) row.put("stack", stack.save(server.registryAccess()));
            items.add(row);
        }
        out.put("inventory", items);
    }

    private static void addKnowledgeSummary(MinecraftServer server, SettlementState settlement, CompoundTag out) {
        ListTag list = new ListTag();
        for (ResourceLocation concept : KnowledgeConcepts.INITIAL) {
            CompoundTag row = new CompoundTag(); row.putString("id", concept.toString());
            row.putString("name", KnowledgeConcepts.displayName(concept));
            for (KnowledgeLevel level : KnowledgeLevel.values()) row.putInt(level.name(), (int) settlement.members().stream()
                    .map(CitizenSavedData.get(server).registry()::find).flatMap(java.util.Optional::stream)
                    .filter(c -> c.presence() != com.tyes.civilisation.citizen.RuntimePresence.DECEASED)
                    .filter(c -> c.knowledge().conceptLevel(concept) == level).count());
            row.putInt("teachers", (int) CivilisationServices.knowledge(server)
                    .livingHolders(settlement.settlementId(), concept, KnowledgeLevel.UNDERSTOOD)); list.add(row);
        }
        out.put("knowledgeSummary", list);
    }

    private static CompoundTag buildingRow(BuildingRecord b) {
        CompoundTag row = new CompoundTag(); row.putUUID("id", b.buildingId()); row.putString("kind", "COMPLETE");
        row.putString("name", b.type().getPath().replace('_', ' ')); row.putString("buildingId", shortBuilding(b.buildingId()));
        row.putString("condition", b.condition().name()); row.putInt("capacity", b.capacity()); return row;
    }

    private static CompoundTag projectRow(MinecraftServer server, ConstructionProject p) {
        CompoundTag row = new CompoundTag(); row.putUUID("id", p.projectId()); row.putString("kind", "PROJECT");
        row.putString("name", p.blueprint().buildingType().getPath().replace('_', ' ')); row.putString("projectId", p.readableId());
        row.putString("state", p.state().name()); row.putString("blocker", p.blocker().name());
        row.putString("priority", p.priority().name()); row.putString("reason", p.priorityReason());
        row.putInt("done", p.completedPlacements()); row.putInt("total", p.totalPlacements());
        ListTag builders = new ListTag(); p.claims().values().stream().distinct()
                .map(CitizenSavedData.get(server).registry()::find).flatMap(java.util.Optional::stream).forEach(c -> {
                    CompoundTag builder = new CompoundTag(); builder.putString("name", c.displayName());
                    builder.putString("level", c.development().careerLevel().name()); builders.add(builder);
                }); row.put("builders", builders);
        ListTag materials = new ListTag(); CivilisationServices.construction(server).accounting(p).forEach((id, a) -> {
            CompoundTag material = new CompoundTag(); material.putString("item", id.toString());
            material.putInt("required", a.required()); material.putInt("available", a.placed() + a.stockpiled() + a.builderHeld());
            materials.add(material);
        }); row.put("materials", materials); return row;
    }

    private static java.util.Optional<CivilianEntity> activeBody(MinecraftServer server, UUID citizenId) {
        UUID bodyId = CivilisationServices.activeCitizens(server).bodyFor(citizenId).orElse(null);
        if (bodyId == null) return java.util.Optional.empty();
        for (ServerLevel level : server.getAllLevels()) if (level.getEntity(bodyId) instanceof CivilianEntity body)
            return java.util.Optional.of(body);
        return java.util.Optional.empty();
    }

    private static String friendlyTask(String task) {
        if (task == null || task.isBlank()) return "Idle";
        return switch (task) {
            case "construction_build" -> "Construction"; case "economy_production" -> "Production";
            case "economy_delivery" -> "Hauling"; case "settlement_work" -> "Settlement work";
            case "eat" -> "Eating"; case "sleep" -> "Sleeping"; case "learning" -> "Learning";
            default -> task.replace('_', ' ');
        };
    }

    private static String shortBuilding(UUID id) { return "B-" + id.toString().substring(0, 8).toUpperCase(); }
}
