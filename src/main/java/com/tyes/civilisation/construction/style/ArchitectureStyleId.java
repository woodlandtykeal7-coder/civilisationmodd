package com.tyes.civilisation.construction.style;
import net.minecraft.resources.ResourceLocation;
public record ArchitectureStyleId(ResourceLocation value){public ArchitectureStyleId{if(value==null)throw new IllegalArgumentException("value");}}
