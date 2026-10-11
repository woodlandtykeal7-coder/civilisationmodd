package com.tyes.civilisation.construction.blueprint;
import net.minecraft.core.BlockPos;
public record RelativeBlockPos(int x,int y,int z){public BlockPos resolve(BlockPos anchor){return anchor.offset(x,y,z);}}
