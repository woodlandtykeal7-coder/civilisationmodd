package com.tyes.civilisation.server;

import com.tyes.civilisation.citizen.CitizenRecord;
import com.tyes.civilisation.persistence.CitizenSavedData;
import com.tyes.civilisation.persistence.SettlementSavedData;
import com.tyes.civilisation.settlement.SettlementState;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;

/** Mutates the settlement-owned member set and the citizen's reference atomically on the server thread. */
public final class SettlementMembershipService {
    private SettlementMembershipService() {}

    public static boolean join(MinecraftServer server, CitizenRecord citizen, SettlementState settlement) {
        SettlementSavedData settlements = SettlementSavedData.get(server);
        CitizenSavedData citizens = CitizenSavedData.get(server);
        UUID previousId = citizen.settlementId().orElse(null);
        if (settlement.settlementId().equals(previousId)) {
            if (settlement.addMember(citizen.citizenId())) settlements.setDirty();
            return true;
        }
        if (previousId != null) settlements.registry().find(previousId).ifPresent(s -> s.removeMember(citizen.citizenId()));
        if (previousId != null) CivilisationServices.demography(server).detachFromHousehold(citizen);
        settlement.addMember(citizen.citizenId());
        citizen.setSettlementReference(settlement.settlementId());
        citizen.history().add(server.overworld().getGameTime(), previousId == null ? "SETTLEMENT" : "MIGRATION",
                (previousId == null ? "Joined " : "Migrated to ") + settlement.name());
        settlements.setDirty();
        citizens.setDirty();
        return true;
    }

    public static void leave(MinecraftServer server, CitizenRecord citizen) {
        UUID previousId = citizen.settlementId().orElse(null);
        if (previousId == null) return;
        SettlementSavedData settlements = SettlementSavedData.get(server);
        CivilisationServices.demography(server).detachFromHousehold(citizen);
        settlements.registry().find(previousId).ifPresent(s -> s.removeMember(citizen.citizenId()));
        citizen.setSettlementReference(null);
        citizen.history().add(server.overworld().getGameTime(), "MIGRATION", "Left their settlement");
        settlements.setDirty();
        CitizenSavedData.get(server).setDirty();
    }
}
