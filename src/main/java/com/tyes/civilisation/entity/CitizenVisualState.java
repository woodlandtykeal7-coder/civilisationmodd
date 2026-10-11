package com.tyes.civilisation.entity;

/** Compact, presentation-only state synchronised to tracking clients. */
public enum CitizenVisualState {
    IDLE,
    WALKING,
    WORKING,
    EATING,
    SLEEPING,
    HARVESTING,
    CHOPPING,
    HAULING,
    RECOVERING;

    public static CitizenVisualState fromNetwork(int value) {
        CitizenVisualState[] values = values();
        return value >= 0 && value < values.length ? values[value] : IDLE;
    }
}
