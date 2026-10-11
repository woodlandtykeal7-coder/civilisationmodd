package com.tyes.civilisation.economy.forecast;

import com.tyes.civilisation.citizen.CitizenRecord;
import com.tyes.civilisation.persistence.CitizenSavedData;
import com.tyes.civilisation.settlement.SettlementState;
import com.tyes.civilisation.settlement.WorkRole;
import net.minecraft.server.MinecraftServer;

/** One shared, intentionally simple near-term survival/capacity forecast. */
public final class DemandForecastService {
    public DemandForecast forecast(MinecraftServer server, SettlementState settlement, int usableAxes,
                                   boolean activeSmeltingDemand) {
        var citizens = CitizenSavedData.get(server).registry();
        int population = settlement.members().size();
        int lumberjacks = 0;
        for (java.util.UUID id : settlement.members()) {
            CitizenRecord record = citizens.find(id).orElse(null);
            if (record != null && record.workRole() == WorkRole.LUMBERJACK) lumberjacks++;
        }
        int food = Math.max(4, population * 2);
        return new DemandForecast(population, food, food, Math.max(8, population), lumberjacks,
                usableAxes, activeSmeltingDemand ? Math.max(1, population / 4) : 0);
    }
}
