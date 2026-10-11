package com.tyes.civilisation.entity;

import com.tyes.civilisation.CivilisationMod;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(BuiltInRegistries.ENTITY_TYPE, CivilisationMod.MOD_ID);

    public static final DeferredHolder<EntityType<?>, EntityType<CivilianEntity>> CIVILIAN =
            ENTITY_TYPES.register("civilian", () -> EntityType.Builder
                    .of(CivilianEntity::new, MobCategory.CREATURE)
                    .sized(0.6F, 1.95F)
                    .clientTrackingRange(10)
                    .updateInterval(3)
                    .build(CivilisationMod.MOD_ID + ":civilian"));

    private ModEntities() {
    }
}
