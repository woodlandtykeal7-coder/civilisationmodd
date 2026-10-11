package com.tyes.civilisation.client.dashboard;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;

/** Client-only packet endpoint kept out of common/server data classes. */
public final class ClientDashboardReceiver {
    private ClientDashboardReceiver() {}
    public static void receive(CompoundTag snapshot) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof CivilisationDashboardScreen dashboard
                && dashboard.accepts(snapshot)) dashboard.updateSnapshot(snapshot);
        else minecraft.setScreen(new CivilisationDashboardScreen(snapshot));
    }
    public static void receiveRecipeCatalog(CompoundTag snapshot) {
        Minecraft.getInstance().setScreen(new RecipeBrowserScreen(snapshot));
    }
}
