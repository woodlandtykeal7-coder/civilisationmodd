package com.tyes.civilisation.economy.shortage;

import com.tyes.civilisation.economy.SettlementEconomyState;
import com.tyes.civilisation.economy.forecast.DemandForecast;

/** Hysteretic shortage classification; callers supply physical counts. */
public final class ShortageService {
    public void update(SettlementEconomyState state, DemandForecast forecast, int food, int seeds, int fuel) {
        state.setShortage(ShortageType.FOOD, hysteresis(state, ShortageType.FOOD, ratio(food, forecast.nearTermFood())));
        state.setShortage(ShortageType.SEEDS, hysteresis(state, ShortageType.SEEDS, ratio(seeds, forecast.seedReserve())));
        state.setShortage(ShortageType.TOOLS, hysteresis(state, ShortageType.TOOLS,
                ratio(forecast.usableAxes(), forecast.lumberjacks())));
        state.setShortage(ShortageType.FUEL, forecast.fuelDemand() == 0 ? ShortageSeverity.NONE
                : hysteresis(state, ShortageType.FUEL, ratio(fuel, forecast.fuelDemand())));
    }
    private static double ratio(int current, int required) { return required <= 0 ? 2.0D : (double)current / required; }
    private static ShortageSeverity hysteresis(SettlementEconomyState state, ShortageType type, double ratio) {
        ShortageSeverity current = state.shortages().getOrDefault(type, ShortageSeverity.NONE);
        if (current == ShortageSeverity.CRITICAL && ratio < 0.25D) return current;
        if (current == ShortageSeverity.SERIOUS && ratio < 0.50D) return current;
        if (ratio < 0.15D) return ShortageSeverity.CRITICAL;
        if (ratio < 0.35D) return ShortageSeverity.SERIOUS;
        if (ratio < 0.65D) return ShortageSeverity.MODERATE;
        if (ratio < 1.0D) return ShortageSeverity.LOW;
        return ShortageSeverity.NONE;
    }
}
