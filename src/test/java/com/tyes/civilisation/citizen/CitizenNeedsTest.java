package com.tyes.civilisation.citizen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CitizenNeedsTest {
    @Test
    void needsChangeOnlyWhenExplicitlyUpdatedAndStayBounded() {
        CitizenNeeds needs = new CitizenNeeds(99.9D, 99.9D);
        for (int i = 0; i < 100; i++) needs.tickAwake();
        assertEquals(100.0D, needs.hunger());
        assertEquals(100.0D, needs.fatigue());

        for (int i = 0; i < 1000; i++) needs.tickSleeping();
        assertEquals(100.0D, needs.hunger());
        assertEquals(0.0D, needs.fatigue());
    }

    @Test
    void physicalFoodNutritionReducesHunger() {
        CitizenNeeds needs = new CitizenNeeds(80.0D, 20.0D);
        needs.eat(5);
        assertEquals(45.0D, needs.hunger());
        assertTrue(!needs.isHungry());
    }
}
