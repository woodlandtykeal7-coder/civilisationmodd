package com.tyes.civilisation.block;

import com.tyes.civilisation.CivilisationMod;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Real full-cube floor anchors which can eventually be placed by construction blueprints. */
public final class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(CivilisationMod.MOD_ID);
    public static final DeferredBlock<BuilderHutFloorBlock> BUILDER_HUT_FLOOR =
            BLOCKS.register("builder_hut_floor", () -> new BuilderHutFloorBlock(
                    BlockBehaviour.Properties.of().strength(2.0F).sound(SoundType.WOOD)));
    private ModBlocks() {}
}
