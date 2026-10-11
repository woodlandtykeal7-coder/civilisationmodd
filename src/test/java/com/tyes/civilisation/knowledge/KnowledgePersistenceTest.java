package com.tyes.civilisation.knowledge;

import static org.junit.jupiter.api.Assertions.*;

import com.tyes.civilisation.citizen.CitizenRecord;
import com.tyes.civilisation.knowledge.research.ResearchAttempt;
import com.tyes.civilisation.knowledge.research.ResearchOutcome;
import com.tyes.civilisation.knowledge.research.ResearchProject;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class KnowledgePersistenceTest {
    @Test void levelsAreGradualAndNotBoolean() {
        CitizenKnowledge knowledge = new CitizenKnowledge();
        assertEquals(KnowledgeLevel.UNKNOWN, knowledge.conceptLevel(KnowledgeConcepts.BASIC_CARPENTRY));
        knowledge.addConcept(KnowledgeConcepts.BASIC_CARPENTRY, 1);
        assertEquals(KnowledgeLevel.OBSERVED, knowledge.conceptLevel(KnowledgeConcepts.BASIC_CARPENTRY));
        knowledge.addConcept(KnowledgeConcepts.BASIC_CARPENTRY, 24);
        assertEquals(KnowledgeLevel.FAMILIAR, knowledge.conceptLevel(KnowledgeConcepts.BASIC_CARPENTRY));
        knowledge.addConcept(KnowledgeConcepts.BASIC_CARPENTRY, 50);
        assertEquals(KnowledgeLevel.UNDERSTOOD, knowledge.conceptLevel(KnowledgeConcepts.BASIC_CARPENTRY));
        knowledge.addConcept(KnowledgeConcepts.BASIC_CARPENTRY, 125);
        assertEquals(KnowledgeLevel.MASTERED, knowledge.conceptLevel(KnowledgeConcepts.BASIC_CARPENTRY));
    }

    @Test void citizenKnowledgeAndHistoryRoundTrip() {
        CitizenRecord citizen = new CitizenRecord(UUID.randomUUID(), "C-0042", "Mira", 42L);
        ResourceLocation recipe = ResourceLocation.parse("minecraft:oak_planks");
        citizen.knowledge().addConcept(KnowledgeConcepts.BASIC_CARPENTRY, 84);
        citizen.knowledge().addRecipe(recipe, 31);
        citizen.history().add(120L, "CAREER", "Became a Builder");
        CitizenRecord loaded = CitizenRecord.load(citizen.save());
        assertEquals(84, loaded.knowledge().conceptPoints(KnowledgeConcepts.BASIC_CARPENTRY));
        assertEquals(31, loaded.knowledge().recipePoints(recipe));
        assertEquals("Became a Builder", loaded.history().events().getFirst().description());
    }

    @Test void negativeResearchOutcomePreventsBlindRepeatAndPersists() {
        ResearchProject project = new ResearchProject(UUID.randomUUID(), UUID.randomUUID(), "R-0001",
                ResourceLocation.parse("civilisation:test_scaffolding"), KnowledgeConcepts.BASIC_SCAFFOLDING);
        project.addAttempt(new ResearchAttempt(400L, "oak_without_support", ResearchOutcome.NEGATIVE,
                "Collapsed under load"));
        ResearchProject loaded = ResearchProject.load(project.save());
        assertTrue(loaded.hasFailedApproach("oak_without_support"));
        assertFalse(loaded.hasFailedApproach("oak_with_bracing"));
    }
}
