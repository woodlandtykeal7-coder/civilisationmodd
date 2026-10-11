package com.tyes.civilisation.settlement;

public enum WorkRole {
    GENERAL_LABOURER,
    FORAGER,
    FARMER,
    LUMBERJACK,
    HAULER,
    CRAFTER,
    SMELTER,
    COURIER,
    MINER,
    BUILDER,
    TEACHER;

    public static WorkRole parse(String value) {
        try { return valueOf(value); } catch (IllegalArgumentException ignored) { return GENERAL_LABOURER; }
    }

    public String displayName() {
        String lower = name().toLowerCase().replace('_', ' ');
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }
}
