package com.tyes.civilisation.society;

public enum LifeStage {
    CHILD,
    ADOLESCENT,
    ADULT,
    ELDER;

    public boolean mayPerformFullWork() { return this == ADULT || this == ELDER; }
    public boolean requiresGuardian() { return this == CHILD || this == ADOLESCENT; }
}
