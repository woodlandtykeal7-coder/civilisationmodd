package com.tyes.civilisation.brain;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tyes.civilisation.citizen.CitizenNeeds;
import org.junit.jupiter.api.Test;

class TwentyDayRoutineTest {
    @Test
    void needsAndScheduleRemainStableForTwentyMinecraftDays() {
        CitizenNeeds needs = new CitizenNeeds();
        int meals = 0;
        int sleeps = 0;
        boolean wasSleeping = false;

        // Needs update once per second: 1,200 updates per Minecraft day.
        for (int second = 0; second < 20 * 1200; second++) {
            long dayTime = (second * 20L) % 24000L;
            boolean sleeping = CitizenSchedule.isSleepTime(dayTime, 0);
            if (sleeping) needs.tickSleeping(); else needs.tickAwake();
            if (sleeping && !wasSleeping) sleeps++;
            wasSleeping = sleeping;
            if (!sleeping && needs.isHungry()) {
                needs.eat(5); // bread nutrition
                meals++;
            }
            assertTrue(needs.hunger() >= 0.0D && needs.hunger() <= 100.0D);
            assertTrue(needs.fatigue() >= 0.0D && needs.fatigue() <= 100.0D);
        }

        assertTrue(meals >= 20 && meals < 80, "bounded meal decisions, not task thrashing");
        assertTrue(sleeps >= 19 && sleeps <= 21, "one sleep window per day");
        assertTrue(needs.fatigue() < CitizenNeeds.CRITICAL_FATIGUE);
    }
}
