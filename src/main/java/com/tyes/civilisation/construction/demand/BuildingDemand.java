package com.tyes.civilisation.construction.demand;

import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

/** Persistent settlement intent. It may create a project, but never places a block. */
public final class BuildingDemand {
    private final UUID settlementId;
    private final ResourceLocation buildingType;
    private final BuildingDemandReason reason;
    private final long createdTick;
    private DemandSeverity severity;
    private int desiredCapacity;
    private int currentCapacity;
    @Nullable private UUID activeProjectId;

    public BuildingDemand(UUID settlementId, ResourceLocation buildingType, BuildingDemandReason reason,
                          DemandSeverity severity, int desiredCapacity, int currentCapacity, long createdTick) {
        this.settlementId = settlementId;
        this.buildingType = buildingType;
        this.reason = reason;
        this.severity = severity;
        this.desiredCapacity = desiredCapacity;
        this.currentCapacity = currentCapacity;
        this.createdTick = createdTick;
    }

    public UUID settlementId() { return settlementId; }
    public ResourceLocation buildingType() { return buildingType; }
    public BuildingDemandReason reason() { return reason; }
    public DemandSeverity severity() { return severity; }
    public int desiredCapacity() { return desiredCapacity; }
    public int currentCapacity() { return currentCapacity; }
    public long createdTick() { return createdTick; }
    public Optional<UUID> activeProjectId() { return Optional.ofNullable(activeProjectId); }

    public void update(DemandSeverity severity, int desiredCapacity, int currentCapacity) {
        this.severity = severity;
        this.desiredCapacity = desiredCapacity;
        this.currentCapacity = currentCapacity;
    }

    public void attachProject(UUID projectId) { this.activeProjectId = projectId; }
    public void clearProject() { this.activeProjectId = null; }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("settlement", settlementId);
        tag.putString("buildingType", buildingType.toString());
        tag.putString("reason", reason.name());
        tag.putString("severity", severity.name());
        tag.putInt("desiredCapacity", desiredCapacity);
        tag.putInt("currentCapacity", currentCapacity);
        tag.putLong("createdTick", createdTick);
        if (activeProjectId != null) tag.putUUID("activeProject", activeProjectId);
        return tag;
    }

    public static BuildingDemand load(CompoundTag tag) {
        BuildingDemand demand = new BuildingDemand(tag.getUUID("settlement"),
                ResourceLocation.parse(tag.getString("buildingType")),
                BuildingDemandReason.valueOf(tag.getString("reason")),
                DemandSeverity.valueOf(tag.getString("severity")),
                tag.getInt("desiredCapacity"), tag.getInt("currentCapacity"), tag.getLong("createdTick"));
        if (tag.hasUUID("activeProject")) demand.activeProjectId = tag.getUUID("activeProject");
        return demand;
    }
}
