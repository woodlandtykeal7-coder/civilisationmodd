package com.tyes.civilisation.server;

import com.tyes.civilisation.CivilisationMod;
import com.tyes.civilisation.entity.CivilianEntity;
import com.tyes.civilisation.persistence.CitizenSavedData;
import com.tyes.civilisation.persistence.SettlementSavedData;
import com.tyes.civilisation.persistence.KnowledgeSavedData;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.EntityEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

public final class CivilisationEvents {
    @SubscribeEvent
    public void registerCommands(RegisterCommandsEvent event) {
        CivilisationCommands.register(event.getDispatcher());
    }

    @SubscribeEvent
    public void entityJoins(EntityJoinLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel serverLevel)
                || !(event.getEntity() instanceof CivilianEntity civilian)) return;
        if (!CitizenBindingService.bindLoadedBody(serverLevel, civilian)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void entityLeaves(EntityLeaveLevelEvent event) {
        if (event.getLevel() instanceof ServerLevel serverLevel
                && event.getEntity() instanceof CivilianEntity civilian) {
            CitizenBindingService.unbind(serverLevel, civilian);
        }
    }

    @SubscribeEvent
    public void entityChangesSection(EntityEvent.EnteringSection event) {
        if (!(event.getEntity() instanceof CivilianEntity civilian)
                || !(civilian.level() instanceof ServerLevel serverLevel)
                || civilian.citizenId().isEmpty()) return;
        ActiveCitizenRegistry active = CivilisationServices.activeCitizens(serverLevel.getServer());
        if (active.bodyFor(civilian.citizenId().orElseThrow()).isEmpty()
                && !CitizenBindingService.bindLoadedBody(serverLevel, civilian)) {
            CivilisationMod.LOGGER.error("Discarding civilian body {} after a failed section-transition rebind",
                    civilian.getUUID());
            civilian.discard();
        }
    }

    @SubscribeEvent
    public void serverStarted(ServerStartedEvent event) {
        CitizenSavedData.get(event.getServer());
        SettlementSavedData.get(event.getServer());
        KnowledgeSavedData.get(event.getServer());
        CivilisationServices.economy(event.getServer());
        CivilisationServices.construction(event.getServer());
        CivilisationServices.buildingDemand(event.getServer());
        CivilisationServices.demography(event.getServer()).reconcileNow();
        CivilisationValidation.Result result = CivilisationValidation.validate(event.getServer());
        if (result.valid()) {
            CivilisationMod.LOGGER.info("Civilisation integrity validation passed: records={}, active={}, duplicates={}, broken={}, invalidAssignments={}, settlements={}, membershipErrors={}, storageErrors={}, appearanceErrors={}",
                    result.citizenCount(), result.activeBodyCount(), result.duplicateBodyCount(),
                    result.brokenReferenceCount(), result.invalidAssignmentCount(), result.settlementCount(),
                    result.membershipErrorCount(), result.storageErrorCount(), result.appearanceErrorCount());
        } else {
            CivilisationMod.LOGGER.error("Civilisation integrity validation failed: {}", result.problems());
        }
    }

    @SubscribeEvent
    public void serverTick(ServerTickEvent.Post event) {
        CivilisationServices.economy(event.getServer()).tick();
        CivilisationServices.construction(event.getServer()).tick();
        CivilisationServices.buildingDemand(event.getServer()).tick();
        CivilisationServices.demography(event.getServer()).tick();
    }

    @SubscribeEvent
    public void serverStopped(ServerStoppedEvent event) {
        CivilisationServices.remove(event.getServer());
    }
}
