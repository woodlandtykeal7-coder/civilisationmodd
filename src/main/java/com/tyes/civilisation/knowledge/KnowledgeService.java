package com.tyes.civilisation.knowledge;

import com.tyes.civilisation.citizen.CitizenRecord;
import com.tyes.civilisation.citizen.RuntimePresence;
import com.tyes.civilisation.entity.CivilianEntity;
import com.tyes.civilisation.persistence.CitizenSavedData;
import com.tyes.civilisation.server.CivilisationServices;
import com.tyes.civilisation.settlement.WorkRole;
import com.tyes.civilisation.work.WorkType;
import java.util.Comparator;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

/** Authoritative event-driven learning. Successful physical work is the primary source. */
public final class KnowledgeService {
    private static final double OBSERVATION_RADIUS_SQR = 144.0D;
    private final MinecraftServer server;

    public KnowledgeService(MinecraftServer server) { this.server = server; }

    public void recordRecipeUse(CitizenRecord citizen, ResourceLocation recipeId) {
        addRecipe(citizen, recipeId, 4, "Practised recipe " + recipeId);
        String path = recipeId.getPath();
        if (path.contains("plank") || path.contains("wood"))
            addConcept(citizen, KnowledgeConcepts.BASIC_CARPENTRY, 2, "Developed basic carpentry");
        if (path.contains("pickaxe") || path.contains("axe") || path.contains("hoe") || path.contains("shovel"))
            addConcept(citizen, KnowledgeConcepts.BASIC_TOOLMAKING, 2, "Developed basic toolmaking");
    }

    /** Optional physical player demonstration; substantial progress, still far from instant mastery. */
    public void demonstrateRecipe(CitizenRecord citizen, ResourceLocation recipeId) {
        addRecipe(citizen, recipeId, 30, "Observed a physical recipe demonstration for " + recipeId);
        String path = recipeId.getPath();
        if (path.contains("plank") || path.contains("wood"))
            addConcept(citizen, KnowledgeConcepts.BASIC_CARPENTRY, 8, "Observed a carpentry demonstration");
        if (path.contains("pickaxe") || path.contains("axe") || path.contains("hoe") || path.contains("shovel"))
            addConcept(citizen, KnowledgeConcepts.BASIC_TOOLMAKING, 8, "Observed a toolmaking demonstration");
    }

    public void recordConstruction(CitizenRecord citizen, boolean stoneBlock) {
        ResourceLocation concept = stoneBlock ? KnowledgeConcepts.STONE_FOUNDATIONS : KnowledgeConcepts.BASIC_CARPENTRY;
        addConcept(citizen, concept, 2, "Developed " + KnowledgeConcepts.displayName(concept));
        teachNearbyFromWork(citizen, concept);
    }

    public void recordWork(CitizenRecord citizen, WorkType type) {
        ResourceLocation concept = switch (type) {
            case FARM, FORAGE -> KnowledgeConcepts.BASIC_AGRICULTURE;
            case LUMBER -> KnowledgeConcepts.BASIC_CARPENTRY;
        };
        addConcept(citizen, concept, 2, "Developed " + KnowledgeConcepts.displayName(concept));
        teachNearbyFromWork(citizen, concept);
    }

    /** A completed, physical mentor session transfers progress, never instant mastery. */
    public void completeTeaching(CitizenRecord learner, CitizenRecord mentor, WorkRole focus, int sessionPoints) {
        ResourceLocation concept = conceptFor(focus);
        if (concept == null || mentor.knowledge().conceptLevel(concept).ordinal() < KnowledgeLevel.FAMILIAR.ordinal()) return;
        int mentorPoints = mentor.knowledge().conceptPoints(concept);
        if (learner.knowledge().conceptPoints(concept) >= mentorPoints) return;
        addConcept(learner, concept, Math.max(1, Math.min(5, sessionPoints)),
                "Learned " + KnowledgeConcepts.displayName(concept) + " from " + mentor.displayName());
    }

