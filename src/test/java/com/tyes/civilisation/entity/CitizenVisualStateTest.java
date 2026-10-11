package com.tyes.civilisation.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class CitizenVisualStateTest {
    @Test
    void decodesEveryValidNetworkValue() {
        for (CitizenVisualState state : CitizenVisualState.values()) {
            assertEquals(state, CitizenVisualState.fromNetwork(state.ordinal()));
        }
    }

    @Test
    void invalidNetworkValuesFailSafeToIdle() {
        assertEquals(CitizenVisualState.IDLE, CitizenVisualState.fromNetwork(-1));
        assertEquals(CitizenVisualState.IDLE, CitizenVisualState.fromNetwork(255));
    }
}
