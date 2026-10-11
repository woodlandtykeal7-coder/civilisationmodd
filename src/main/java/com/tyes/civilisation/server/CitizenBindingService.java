package com.tyes.civilisation.server;

import com.tyes.civilisation.CivilisationMod;
import com.tyes.civilisation.citizen.CitizenRecord;
import com.tyes.civilisation.citizen.RuntimePresence;
import com.tyes.civilisation.entity.CivilianEntity;
import com.tyes.civilisation.entity.ModEntities;
import com.tyes.civilisation.persistence.CitizenSavedData;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

/** The only service allowed to join a persistent person to a loaded body. */
public final class CitizenBindingService {
    private CitizenBindingService() {
    }

    public static Optional<CivilianEntity> materialize(ServerLevel level, CitizenRecord record,
                                                        double x, double y, double z) {
        if (record.presence() == RuntimePresence.DECEASED) {
            CivilisationMod.LOGGER.warn("Refusing to materialise deceased citizen {}", record.citizenId());
            return Optional.empty();
        }
        if (CivilisationServices.activeCitizens(level.getServer()).bodyFor(record.citizenId()).isPresent()) {
            CivilisationMod.LOGGER.warn("Refusing to create a second body for active citizen {}", record.citizenId());
            return Optional.empty();
        }

        CivilianEntity entity = ModEntities.CIVILIAN.get().create(level);
        if (entity == null) {
            CivilisationMod.LOGGER.error("EntityType failed to create a body for citizen {}", record.citizenId());
            return Optional.empty();
        }
        entity.assignCitizenId(record.citizenId());
        entity.setAppearance(record.appearance());
        entity.setCustomName(Component.literal(record.displayName()));
        entity.setCustomNameVisible(true);
        entity.moveTo(x, y, z, 0.0F, 0.0F);
        if (!level.addFreshEntity(entity)) {
            CivilisationMod.LOGGER.error("World rejected body {} for citizen {}", entity.getUUID(), record.citizenId());
            return Optional.empty();
        }
        return Optional.of(entity);
    }

    public static boolean bindLoadedBody(ServerLevel level, CivilianEntity entity) {
        UUID entityId = entity.getUUID();
        Optional<UUID> citizenId = entity.citizenId();
        if (citizenId.isEmpty()) {
            CitizenSavedData data = CitizenSavedData.get(level.getServer());
            CitizenRecord created = data.registry().create();
            entity.assignCitizenId(created.citizenId());
            entity.setCustomName(Component.literal(created.displayName()));
            entity.setCustomNameVisible(true);
            citizenId = Optional.of(created.citizenId());
            data.setDirty();
            CivilisationMod.LOGGER.info("Registered newly spawned civilian body {} as {} ({})",
                    entityId, created.readableId(), created.citizenId());
        }

        CitizenSavedData data = CitizenSavedData.get(level.getServer());
        Optional<CitizenRecord> record = data.registry().find(citizenId.get());
        if (record.isEmpty()) {
            CivilisationMod.LOGGER.error("Rejecting orphan civilian body {}: citizen {} does not exist",
                    entityId, citizenId.get());
            return false;
        }
        if (record.get().presence() == RuntimePresence.DECEASED) {
            CivilisationMod.LOGGER.error("Rejecting body {} for deceased citizen {}", entityId, citizenId.get());
            return false;
        }

        entity.setAppearance(record.get().appearance());

        ActiveCitizenRegistry.ClaimResult result = CivilisationServices.activeCitizens(level.getServer())
                .claim(citizenId.get(), entityId);
        if (result == ActiveCitizenRegistry.ClaimResult.DUPLICATE_BODY) {
            UUID accepted = CivilisationServices.activeCitizens(level.getServer()).bodyFor(citizenId.get()).orElseThrow();
            CivilisationMod.LOGGER.error("Rejecting duplicate body {} for citizen {}; active body is {}",
                    entityId, citizenId.get(), accepted);
            return false;
        }

        record.get().updatePresence(RuntimePresence.MATERIALIZED, level, entity.position());
        data.setDirty();
        CivilisationMod.LOGGER.debug("Bound citizen {} to body {}", citizenId.get(), entityId);
        return true;
    }

    public static void unbind(ServerLevel level, CivilianEntity entity) {
        entity.citizenId().ifPresent(citizenId -> {
            ActiveCitizenRegistry active = CivilisationServices.activeCitizens(level.getServer());
            if (!active.release(citizenId, entity.getUUID())) return;

            CitizenSavedData data = CitizenSavedData.get(level.getServer());
            data.registry().find(citizenId).ifPresent(record -> {
                if (record.presence() != RuntimePresence.DECEASED) {
                    record.updatePresence(RuntimePresence.CHUNK_SERIALIZED, level, entity.position());
                    data.setDirty();
                }
            });
            CivilisationMod.LOGGER.debug("Unbound citizen {} from body {}", citizenId, entity.getUUID());
        });
    }

    public static void onDeath(ServerLevel level, CivilianEntity entity) {
        entity.citizenId().ifPresent(citizenId -> {
            CitizenSavedData data = CitizenSavedData.get(level.getServer());
            data.registry().find(citizenId).ifPresent(record -> {
                CivilisationServices.construction(level.getServer()).onDeath(record, level);
                CivilisationServices.economy(level.getServer()).onCitizenDeath(record);
                record.inventory().dropAll(level, entity.position());
                record.markDeceased(level, entity.position());
                CivilisationServices.demography(level.getServer()).onDeath(record);
                data.setDirty();
                CivilisationMod.LOGGER.info("Citizen {} died; its persistent record is retained", citizenId);
            });
            CivilisationServices.activeCitizens(level.getServer()).release(citizenId, entity.getUUID());
        });
    }
}