    private void teachNearbyFromWork(CitizenRecord worker, ResourceLocation concept) {
        CivilianEntity body = activeBody(worker.citizenId());
        if (body == null || worker.knowledge().conceptLevel(concept) == KnowledgeLevel.UNKNOWN) return;
        for (CivilianEntity observer : body.level().getEntitiesOfClass(CivilianEntity.class,
                body.getBoundingBox().inflate(12.0D), other -> other != body)) {
            if (observer.distanceToSqr(body) > OBSERVATION_RADIUS_SQR) continue;
            CitizenRecord learner = observer.citizenId().flatMap(CitizenSavedData.get(server).registry()::find).orElse(null);
            if (learner == null || learner.presence() == RuntimePresence.DECEASED
                    || learner.settlementId().isEmpty() || !learner.settlementId().equals(worker.settlementId())) continue;
            if (learner.knowledge().conceptPoints(concept) < worker.knowledge().conceptPoints(concept))
                addConcept(learner, concept, 1, "Observed " + worker.displayName() + " practising "
                        + KnowledgeConcepts.displayName(concept));
        }
    }

    public long livingHolders(UUID settlementId, ResourceLocation concept, KnowledgeLevel minimum) {
        return CitizenSavedData.get(server).registry().all().stream()
                .filter(c -> c.presence() != RuntimePresence.DECEASED)
                .filter(c -> c.settlementId().filter(settlementId::equals).isPresent())
                .filter(c -> c.knowledge().conceptLevel(concept).ordinal() >= minimum.ordinal()).count();
    }

    public CitizenRecord bestTeacher(UUID settlementId, ResourceLocation concept) {
        return CitizenSavedData.get(server).registry().all().stream()
                .filter(c -> c.presence() != RuntimePresence.DECEASED)
                .filter(c -> c.settlementId().filter(settlementId::equals).isPresent())
                .filter(c -> c.knowledge().conceptLevel(concept).ordinal() >= KnowledgeLevel.UNDERSTOOD.ordinal())
                .max(Comparator.comparingInt(c -> c.knowledge().conceptPoints(concept))).orElse(null);
    }

    private boolean addConcept(CitizenRecord citizen, ResourceLocation id, int points, String description) {
        KnowledgeLevel before = citizen.knowledge().conceptLevel(id);
        if (!citizen.knowledge().addConcept(id, points)) return false;
        KnowledgeLevel after = citizen.knowledge().conceptLevel(id);
        if (after != before) citizen.history().add(server.overworld().getGameTime(), "KNOWLEDGE",
                description + " — " + after.name());
        CitizenSavedData.get(server).setDirty();
        return true;
    }

    private void addRecipe(CitizenRecord citizen, ResourceLocation id, int points, String description) {
        KnowledgeLevel before = citizen.knowledge().recipeLevel(id);
        if (!citizen.knowledge().addRecipe(id, points)) return;
        KnowledgeLevel after = citizen.knowledge().recipeLevel(id);
        if (after != before) citizen.history().add(server.overworld().getGameTime(), "RECIPE",
                description + " — " + after.name());
        CitizenSavedData.get(server).setDirty();
    }

    private ResourceLocation conceptFor(WorkRole role) {
        return switch (role) {
            case BUILDER, LUMBERJACK -> KnowledgeConcepts.BASIC_CARPENTRY;
            case FARMER, FORAGER -> KnowledgeConcepts.BASIC_AGRICULTURE;
            case CRAFTER, SMELTER -> KnowledgeConcepts.BASIC_TOOLMAKING;
            default -> null;
        };
    }

    private CivilianEntity activeBody(UUID citizenId) {
        UUID entityId = CivilisationServices.activeCitizens(server).bodyFor(citizenId).orElse(null);
        if (entityId == null) return null;
        for (ServerLevel level : server.getAllLevels())
            if (level.getEntity(entityId) instanceof CivilianEntity civilian) return civilian;
        return null;
    }
}
