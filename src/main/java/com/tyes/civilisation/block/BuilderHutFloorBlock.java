package com.tyes.civilisation.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

/** Full-height, walkable floor marker: never a miniature building or instant build. */
public final class BuilderHutFloorBlock extends Block implements EntityBlock {
    public BuilderHutFloorBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BuilderHutFloorBlockEntity(pos, state);
    }
}
