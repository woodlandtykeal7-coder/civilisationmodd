package com.tyes.civilisation.block;

import com.tyes.civilisation.CivilisationMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, CivilisationMod.MOD_ID);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BuilderHutFloorBlockEntity>> BUILDER_HUT_FLOOR =
            BLOCK_ENTITY_TYPES.register("builder_hut_floor",
                    () -> BlockEntityType.Builder.of(BuilderHutFloorBlockEntity::new,
                            ModBlocks.BUILDER_HUT_FLOOR.get()).build(null));
    private ModBlockEntities() {}
}
