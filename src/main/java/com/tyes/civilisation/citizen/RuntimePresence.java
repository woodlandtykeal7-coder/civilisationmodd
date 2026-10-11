package com.tyes.civilisation.citizen;

public enum RuntimePresence {
    PENDING_MATERIALIZATION,
    MATERIALIZED,
    CHUNK_SERIALIZED,
    VIRTUAL_TRAVEL,
    ABSTRACT,
    DECEASED;

    public static RuntimePresence parse(String value) {
        try {
            return valueOf(value);
        } catch (IllegalArgumentException | NullPointerException ignored) {
            return ABSTRACT;
        }
    }
}
