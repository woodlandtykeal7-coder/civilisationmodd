package com.tyes.civilisation.society;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tyes.civilisation.settlement.WorkRole;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class CitizenDevelopmentTest {
    @Test
    void scheduledLifeStagesAndCareerProgressPersist() {
        CitizenDevelopment development = CitizenDevelopment.child(100L);
        UUID household = UUID.randomUUID();
        development.setHousehold(household);
        development.addEducation(12);
        development.addExperience(WorkRole.BUILDER, 75);
        development.setCareer(WorkRole.BUILDER);

        assertFalse(development.advanceIfDue(100L + CitizenDevelopment.CHILD_DURATION - 1L));
        assertTrue(development.advanceIfDue(100L + CitizenDevelopment.CHILD_DURATION));
        assertEquals(LifeStage.ADOLESCENT, development.lifeStage());

        CitizenDevelopment restored = CitizenDevelopment.load(development.save());
        assertEquals(LifeStage.ADOLESCENT, restored.lifeStage());
        assertEquals(household, restored.householdId().orElseThrow());
        assertEquals(12, restored.education());
        assertEquals(75, restored.experience(WorkRole.BUILDER));
        assertEquals(CareerLevel.TRAINED, restored.careerLevel());
    }

    @Test
    void existingCitizensDefaultToSafeAdultState() {
        CitizenDevelopment development = new CitizenDevelopment();
        assertEquals(LifeStage.ADULT, development.lifeStage());
        assertTrue(development.lifeStage().mayPerformFullWork());
        assertEquals(-1L, development.nextTransitionTick());
    }
}
