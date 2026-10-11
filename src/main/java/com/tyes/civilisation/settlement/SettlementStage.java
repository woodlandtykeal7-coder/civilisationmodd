package com.tyes.civilisation.settlement;

public enum SettlementStage {
    CAMP;

    public static SettlementStage parse(String value) {
        try { return valueOf(value); } catch (IllegalArgumentException ignored) { return CAMP; }
    }
}
