package com.tyes.civilisation.entity;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class SleepCollisionPolicyTest {
    @Test void sleepingBodyIgnoresExternalPushes() {
        assertFalse(SleepCollisionPolicy.canReceivePush(true));
    }

    @Test void awakeBodyRegainsNormalCollisionBehaviour() {
        assertTrue(SleepCollisionPolicy.canReceivePush(false));
    }

    @Test void repeatedSleepWakeTransitionsNeverPermanentlyLockMovement() {
        assertTrue(SleepCollisionPolicy.canReceivePush(false));
        assertFalse(SleepCollisionPolicy.canReceivePush(true));
        assertFalse(SleepCollisionPolicy.canReceivePush(true));
        assertTrue(SleepCollisionPolicy.canReceivePush(false));
    }
}
