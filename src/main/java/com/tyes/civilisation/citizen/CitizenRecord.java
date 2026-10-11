package com.tyes.civilisation.citizen;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import com.tyes.civilisation.settlement.WorkRole;
import javax.annotation.Nullable;
import com.tyes.civilisation.citizen.identity.CitizenAppearanceProfile;
import com.tyes.civilisation.citizen.identity.CitizenAppearanceService;
import com.tyes.civilisation.society.CitizenDevelopment;
import com.tyes.civilisation.knowledge.CitizenKnowledge;
import com.tyes.civilisation.history.CitizenHistory;

/** The authoritative, persistent identity of one person. */
public final class CitizenRecord {
    private static final String DEFAULT_DIMENSION = "minecraft:overworld";

    private final UUID citizenId;
    private final String readableId;
    private final String displayName;
    private final long appearanceSeed;
    private final CitizenAppearanceProfile appearance;
    private RuntimePresence presence;
    private ResourceLocation lastDimension;
    private Vec3 lastPosition;
    private final CitizenNeeds needs;
    private final CitizenAssignments assignments;
    private final CitizenInventory inventory;
    private final BrainPersistence brainPersistence;
    @Nullable private UUID settlementId;
    private WorkRole workRole;
    private final CitizenDevelopment development;
    private final CitizenKnowledge knowledge;
    private final CitizenHistory history;

    public CitizenRecord(UUID citizenId, String readableId, String displayName, long appearanceSeed) {
        this(citizenId, readableId, displayName, appearanceSeed,
                CitizenAppearanceService.generate(citizenId, appearanceSeed), RuntimePresence.PENDING_MATERIALIZATION,
                ResourceLocation.parse(DEFAULT_DIMENSION), Vec3.ZERO, new CitizenNeeds(),
                new CitizenAssignments(), new CitizenInventory(), new BrainPersistence(), null,
                WorkRole.GENERAL_LABOURER, new CitizenDevelopment());
    }

    private CitizenRecord(UUID citizenId, String readableId, String displayName, long appearanceSeed,
                          CitizenAppearanceProfile appearance, RuntimePresence presence,
                          ResourceLocation lastDimension, Vec3 lastPosition, CitizenNeeds needs,
                          CitizenAssignments assignments, CitizenInventory inventory,
                          BrainPersistence brainPersistence, @Nullable UUID settlementId, WorkRole workRole) {
        this(citizenId, readableId, displayName, appearanceSeed, appearance, presence, lastDimension,
                lastPosition, needs, assignments, inventory, brainPersistence, settlementId, workRole,
                new CitizenDevelopment(), new CitizenKnowledge(), new CitizenHistory());
    }

    private CitizenRecord(UUID citizenId, String readableId, String displayName, long appearanceSeed,
                          CitizenAppearanceProfile appearance, RuntimePresence presence,
                          ResourceLocation lastDimension, Vec3 lastPosition, CitizenNeeds needs,
                          CitizenAssignments assignments, CitizenInventory inventory,
                          BrainPersistence brainPersistence, @Nullable UUID settlementId, WorkRole workRole,
                          CitizenDevelopment development) {
        this(citizenId, readableId, displayName, appearanceSeed, appearance, presence, lastDimension,
                lastPosition, needs, assignments, inventory, brainPersistence, settlementId, workRole,
                development, new CitizenKnowledge(), new CitizenHistory());
    }

    private CitizenRecord(UUID citizenId, String readableId, String displayName, long appearanceSeed,
                          CitizenAppearanceProfile appearance, RuntimePresence presence,
                          ResourceLocation lastDimension, Vec3 lastPosition, CitizenNeeds needs,
                          CitizenAssignments assignments, CitizenInventory inventory,
                          BrainPersistence brainPersistence, @Nullable UUID settlementId, WorkRole workRole,
                          CitizenDevelopment development, CitizenKnowledge knowledge, CitizenHistory history) {
        this.citizenId = Objects.requireNonNull(citizenId);
        this.readableId = Objects.requireNonNull(readableId);
        this.displayName = Objects.requireNonNull(displayName);
        this.appearanceSeed = appearanceSeed;
        this.appearance = Objects.requireNonNull(appearance);
        this.presence = Objects.requireNonNull(presence);
        this.lastDimension = Objects.requireNonNull(lastDimension);
        this.lastPosition = Objects.requireNonNull(lastPosition);
        this.needs = Objects.requireNonNull(needs);
        this.assignments = Objects.requireNonNull(assignments);
        this.inventory = Objects.requireNonNull(inventory);
        this.brainPersistence = Objects.requireNonNull(brainPersistence);
        this.settlementId = settlementId;
        this.workRole = Objects.requireNonNull(workRole);
        this.development = Objects.requireNonNull(development);
        this.knowledge = Objects.requireNonNull(knowledge);
        this.history = Objects.requireNonNull(history);
    }

