package com.tyes.civilisation.brain;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CitizenScheduleTest {
    @Test
    void workAndSleepWindowsDoNotOverlap() {
        for (long time = 0; time < 24000; time++) {
            assertFalse(CitizenSchedule.isWorkTime(time, 0) && CitizenSchedule.isSleepTime(time, 0));
        }
        assertTrue(CitizenSchedule.isWorkTime(6000, 0));
        assertTrue(CitizenSchedule.isSleepTime(18000, 0));
    }

    @Test
    void deterministicJitterSpreadsTransitions() {
        assertFalse(CitizenSchedule.isWorkTime(900, 0));
        assertTrue(CitizenSchedule.isWorkTime(900, 200));
    }
}
