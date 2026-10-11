package com.tyes.civilisation.economy.inventory;

import com.tyes.civilisation.citizen.AssignedLocation;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Persistent reference only; live block entities are resolved and validated per operation. */
public final class InventoryEndpoint {
    private final InventoryEndpointId id;
    private final UUID settlementId;
    private final AssignedLocation location;
    private final InventoryEndpointType type;
    private final InventoryOwnership ownership;
    private long revision;

    public InventoryEndpoint(InventoryEndpointId id, UUID settlementId, AssignedLocation location,
                             InventoryEndpointType type, InventoryOwnership ownership) {
        this.id = Objects.requireNonNull(id);
        this.settlementId = Objects.requireNonNull(settlementId);
        this.location = Objects.requireNonNull(location);
        this.type = Objects.requireNonNull(type);
        this.ownership = Objects.requireNonNull(ownership);
    }

    public InventoryEndpointId id() { return id; }
    public UUID settlementId() { return settlementId; }
    public AssignedLocation location() { return location; }
    public InventoryEndpointType type() { return type; }
    public InventoryOwnership ownership() { return ownership; }
    public long revision() { return revision; }
    public void touch() { revision++; }

    public Optional<Container> resolve(ServerLevel level) {
        if (!ownership.permitsAutomaticUse() || !location.isIn(level) || !level.isLoaded(location.pos())) {
            return Optional.empty();
        }
        BlockEntity blockEntity = level.getBlockEntity(location.pos());
        return blockEntity instanceof Container container ? Optional.of(container) : Optional.empty();
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("id", id.value());
        tag.putUUID("settlementId", settlementId);
        tag.put("location", location.save());
        tag.putString("type", type.name());
        tag.putString("ownership", ownership.name());
        tag.putLong("revision", revision);
        return tag;
    }

    public static InventoryEndpoint load(CompoundTag tag) {
        InventoryEndpoint endpoint = new InventoryEndpoint(new InventoryEndpointId(tag.getUUID("id")),
                tag.getUUID("settlementId"), AssignedLocation.load(tag.getCompound("location")).orElseThrow(),
                InventoryEndpointType.valueOf(tag.getString("type")),
                InventoryOwnership.valueOf(tag.getString("ownership")));
        endpoint.revision = tag.getLong("revision");
        return endpoint;
    }
}
