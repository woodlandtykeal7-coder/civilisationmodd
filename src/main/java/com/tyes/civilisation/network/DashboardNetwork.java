package com.tyes.civilisation.network;

import com.tyes.civilisation.CivilisationMod;
import com.tyes.civilisation.citizen.CitizenRecord;
import com.tyes.civilisation.construction.building.BuildingRecord;
import com.tyes.civilisation.dashboard.DashboardSnapshotFactory;
import com.tyes.civilisation.dashboard.DashboardView;
import com.tyes.civilisation.entity.CivilianEntity;
import com.tyes.civilisation.persistence.CitizenSavedData;
import com.tyes.civilisation.persistence.ConstructionSavedData;
import com.tyes.civilisation.persistence.SettlementSavedData;
import com.tyes.civilisation.server.CivilisationServices;
import com.tyes.civilisation.settlement.SettlementState;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;

/** Compact request/snapshot channel. All requests are revalidated against server state and proximity. */
public final class DashboardNetwork {
    private static final double CITIZEN_RANGE_SQR = 16.0D * 16.0D;
    private static final double SETTLEMENT_RANGE_SQR = 64.0D * 64.0D;
    private DashboardNetwork() {}

    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("1");
        registrar.playToClient(DashboardSnapshotPayload.TYPE, DashboardSnapshotPayload.STREAM_CODEC,
                (payload, context) -> DashboardClientHooks.receiveDashboard(payload.data()));
        registrar.playToServer(DashboardRequestPayload.TYPE, DashboardRequestPayload.STREAM_CODEC,
                (payload, context) -> {
                    if (context.player() instanceof ServerPlayer player) handleRequest(player, payload);
                });
        registrar.playToClient(RecipeCatalogPayload.TYPE, RecipeCatalogPayload.STREAM_CODEC,
                (payload, context) -> DashboardClientHooks.receiveRecipes(payload.data()));
        registrar.playToServer(RecipeCatalogRequestPayload.TYPE, RecipeCatalogRequestPayload.STREAM_CODEC,
                (payload, context) -> { if (context.player() instanceof ServerPlayer player) sendRecipeCatalog(player, payload.citizenId()); });
        registrar.playToServer(DemonstrateRecipePayload.TYPE, DemonstrateRecipePayload.STREAM_CODEC,
                (payload, context) -> { if (context.player() instanceof ServerPlayer player) demonstrate(player, payload); });
    }

    public static void openCitizen(ServerPlayer player, CitizenRecord citizen) {
        send(player, DashboardSnapshotFactory.citizen(player.server, citizen));
    }

    private static void handleRequest(ServerPlayer player, DashboardRequestPayload request) {
        MinecraftServer server = player.server;
        switch (request.view()) {
            case CITIZEN -> CitizenSavedData.get(server).registry().find(request.target()).ifPresent(citizen -> {
                CivilianEntity body = body(server, citizen.citizenId());
                if (body != null && body.level() == player.level() && player.distanceToSqr(body) <= CITIZEN_RANGE_SQR)
                    send(player, DashboardSnapshotFactory.citizen(server, citizen));
            });
            case SETTLEMENT -> SettlementSavedData.get(server).registry().find(request.target()).ifPresent(settlement -> {
                if (near(player, settlement)) send(player, DashboardSnapshotFactory.settlement(server, settlement));
            });
            case BUILDING -> ConstructionSavedData.get(server).buildings().stream()
                    .filter(building -> building.buildingId().equals(request.target())).findFirst().ifPresent(building -> {
                        SettlementState settlement = SettlementSavedData.get(server).registry().find(building.settlementId()).orElse(null);
                        if (settlement != null && near(player, settlement))
                            send(player, DashboardSnapshotFactory.building(server, building));
                    });
        }
    }

    private static boolean near(ServerPlayer player, SettlementState settlement) {
        return settlement.centre().isIn(player.serverLevel())
                && player.position().distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(settlement.centre().pos()))
                <= SETTLEMENT_RANGE_SQR;
    }

    private static CivilianEntity body(MinecraftServer server, UUID citizenId) {
        UUID entityId = CivilisationServices.activeCitizens(server).bodyFor(citizenId).orElse(null);
        if (entityId == null) return null;
        for (ServerLevel level : server.getAllLevels()) if (level.getEntity(entityId) instanceof CivilianEntity body) return body;
        return null;
    }

    private static void send(ServerPlayer player, net.minecraft.nbt.CompoundTag snapshot) {
        PacketDistributor.sendToPlayer(player, new DashboardSnapshotPayload(snapshot));
    }

    private static void sendRecipeCatalog(ServerPlayer player, UUID citizenId) {
        CitizenRecord citizen = CitizenSavedData.get(player.server).registry().find(citizenId).orElse(null);
        CivilianEntity body = citizen == null ? null : body(player.server, citizenId);
        if (body == null || body.level() != player.level() || player.distanceToSqr(body) > CITIZEN_RANGE_SQR) return;
        CompoundTag data = new CompoundTag(); data.putUUID("citizen", citizenId); ListTag entries = new ListTag();
        player.server.getRecipeManager().getRecipes().stream().filter(DashboardNetwork::supportedRecipe)
                .sorted(java.util.Comparator.comparing(r -> r.id().toString())).limit(256).forEach(holder -> {
                    ItemStack output = holder.value().getResultItem(player.server.registryAccess());
                    if (output.isEmpty()) return;
                    CompoundTag row = new CompoundTag(); row.putString("id", holder.id().toString());
                    row.putString("category", recipeCategory(holder));
                    row.put("output", output.save(player.server.registryAccess()));
                    row.putString("level", citizen.knowledge().recipeLevel(holder.id()).name());
                    row.putInt("points", citizen.knowledge().recipePoints(holder.id()));
                    ListTag ingredients = new ListTag();
                    for (Ingredient ingredient : holder.value().getIngredients()) {
                        if (ingredient.isEmpty()) continue;
                        ItemStack[] options = ingredient.getItems();
                        if (options.length > 0) ingredients.add(options[0].save(player.server.registryAccess()));
                    }
                    row.put("ingredients", ingredients); entries.add(row);
                });
        data.put("recipes", entries); PacketDistributor.sendToPlayer(player, new RecipeCatalogPayload(data));
    }

    private static void demonstrate(ServerPlayer player, DemonstrateRecipePayload request) {
        CitizenRecord citizen = CitizenSavedData.get(player.server).registry().find(request.citizenId()).orElse(null);
        CivilianEntity body = citizen == null ? null : body(player.server, request.citizenId());
        RecipeHolder<?> recipe = player.server.getRecipeManager().byKey(request.recipeId()).orElse(null);
        if (body == null || body.level() != player.level() || player.distanceToSqr(body) > 64.0D
                || recipe == null || !supportedRecipe(recipe) || recipe.value().isSpecial()) {
            player.displayClientMessage(Component.literal("That demonstration is not valid here."), false); return;
        }
        if (!hasNearbyWorkstation(player.serverLevel(), body.blockPosition(), recipe)) {
            player.displayClientMessage(Component.literal("Move the citizen and yourself near the required workstation."), false); return;
        }
        int[] remaining = new int[player.getInventory().getContainerSize()];
        for (int i = 0; i < remaining.length; i++) remaining[i] = player.getInventory().getItem(i).getCount();
        java.util.List<Integer> consumedSlots = new java.util.ArrayList<>();
        for (Ingredient ingredient : recipe.value().getIngredients()) {
            if (ingredient.isEmpty()) continue;
            int selected = -1;
            for (int slot = 0; slot < remaining.length; slot++)
                if (remaining[slot] > 0 && ingredient.test(player.getInventory().getItem(slot))) { selected = slot; break; }
            if (selected < 0) { player.displayClientMessage(Component.literal("Missing real ingredients for that demonstration."), false); return; }
            remaining[selected]--; consumedSlots.add(selected);
        }
        ItemStack output = recipe.value().getResultItem(player.server.registryAccess()).copy();
        if (output.isEmpty()) return;
        consumedSlots.forEach(slot -> player.getInventory().getItem(slot).shrink(1));
        if (!player.getInventory().add(output)) player.drop(output, false);
        player.inventoryMenu.broadcastChanges();
        CivilisationServices.knowledge(player.server).demonstrateRecipe(citizen, request.recipeId());
        player.displayClientMessage(Component.literal(citizen.displayName() + " observed the physical demonstration."), false);
        send(player, DashboardSnapshotFactory.citizen(player.server, citizen));
    }

    private static boolean supportedRecipe(RecipeHolder<?> holder) {
        RecipeType<?> type = holder.value().getType();
        return !holder.value().isSpecial() && (type == RecipeType.CRAFTING || type == RecipeType.SMELTING
                || type == RecipeType.BLASTING || type == RecipeType.SMOKING);
    }

    private static String recipeCategory(RecipeHolder<?> holder) {
        RecipeType<?> type = holder.value().getType();
        if (type == RecipeType.SMELTING) return "Smelting";
        if (type == RecipeType.BLASTING) return "Blasting";
        if (type == RecipeType.SMOKING) return "Smoking";
        return "Crafting";
    }

    private static boolean hasNearbyWorkstation(ServerLevel level, BlockPos centre, RecipeHolder<?> recipe) {
        boolean crafting = recipe.value().getType() == RecipeType.CRAFTING;
        for (BlockPos pos : BlockPos.betweenClosed(centre.offset(-4, -2, -4), centre.offset(4, 2, 4))) {
            if (crafting && level.getBlockState(pos).is(Blocks.CRAFTING_TABLE)) return true;
            if (!crafting && level.getBlockEntity(pos) instanceof AbstractFurnaceBlockEntity) return true;
        }
        return false;
    }
}
