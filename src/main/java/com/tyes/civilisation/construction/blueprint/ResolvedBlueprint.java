package com.tyes.civilisation.construction.blueprint;
import com.tyes.civilisation.construction.style.ArchitectureStyleId;
import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
public record ResolvedBlueprint(UUID blueprintInstanceId,ResourceLocation buildingType,ArchitectureStyleId style,int generatorVersion,List<PlannedBlock> blocks){
 public ResolvedBlueprint{blocks=List.copyOf(blocks);}
 public CompoundTag save(){CompoundTag t=new CompoundTag();t.putUUID("id",blueprintInstanceId);t.putString("type",buildingType.toString());t.putString("style",style.value().toString());t.putInt("version",generatorVersion);ListTag l=new ListTag();for(var b:blocks){CompoundTag e=new CompoundTag();e.putInt("x",b.relativePos().x());e.putInt("y",b.relativePos().y());e.putInt("z",b.relativePos().z());e.putString("block",b.blockId().toString());e.putBoolean("structural",b.structural());l.add(e);}t.put("blocks",l);return t;}
 public static ResolvedBlueprint load(CompoundTag t){List<PlannedBlock> b=new ArrayList<>();ListTag l=t.getList("blocks",Tag.TAG_COMPOUND);for(int i=0;i<l.size();i++){CompoundTag e=l.getCompound(i);b.add(new PlannedBlock(new RelativeBlockPos(e.getInt("x"),e.getInt("y"),e.getInt("z")),ResourceLocation.parse(e.getString("block")),e.getBoolean("structural")));}return new ResolvedBlueprint(t.getUUID("id"),ResourceLocation.parse(t.getString("type")),new ArchitectureStyleId(ResourceLocation.parse(t.getString("style"))),t.getInt("version"),b);}
}
