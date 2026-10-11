package com.tyes.civilisation.construction.blueprint;
import net.minecraft.resources.ResourceLocation;
public record PlannedBlock(RelativeBlockPos relativePos,ResourceLocation blockId,boolean structural){}
