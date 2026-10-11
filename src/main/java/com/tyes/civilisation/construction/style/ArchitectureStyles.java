package com.tyes.civilisation.construction.style;
import com.tyes.civilisation.CivilisationMod;
import net.minecraft.resources.ResourceLocation;
public final class ArchitectureStyles {
 public static final ArchitectureStyleProfile TEMPERATE=new ArchitectureStyleProfile(id("temperate_timber"),.8,1,false,false,false,false,2);
 public static final ArchitectureStyleProfile DESERT=new ArchitectureStyleProfile(id("desert_masonry"),.1,1,false,false,true,true,2);
 public static final ArchitectureStyleProfile JUNGLE=new ArchitectureStyleProfile(id("jungle_elevated"),.7,1,true,true,false,false,2);
 private static ArchitectureStyleId id(String p){return new ArchitectureStyleId(ResourceLocation.fromNamespaceAndPath(CivilisationMod.MOD_ID,p));}
 private ArchitectureStyles(){}
}
