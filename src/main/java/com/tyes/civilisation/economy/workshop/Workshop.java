package com.tyes.civilisation.economy.workshop;

import com.tyes.civilisation.citizen.AssignedLocation;
import com.tyes.civilisation.economy.inventory.InventoryEndpointId;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;

public final class Workshop {
    private final UUID id;
    private final UUID settlementId;
    private final WorkshopType type;
    private final AssignedLocation workstation;
    private final InventoryEndpointId input;
    private final InventoryEndpointId output;
    private UUID claimedBy;
    private long claimExpiry;

    public Workshop(UUID id, UUID settlementId, WorkshopType type, AssignedLocation workstation,
                    InventoryEndpointId input, InventoryEndpointId output) {
        this.id = id; this.settlementId = settlementId; this.type = type; this.workstation = workstation;
        this.input = input; this.output = output;
    }
    public UUID id() { return id; } public UUID settlementId() { return settlementId; }
    public WorkshopType type() { return type; } public AssignedLocation workstation() { return workstation; }
    public InventoryEndpointId input() { return input; } public InventoryEndpointId output() { return output; }
    public Optional<UUID> claimedBy() { return Optional.ofNullable(claimedBy); }
    public boolean claim(UUID citizenId, long now, long ttl) {
        if (claimedBy != null && claimExpiry > now && !claimedBy.equals(citizenId)) return false;
        claimedBy = citizenId; claimExpiry = now + ttl; return true;
    }
    public void release(UUID citizenId) { if (citizenId.equals(claimedBy)) { claimedBy = null; claimExpiry = 0; } }
    public void expire(long now) { if (claimedBy != null && claimExpiry <= now) { claimedBy = null; claimExpiry = 0; } }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag(); tag.putUUID("id", id); tag.putUUID("settlementId", settlementId);
        tag.putString("type", type.name()); tag.put("workstation", workstation.save());
        tag.putUUID("input", input.value()); tag.putUUID("output", output.value());
        if (claimedBy != null) tag.putUUID("claimedBy", claimedBy); tag.putLong("claimExpiry", claimExpiry); return tag;
    }
    public static Workshop load(CompoundTag tag) {
        Workshop workshop = new Workshop(tag.getUUID("id"), tag.getUUID("settlementId"),
                WorkshopType.valueOf(tag.getString("type")), AssignedLocation.load(tag.getCompound("workstation")).orElseThrow(),
                new InventoryEndpointId(tag.getUUID("input")), new InventoryEndpointId(tag.getUUID("output")));
        workshop.claimedBy = tag.hasUUID("claimedBy") ? tag.getUUID("claimedBy") : null;
        workshop.claimExpiry = tag.getLong("claimExpiry"); return workshop;
    }
}
