package com.tyes.civilisation.knowledge;

/** Gradual personal familiarity; points remain authoritative and portable with the citizen. */
public enum KnowledgeLevel {
    UNKNOWN(0, 1),
    OBSERVED(1, 25),
    FAMILIAR(25, 75),
    UNDERSTOOD(75, 200),
    MASTERED(200, Integer.MAX_VALUE);

    private final int minimum;
    private final int next;

    KnowledgeLevel(int minimum, int next) { this.minimum = minimum; this.next = next; }
    public int minimum() { return minimum; }
    public int nextThreshold() { return next; }

    public static KnowledgeLevel fromPoints(int points) {
        if (points >= MASTERED.minimum) return MASTERED;
        if (points >= UNDERSTOOD.minimum) return UNDERSTOOD;
        if (points >= FAMILIAR.minimum) return FAMILIAR;
        if (points >= OBSERVED.minimum) return OBSERVED;
        return UNKNOWN;
    }
}