    public UUID citizenId() { return citizenId; }
    public String readableId() { return readableId; }
    public String displayName() { return displayName; }
    public long appearanceSeed() { return appearanceSeed; }
    public CitizenAppearanceProfile appearance() { return appearance; }
    public RuntimePresence presence() { return presence; }
    public ResourceLocation lastDimension() { return lastDimension; }
    public Vec3 lastPosition() { return lastPosition; }
    public CitizenNeeds needs() { return needs; }
    public CitizenAssignments assignments() { return assignments; }
    public CitizenInventory inventory() { return inventory; }
    public BrainPersistence brainPersistence() { return brainPersistence; }
    public Optional<UUID> settlementId() { return Optional.ofNullable(settlementId); }
    public WorkRole workRole() { return workRole; }
    public CitizenDevelopment development() { return development; }
    public CitizenKnowledge knowledge() { return knowledge; }
    public CitizenHistory history() { return history; }
    public void setSettlementReference(@Nullable UUID settlementId) { this.settlementId = settlementId; }
    public void setWorkRole(WorkRole workRole) { this.workRole = Objects.requireNonNull(workRole); }

    public void updatePresence(RuntimePresence presence, ServerLevel level, Vec3 position) {
        if (this.presence == RuntimePresence.DECEASED && presence != RuntimePresence.DECEASED) {
            return;
        }
        this.presence = Objects.requireNonNull(presence);
        this.lastDimension = level.dimension().location();
        this.lastPosition = position;
    }

    public void markDeceased(ServerLevel level, Vec3 position) {
        updatePresence(RuntimePresence.DECEASED, level, position);
    }

    public CompoundTag save(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("citizenId", citizenId);
        tag.putString("readableId", readableId);
        tag.putString("displayName", displayName);
        tag.putLong("appearanceSeed", appearanceSeed);
        tag.put("appearance", appearance.save());
        tag.putString("presence", presence.name());
        tag.putString("lastDimension", lastDimension.toString());
        tag.putDouble("lastX", lastPosition.x);
        tag.putDouble("lastY", lastPosition.y);
        tag.putDouble("lastZ", lastPosition.z);
        tag.put("needs", needs.save());
        tag.put("assignments", assignments.save());
        tag.put("inventory", inventory.save(registries));
        tag.put("brain", brainPersistence.save());
        if (settlementId != null) tag.putUUID("settlementId", settlementId);
        tag.putString("workRole", workRole.name());
        tag.put("development", development.save());
        tag.put("knowledge", knowledge.save());
        tag.put("history", history.save());
        return tag;
    }

    public CompoundTag save() {
        return save(RegistryAccess.EMPTY);
    }

    public static CitizenRecord load(CompoundTag tag, HolderLookup.Provider registries, String migratedReadableId) {
        if (!tag.hasUUID("citizenId")) {
            throw new IllegalArgumentException("Citizen record is missing citizenId");
        }
        UUID id = tag.getUUID("citizenId");
        String name = tag.getString("displayName");
        if (name.isBlank()) {
            name = "Citizen " + id.toString().substring(0, 8);
        }
        ResourceLocation dimension = ResourceLocation.tryParse(tag.getString("lastDimension"));
        if (dimension == null) {
            dimension = ResourceLocation.parse(DEFAULT_DIMENSION);
        }
        String readableId = tag.getString("readableId");
        if (readableId.isBlank()) readableId = migratedReadableId;
        long appearanceSeed = tag.getLong("appearanceSeed");
        CitizenAppearanceProfile appearance = tag.contains("appearance", CompoundTag.TAG_COMPOUND)
                ? CitizenAppearanceProfile.load(tag.getCompound("appearance"))
                : CitizenAppearanceService.generate(id, appearanceSeed);
        return new CitizenRecord(id, readableId, name, appearanceSeed, appearance,
                RuntimePresence.parse(tag.getString("presence")), dimension,
                new Vec3(tag.getDouble("lastX"), tag.getDouble("lastY"), tag.getDouble("lastZ")),
                CitizenNeeds.load(tag.getCompound("needs")),
                CitizenAssignments.load(tag.getCompound("assignments")),
                CitizenInventory.load(tag.getCompound("inventory"), registries),
                BrainPersistence.load(tag.getCompound("brain")),
                tag.hasUUID("settlementId") ? tag.getUUID("settlementId") : null,
                WorkRole.parse(tag.getString("workRole")),
                tag.contains("development", CompoundTag.TAG_COMPOUND)
                        ? CitizenDevelopment.load(tag.getCompound("development"))
                        : new CitizenDevelopment(),
                tag.contains("knowledge", CompoundTag.TAG_COMPOUND)
                        ? CitizenKnowledge.load(tag.getCompound("knowledge")) : new CitizenKnowledge(),
                tag.contains("history", CompoundTag.TAG_COMPOUND)
                        ? CitizenHistory.load(tag.getCompound("history")) : new CitizenHistory());
    }

    public static CitizenRecord load(CompoundTag tag) {
        return load(tag, RegistryAccess.EMPTY, "C-0000");
    }
}
