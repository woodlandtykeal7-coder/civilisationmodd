package com.tyes.civilisation.construction.building;

import com.tyes.civilisation.citizen.AssignedLocation;
import com.tyes.civilisation.construction.material.MaterialPalette;
import com.tyes.civilisation.construction.project.BuildingCondition;
import com.tyes.civilisation.construction.style.ArchitectureStyleId;
import java.util.List;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

public record BuildingRecord(UUID buildingId, UUID projectId, UUID settlementId, AssignedLocation anchor,
                             ResourceLocation type, ArchitectureStyleId style, MaterialPalette palette,
                             int capacity, BuildingCondition condition, List<UUID> occupants,
                             long constructionTick) {
    public BuildingRecord { occupants = List.copyOf(occupants); }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag(); tag.putUUID("id", buildingId); tag.putUUID("project", projectId);
        tag.putUUID("settlement", settlementId); tag.put("anchor", anchor.save()); tag.putString("type", type.toString());
        tag.putString("style", style.value().toString()); tag.put("palette", palette.save()); tag.putInt("capacity", capacity);
        tag.putString("condition", condition.name()); tag.putLong("constructionTick", constructionTick); return tag;
    }

    public static BuildingRecord load(CompoundTag tag) {
        return new BuildingRecord(tag.getUUID("id"), tag.getUUID("project"), tag.getUUID("settlement"),
                AssignedLocation.load(tag.getCompound("anchor")).orElseThrow(), ResourceLocation.parse(tag.getString("type")),
                new ArchitectureStyleId(ResourceLocation.parse(tag.getString("style"))),
                MaterialPalette.load(tag.getCompound("palette")), tag.getInt("capacity"),
                BuildingCondition.valueOf(tag.getString("condition")), List.of(), tag.getLong("constructionTick"));
    }
}
