package com.tyes.civilisation.construction.project;

/** Persistent need priority. It affects coordination, never block placement speed. */
public enum ConstructionPriority {
    CRITICAL,
    HIGH,
    NORMAL,
    LOW;

    public boolean outranks(ConstructionPriority other) {
        return ordinal() < other.ordinal();
    }
}
