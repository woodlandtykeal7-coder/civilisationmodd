package com.tyes.civilisation.persistence;

import com.tyes.civilisation.CivilisationMod;
import com.tyes.civilisation.construction.building.BuildingRecord;
import com.tyes.civilisation.construction.demand.BuildingDemand;
import com.tyes.civilisation.construction.project.ConstructionProject;
import java.util.*;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

public final class ConstructionSavedData extends SavedData {
    public static final int CURRENT_SCHEMA_VERSION=5;
    private final Map<UUID,ConstructionProject> projects=new LinkedHashMap<>();
    private final Map<UUID,BuildingRecord> buildings=new LinkedHashMap<>();
    private final Map<UUID,BuildingDemand> demands=new LinkedHashMap<>();
    private int next=1;

    public static ConstructionSavedData get(MinecraftServer server){return server.overworld().getDataStorage().computeIfAbsent(new Factory<>(ConstructionSavedData::new,ConstructionSavedData::load),CivilisationMod.MOD_ID+"_construction");}
    public Collection<ConstructionProject> projects(){return List.copyOf(projects.values());}
    public Collection<BuildingRecord> buildings(){return List.copyOf(buildings.values());}
    public Optional<ConstructionProject> project(UUID id){return Optional.ofNullable(projects.get(id));}
    public void add(ConstructionProject project){projects.put(project.projectId(),project);setDirty();}
    public String nextId(){setDirty();return String.format("P-%04d",next++);}
    public void addBuilding(BuildingRecord building){buildings.put(building.buildingId(),building);setDirty();}
    public Optional<BuildingDemand> demand(UUID settlementId){return Optional.ofNullable(demands.get(settlementId));}
    public Collection<BuildingDemand> demands(){return List.copyOf(demands.values());}
    public void putDemand(BuildingDemand demand){demands.put(demand.settlementId(),demand);setDirty();}
    public void clearDemand(UUID settlementId){demands.remove(settlementId);setDirty();}

    @Override public CompoundTag save(CompoundTag tag,HolderLookup.Provider registries){
        tag.putInt("schema",CURRENT_SCHEMA_VERSION);tag.putInt("next",next);
        ListTag savedProjects=new ListTag();projects.values().forEach(project->savedProjects.add(project.save()));tag.put("projects",savedProjects);
        ListTag savedBuildings=new ListTag();buildings.values().forEach(building->savedBuildings.add(building.save()));tag.put("buildings",savedBuildings);
        ListTag savedDemands=new ListTag();demands.values().forEach(demand->savedDemands.add(demand.save()));tag.put("demands",savedDemands);
        return tag;
    }

    private static ConstructionSavedData load(CompoundTag tag,HolderLookup.Provider registries){
        if(tag.getInt("schema")>CURRENT_SCHEMA_VERSION)throw new IllegalStateException("Unsupported construction schema");
        var data=new ConstructionSavedData();data.next=Math.max(1,tag.getInt("next"));
        ListTag savedProjects=tag.getList("projects",Tag.TAG_COMPOUND);for(int i=0;i<savedProjects.size();i++){var project=ConstructionProject.load(savedProjects.getCompound(i));data.projects.put(project.projectId(),project);}
        ListTag savedBuildings=tag.getList("buildings",Tag.TAG_COMPOUND);for(int i=0;i<savedBuildings.size();i++){var building=BuildingRecord.load(savedBuildings.getCompound(i));data.buildings.put(building.buildingId(),building);}
        ListTag savedDemands=tag.getList("demands",Tag.TAG_COMPOUND);for(int i=0;i<savedDemands.size();i++){var demand=BuildingDemand.load(savedDemands.getCompound(i));data.demands.put(demand.settlementId(),demand);}
        return data;
    }
}
