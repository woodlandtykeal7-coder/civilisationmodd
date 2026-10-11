package com.tyes.civilisation.network;

import java.util.function.Consumer;
import net.minecraft.nbt.CompoundTag;

/**
 * Side-neutral bridge for client-bound dashboard payloads. The common network
 * registrar never links against Minecraft client classes; the client entry
 * point installs these callbacks when it is present.
 */
public final class DashboardClientHooks {
    private static Consumer<CompoundTag> dashboard = ignored -> {};
    private static Consumer<CompoundTag> recipes = ignored -> {};

    private DashboardClientHooks() {}

    public static void install(Consumer<CompoundTag> dashboardHandler, Consumer<CompoundTag> recipeHandler) {
        dashboard = dashboardHandler;
        recipes = recipeHandler;
    }

    public static void receiveDashboard(CompoundTag snapshot) {
        dashboard.accept(snapshot.copy());
    }

    public static void receiveRecipes(CompoundTag snapshot) {
        recipes.accept(snapshot.copy());
    }
}
