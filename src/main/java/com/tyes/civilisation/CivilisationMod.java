package com.tyes.civilisation;

import com.mojang.logging.LogUtils;
import com.tyes.civilisation.entity.CivilianEntity;
import com.tyes.civilisation.entity.ModEntities;
import com.tyes.civilisation.server.CivilisationEvents;
import com.tyes.civilisation.item.ModItems;
import com.tyes.civilisation.network.DashboardNetwork;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import org.slf4j.Logger;

@Mod(CivilisationMod.MOD_ID)
public final class CivilisationMod {
    public static final String MOD_ID = "civilisation";
    public static final Logger LOGGER = LogUtils.getLogger();

    public CivilisationMod(IEventBus modEventBus) {
        ModEntities.ENTITY_TYPES.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);
        modEventBus.addListener(CivilisationMod::registerAttributes);
        modEventBus.addListener(CivilisationMod::addCreativeItems);
        modEventBus.addListener(DashboardNetwork::register);
        NeoForge.EVENT_BUS.register(new CivilisationEvents());
        LOGGER.info("Initialising Civilisation Phase 6 dashboards and personal knowledge");
    }

    private static void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(ModEntities.CIVILIAN.get(), CivilianEntity.createAttributes().build());
    }

    private static void addCreativeItems(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.SPAWN_EGGS) event.accept(ModItems.CIVILIAN_SPAWN_EGG.get());
    }
}
