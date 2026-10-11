package com.tyes.civilisation.construction.material;
import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
public final class MaterialPalette {
 private final EnumMap<MaterialRole,ResourceLocation> values=new EnumMap<>(MaterialRole.class);
 public MaterialPalette(Map<MaterialRole,ResourceLocation> values){this.values.putAll(values);}
 public ResourceLocation require(MaterialRole role){var v=values.get(role);if(v==null)throw new IllegalStateException("Missing material role "+role);return v;}
 public Map<MaterialRole,ResourceLocation> asMap(){return Map.copyOf(values);}
 public CompoundTag save(){CompoundTag t=new CompoundTag();values.forEach((r,v)->t.putString(r.name(),v.toString()));return t;}
 public static MaterialPalette load(CompoundTag t){Map<MaterialRole,ResourceLocation> m=new EnumMap<>(MaterialRole.class);for(String k:t.getAllKeys())m.put(MaterialRole.valueOf(k),ResourceLocation.parse(t.getString(k)));return new MaterialPalette(m);}
}
