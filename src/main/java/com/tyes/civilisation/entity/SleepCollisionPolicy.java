package com.tyes.civilisation.entity;

/**
 * Sleeping citizens remain on their assigned beds during ordinary entity collisions.
 * They become pushable again as soon as vanilla sleep is ended.
 */
public final class SleepCollisionPolicy {
    private SleepCollisionPolicy() {}

    public static boolean canReceivePush(boolean sleeping) {
        return !sleeping;
    }
}
