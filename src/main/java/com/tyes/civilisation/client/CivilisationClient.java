package com.tyes.civilisation.client;

import com.tyes.civilisation.CivilisationMod;
import com.tyes.civilisation.client.dashboard.ClientDashboardReceiver;
import com.tyes.civilisation.client.render.CivilianRenderer;
import com.tyes.civilisation.entity.ModEntities;
import com.tyes.civilisation.network.DashboardClientHooks;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

@EventBusSubscriber(modid = CivilisationMod.MOD_ID, value = Dist.CLIENT)
public final class CivilisationClient {
    private CivilisationClient() {
    }

    @SubscribeEvent
    public static void clientSetup(FMLClientSetupEvent event) {
        DashboardClientHooks.install(ClientDashboardReceiver::receive, ClientDashboardReceiver::receiveRecipeCatalog);
        EntityRenderers.register(ModEntities.CIVILIAN.get(), CivilianRenderer::new);
    }
}
