package com.tyes.civilisation.server;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.tyes.civilisation.citizen.AssignedLocation;
import com.tyes.civilisation.citizen.CitizenRecord;
import com.tyes.civilisation.entity.CivilianEntity;
import com.tyes.civilisation.persistence.CitizenSavedData;
import com.tyes.civilisation.persistence.SettlementSavedData;
import com.tyes.civilisation.settlement.SettlementState;
import com.tyes.civilisation.settlement.WorkRole;
import com.tyes.civilisation.economy.*;
import com.tyes.civilisation.economy.inventory.*;
import com.tyes.civilisation.economy.item.ItemKey;
import com.tyes.civilisation.economy.production.*;
import com.tyes.civilisation.economy.workshop.*;
import com.tyes.civilisation.society.*;
import java.util.LinkedHashMap;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.Container;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.phys.Vec3;

public final class CivilisationCommands {
    private CivilisationCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(root("civilisation"));
        dispatcher.register(root("civ"));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> root(String name) {
        return Commands.literal(name).requires(source -> source.hasPermission(2))
                .then(Commands.literal("spawn").executes(context -> spawn(context.getSource())))
                .then(Commands.literal("setup").executes(context -> setup(context.getSource())))
                .then(Commands.literal("list").executes(context -> list(context.getSource())))
                .then(Commands.literal("inspect").then(selectorArgument()
                        .executes(context -> inspect(context.getSource(), StringArgumentType.getString(context, "citizen")))))
                .then(Commands.literal("citizen")
                        .then(Commands.literal("spawn").executes(context -> spawn(context.getSource())))
                        .then(Commands.literal("setup").executes(context -> setup(context.getSource())))
                        .then(Commands.literal("list").executes(context -> list(context.getSource())))
                        .then(Commands.literal("inspect")
                                .then(selectorArgument()
                                        .executes(context -> inspect(context.getSource(),
                                                StringArgumentType.getString(context, "citizen"))))))
                .then(colonyCommands())
                .then(Commands.literal("debug")
                        .then(Commands.literal("reservations").then(settlementArgument().executes(c -> economyDebug(c.getSource(), StringArgumentType.getString(c, "settlement"), "reservations"))))
                        .then(Commands.literal("deliveries").then(settlementArgument().executes(c -> economyDebug(c.getSource(), StringArgumentType.getString(c, "settlement"), "deliveries"))))
                        .then(Commands.literal("workshops").then(settlementArgument().executes(c -> economyDebug(c.getSource(), StringArgumentType.getString(c, "settlement"), "workshops"))))
                        .then(Commands.literal("construction").then(settlementArgument().executes(c -> constructionList(c.getSource(), StringArgumentType.getString(c, "settlement")))))
                        .then(Commands.literal("building-demand").then(settlementArgument().executes(c -> buildingDemand(c.getSource(), StringArgumentType.getString(c, "settlement")))))
                        .then(Commands.literal("project").then(Commands.argument("project", StringArgumentType.string()).executes(c -> debugProject(c.getSource(), StringArgumentType.getString(c, "project")))))
                        .then(Commands.literal("demography").then(selectorArgument().executes(c -> debugDemography(c.getSource(), StringArgumentType.getString(c, "citizen")))))
                        .then(Commands.literal("knowledge").then(selectorArgument().executes(c -> debugKnowledge(c.getSource(), StringArgumentType.getString(c, "citizen")))))
                        .then(Commands.literal("create-child").then(settlementArgument().executes(c -> createTestChild(c.getSource(), StringArgumentType.getString(c, "settlement")))))
                        .then(Commands.literal("advance-life").then(selectorArgument().executes(c -> advanceLife(c.getSource(), StringArgumentType.getString(c, "citizen")))))
                        .then(Commands.literal("colony")
                                .then(Commands.literal("setup").executes(context -> setupSettlement(context.getSource()))))
                        .then(Commands.literal("citizen")
                                .then(selectorArgument()
                                        .executes(context -> debugCitizen(context.getSource(), StringArgumentType.getString(context, "citizen")))
                                        .then(Commands.literal("needs")
                                                .then(Commands.argument("hunger", DoubleArgumentType.doubleArg(0.0D, 100.0D))
                                                        .then(Commands.argument("fatigue", DoubleArgumentType.doubleArg(0.0D, 100.0D))
                                                                .executes(context -> setNeeds(context.getSource(),
                                                                        StringArgumentType.getString(context, "citizen"),
                                                                        DoubleArgumentType.getDouble(context, "hunger"),
                                                                        DoubleArgumentType.getDouble(context, "fatigue")))))))))
                .then(Commands.literal("validate").executes(context -> validate(context.getSource())));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> colonyCommands() {
        return Commands.literal("colony")
                .then(Commands.literal("setup").executes(context -> setupSettlement(context.getSource())))
                .then(Commands.literal("create").executes(context -> createSettlement(context.getSource())))
                .then(Commands.literal("list").executes(context -> listSettlements(context.getSource())))
                .then(Commands.literal("info").then(settlementArgument()
                        .executes(context -> settlementInfo(context.getSource(), StringArgumentType.getString(context, "settlement")))))
                .then(Commands.literal("citizens").then(settlementArgument()
                        .executes(context -> settlementCitizens(context.getSource(), StringArgumentType.getString(context, "settlement")))))
                .then(Commands.literal("resources").then(settlementArgument()
                        .executes(context -> settlementResources(context.getSource(), StringArgumentType.getString(context, "settlement")))))
                .then(Commands.literal("economy").then(settlementArgument()
                        .executes(context -> colonyEconomy(context.getSource(), StringArgumentType.getString(context, "settlement")))))
                .then(Commands.literal("production").then(settlementArgument()
                        .executes(context -> colonyProduction(context.getSource(), StringArgumentType.getString(context, "settlement")))))
                .then(Commands.literal("construction").then(settlementArgument()
                        .executes(context -> constructionList(context.getSource(), StringArgumentType.getString(context, "settlement")))))
                .then(Commands.literal("build").then(settlementArgument().then(Commands.argument("type", StringArgumentType.word())
                        .executes(context -> build(context.getSource(), StringArgumentType.getString(context, "settlement"), StringArgumentType.getString(context, "type"))))))
                .then(Commands.literal("buildings").then(settlementArgument()
                        .executes(context -> buildingList(context.getSource(), StringArgumentType.getString(context, "settlement")))))
                .then(Commands.literal("households").then(settlementArgument()
                        .executes(context -> householdList(context.getSource(), StringArgumentType.getString(context, "settlement")))))
                .then(Commands.literal("request").then(settlementArgument()
                        .then(Commands.argument("item", ResourceLocationArgument.id())
                                .then(Commands.argument("quantity", IntegerArgumentType.integer(1, 4096))
                                        .executes(context -> requestProduction(context.getSource(),
                                                StringArgumentType.getString(context, "settlement"),
                                                ResourceLocationArgument.getId(context, "item").toString(),
                                                IntegerArgumentType.getInteger(context, "quantity")))))))
                .then(Commands.literal("join").then(settlementArgument().then(selectorArgument()
                        .executes(context -> joinSettlement(context.getSource(),
                                StringArgumentType.getString(context, "settlement"),
                                StringArgumentType.getString(context, "citizen"))))))
                .then(Commands.literal("leave").then(selectorArgument()
                        .executes(context -> leaveSettlement(context.getSource(), StringArgumentType.getString(context, "citizen")))))
                .then(Commands.literal("register-storage").then(settlementArgument()
                        .executes(context -> registerStorage(context.getSource(), StringArgumentType.getString(context, "settlement")))));
    }

    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, String> settlementArgument() {
        return Commands.argument("settlement", StringArgumentType.string())
                .suggests((context, builder) -> SettlementSelectorResolver.suggest(context.getSource(), builder));
    }

    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, String> selectorArgument() {
        return Commands.argument("citizen", StringArgumentType.string())
                .suggests((context, builder) -> CitizenSelectorResolver.suggest(context.getSource(), builder));
    }

    private static int setup(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        BlockPos origin = BlockPos.containing(source.getPosition());
        int floorY = origin.getY() - 1;
        for (int x = -2; x <= 14; x++) {
            for (int z = -2; z <= 2; z++) {
                level.setBlockAndUpdate(new BlockPos(origin.getX() + x, floorY, origin.getZ() + z),
                        Blocks.STONE.defaultBlockState());
            }
        }

        BlockPos bedFoot = origin.offset(1, 0, 0);
        BlockPos bedHead = bedFoot.relative(Direction.SOUTH);
        level.setBlockAndUpdate(bedFoot, Blocks.RED_BED.defaultBlockState()
                .setValue(BedBlock.FACING, Direction.SOUTH).setValue(BedBlock.PART, BedPart.FOOT));
        level.setBlockAndUpdate(bedHead, Blocks.RED_BED.defaultBlockState()
                .setValue(BedBlock.FACING, Direction.SOUTH).setValue(BedBlock.PART, BedPart.HEAD));

        BlockPos chestPos = origin.offset(4, 0, 0);
        level.setBlockAndUpdate(chestPos, Blocks.CHEST.defaultBlockState());
        if (level.getBlockEntity(chestPos) instanceof ChestBlockEntity chest) {
            chest.setItem(0, new ItemStack(Items.BREAD, 32));
            chest.setChanged();
        }
        BlockPos workplace = origin.offset(12, 0, 0);
        level.setBlockAndUpdate(workplace, Blocks.CRAFTING_TABLE.defaultBlockState());

        CitizenSavedData data = CitizenSavedData.get(source.getServer());
        CitizenRecord record = data.registry().create();
        record.assignments().setHome(AssignedLocation.at(level, bedFoot));
        record.assignments().setBed(AssignedLocation.at(level, bedFoot));
        record.assignments().setFoodStorage(AssignedLocation.at(level, chestPos));
        record.assignments().setWorkplace(AssignedLocation.at(level, workplace));
        data.setDirty();
        CivilianEntity body = CitizenBindingService.materialize(level, record,
                origin.getX() + 0.5D, origin.getY(), origin.getZ() + 0.5D).orElse(null);
        if (body == null) {
            source.sendFailure(Component.literal("Created test record " + record.citizenId() + " but body creation failed"));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Created Phase 1 setup for " + identity(record)
                + " [bed=" + bedFoot.toShortString() + ", food=" + chestPos.toShortString()
                + ", work=" + workplace.toShortString() + "]"), true);
        return 1;
    }

    private static int spawn(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        CitizenSavedData data = CitizenSavedData.get(source.getServer());
        CitizenRecord record = data.registry().create();
        data.setDirty();
        Vec3 position = source.getPosition();
        CivilianEntity body = CitizenBindingService.materialize(level, record,
                position.x, position.y, position.z).orElse(null);
        if (body == null) {
            source.sendFailure(Component.literal("Citizen record " + record.citizenId()
                    + " was created, but its body could not be materialised"));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Spawned " + identity(record) + " [body=" + body.getUUID() + "]"), true);
        return 1;
    }

    private static int list(CommandSourceStack source) {
        CitizenSavedData data = CitizenSavedData.get(source.getServer());
        ActiveCitizenRegistry active = CivilisationServices.activeCitizens(source.getServer());
        source.sendSuccess(() -> Component.literal("Citizens: " + data.registry().size()
                + " (active bodies: " + active.size() + ")"), false);
        for (CitizenRecord record : data.registry().all()) {
            String body = active.bodyFor(record.citizenId()).map(UUID::toString).orElse("none");
            source.sendSuccess(() -> Component.literal(record.readableId() + " | " + record.displayName()
                    + " | " + record.workRole().displayName() + " | " + record.presence() + " | body=" + body), false);
        }
        return data.registry().size();
    }

    private static int inspect(CommandSourceStack source, String selector) {
        CitizenRecord record = resolve(source, selector);
        if (record == null) return 0;
        UUID citizenId = record.citizenId();
        String body = CivilisationServices.activeCitizens(source.getServer()).bodyFor(citizenId)
                .map(UUID::toString).orElse("none");
        source.sendSuccess(() -> Component.literal("Name: " + record.displayName()
                + "\nCitizen ID: " + record.readableId()
                + "\nUUID: " + record.citizenId()
                + "\nrole=" + record.workRole().displayName()
                + "\nlifeStage=" + record.development().lifeStage()
                + " household=" + record.development().householdId().map(UUID::toString).orElse("none")
                + "\ncareer=" + record.development().career() + " (" + record.development().careerLevel() + ")"
                + " education=" + record.development().education()
                + "\nsettlement=" + record.settlementId().map(UUID::toString).orElse("none")
                + "\nappearanceSeed=" + record.appearanceSeed()
                + "\nappearance=" + record.appearance().profileId()
                + "\npresence=" + record.presence()
                + "\nlastLocation=" + record.lastDimension() + " " + record.lastPosition()
                + "\nhunger=" + String.format("%.1f", record.needs().hunger())
                + " fatigue=" + String.format("%.1f", record.needs().fatigue())
                + "\ninventory=" + record.inventory().summary()
                + "\nactiveTask=" + record.brainPersistence().activeTaskType()
                + " suspendedTask=" + record.brainPersistence().suspendedTaskType()
                + "\nhome=" + record.assignments().home().map(Object::toString).orElse("none")
                + "\nbed=" + record.assignments().bed().map(Object::toString).orElse("none")
                + "\nfoodStorage=" + record.assignments().foodStorage().map(Object::toString).orElse("none")
                + "\nworkplace=" + record.assignments().workplace().map(Object::toString).orElse("none")
                + "\nactiveBody=" + body), false);
        return 1;
    }

    private static int debugCitizen(CommandSourceStack source, String selector) {
        CitizenRecord record = resolve(source, selector);
        if (record == null) return 0;
        UUID citizenId = record.citizenId();
        CivilianEntity entity = findActiveEntity(source, citizenId).orElse(null);
        if (entity == null || entity.civilisationBrain().isEmpty()) {
            source.sendSuccess(() -> Component.literal("Citizen " + citizenId + " is not currently brain-active; persisted task="
                    + record.brainPersistence().activeTaskType()), false);
            return 1;
        }
        var brain = entity.civilisationBrain().orElseThrow();
        source.sendSuccess(() -> Component.literal("Citizen " + citizenId + ": " + brain.debugSummary()), false);
        brain.debugHistory().forEach(line -> source.sendSuccess(() -> Component.literal("- " + line), false));
        return 1;
    }

    private static int setNeeds(CommandSourceStack source, String selector, double hunger, double fatigue) {
        CitizenSavedData data = CitizenSavedData.get(source.getServer());
        CitizenRecord record = resolve(source, selector);
        if (record == null) return 0;
        record.needs().set(hunger, fatigue);
        data.setDirty();
        source.sendSuccess(() -> Component.literal("Set " + record.readableId() + " needs: hunger=" + hunger
                + ", fatigue=" + fatigue), true);
        return 1;
    }

    private static CitizenRecord resolve(CommandSourceStack source, String selector) {
        CitizenSelectorResolver.Result result = CitizenSelectorResolver.resolve(source, selector);
        if (!result.success()) source.sendFailure(Component.literal(result.error()));
        return result.citizen();
    }

    private static String identity(CitizenRecord record) {
        return record.readableId() + " | " + record.displayName() + " | UUID " + record.citizenId();
    }

    private static int createSettlement(CommandSourceStack source) {
        SettlementSavedData data = SettlementSavedData.get(source.getServer());
        SettlementState settlement = data.registry().create("New Camp", source.getLevel().getGameTime(),
                AssignedLocation.at(source.getLevel(), BlockPos.containing(source.getPosition())));
        data.setDirty();
        source.sendSuccess(() -> Component.literal("Created " + settlement.readableId() + " | " + settlement.name()
                + " | UUID " + settlement.settlementId()), true);
        return 1;
    }

    private static int listSettlements(CommandSourceStack source) {
        var all = SettlementSavedData.get(source.getServer()).registry().all();
        source.sendSuccess(() -> Component.literal("Settlements: " + all.size()), false);
        all.forEach(s -> source.sendSuccess(() -> Component.literal(s.readableId() + " | " + s.name()
                + " | " + s.stage() + " | citizens=" + s.members().size()), false));
        return all.size();
    }

    private static SettlementState resolveSettlement(CommandSourceStack source, String selector) {
        SettlementSelectorResolver.Result result = SettlementSelectorResolver.resolve(source, selector);
        if (!result.success()) source.sendFailure(Component.literal(result.error()));
        return result.settlement();
    }

    private static int settlementInfo(CommandSourceStack source, String selector) {
        SettlementState s = resolveSettlement(source, selector);
        if (s == null) return 0;
        source.sendSuccess(() -> Component.literal("Name: " + s.name() + "\nSettlement ID: " + s.readableId()
                + "\nUUID: " + s.settlementId() + "\nStage: " + s.stage() + "\nFounded: " + s.foundingTime()
                + "\nCentre: " + s.centre() + "\nCitizens: " + s.members().size()
                + "\nRegistered storage: " + s.storageLocations().size()), false);
        return 1;
    }

    private static int settlementCitizens(CommandSourceStack source, String selector) {
        SettlementState s = resolveSettlement(source, selector);
        if (s == null) return 0;
        CitizenSavedData citizens = CitizenSavedData.get(source.getServer());
        source.sendSuccess(() -> Component.literal(s.readableId() + " citizens: " + s.members().size()), false);
        s.members().forEach(id -> citizens.registry().find(id).ifPresent(c -> source.sendSuccess(() ->
                Component.literal(c.readableId() + " | " + c.displayName() + " | " + c.workRole().displayName()
                        + " | " + c.presence()), false)));
        return s.members().size();
    }

    private static int settlementResources(CommandSourceStack source, String selector) {
        SettlementState s = resolveSettlement(source, selector);
        if (s == null) return 0;
        Map<String, Integer> totals = new LinkedHashMap<>();
        for (AssignedLocation location : s.storageLocations()) {
            if (!location.isIn(source.getLevel()) || !source.getLevel().isLoaded(location.pos())) continue;
            if (!(source.getLevel().getBlockEntity(location.pos()) instanceof Container container)) continue;
            for (int i = 0; i < container.getContainerSize(); i++) {
                ItemStack stack = container.getItem(i);
                if (!stack.isEmpty()) totals.merge(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(), stack.getCount(), Integer::sum);
            }
        }
        CivilisationServices.resourceIndex(source.getServer()).reconcile(
                s.settlementId(), totals, source.getLevel().getGameTime());
        source.sendSuccess(() -> Component.literal(s.readableId() + " physically observed resources: "
                + (totals.isEmpty() ? "none" : totals)), false);
        return 1;
    }

    private static int joinSettlement(CommandSourceStack source, String settlementSelector, String citizenSelector) {
        SettlementState settlement = resolveSettlement(source, settlementSelector);
        CitizenRecord citizen = resolve(source, citizenSelector);
        if (settlement == null || citizen == null) return 0;
        SettlementMembershipService.join(source.getServer(), citizen, settlement);
        settlement.primaryStorage().ifPresent(citizen.assignments()::setFoodStorage);
        CitizenSavedData.get(source.getServer()).setDirty();
        source.sendSuccess(() -> Component.literal(citizen.readableId() + " joined " + settlement.readableId()), true);
        return 1;
    }

    private static int leaveSettlement(CommandSourceStack source, String citizenSelector) {
        CitizenRecord citizen = resolve(source, citizenSelector);
        if (citizen == null) return 0;
        SettlementMembershipService.leave(source.getServer(), citizen);
        source.sendSuccess(() -> Component.literal(citizen.readableId() + " left its settlement"), true);
        return 1;
    }

    private static int registerStorage(CommandSourceStack source, String settlementSelector) {
        SettlementState settlement = resolveSettlement(source, settlementSelector);
        if (settlement == null) return 0;
        BlockPos pos = BlockPos.containing(source.getPosition()).below();
        if (!(source.getLevel().getBlockEntity(pos) instanceof Container)) {
            source.sendFailure(Component.literal("Stand on a physical container to register it"));
            return 0;
        }
        AssignedLocation location = AssignedLocation.at(source.getLevel(), pos);
        settlement.addStorage(location);
        CivilisationServices.economy(source.getServer()).registerEndpoint(settlement.settlementId(), location,
                InventoryEndpointType.SETTLEMENT_STORAGE, InventoryOwnership.SETTLEMENT);
        SettlementSavedData.get(source.getServer()).setDirty();
        source.sendSuccess(() -> Component.literal("Registered settlement storage at " + pos.toShortString()), true);
        return 1;
    }

    private static int setupSettlement(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        BlockPos origin = BlockPos.containing(source.getPosition());
        // The explicit developer fixture must be deterministic even when invoked inside a hill/tree canopy.
        for (int x = -3; x <= 24; x++) for (int z = -9; z <= 24; z++) for (int y = 0; y <= 8; y++) {
            level.setBlockAndUpdate(origin.offset(x, y, z), Blocks.AIR.defaultBlockState());
        }
        for (int x = -3; x <= 24; x++) for (int z = -9; z <= 24; z++) {
            level.setBlockAndUpdate(origin.offset(x, -1, z), Blocks.STONE.defaultBlockState());
        }
        BlockPos storagePos = origin.offset(4, 0, 0);
        level.setBlockAndUpdate(storagePos, Blocks.CHEST.defaultBlockState());
        if (level.getBlockEntity(storagePos) instanceof ChestBlockEntity chest) {
            chest.setItem(0, new ItemStack(Items.BREAD, 16));
            chest.setItem(1, new ItemStack(Items.OAK_LOG, 64));
            chest.setItem(2, new ItemStack(Items.COBBLESTONE, 64));
            chest.setItem(3, new ItemStack(Items.RAW_IRON, 16));
            chest.setItem(4, new ItemStack(Items.COAL, 16));
            chest.setItem(5, new ItemStack(Items.WHEAT_SEEDS, 16));
            chest.setChanged();
        }
        BlockPos workplace = origin.offset(7, 0, 0);
        level.setBlockAndUpdate(workplace, Blocks.CRAFTING_TABLE.defaultBlockState());
        BlockPos inputPos = origin.offset(6, 0, 2), outputPos = origin.offset(8, 0, 2), toolPos = origin.offset(4, 0, 2);
        BlockPos furnacePos = origin.offset(7, 0, 4), smeltInputPos = origin.offset(6, 0, 4), smeltOutputPos = origin.offset(8, 0, 3);
        for (BlockPos chest : List.of(inputPos, outputPos, toolPos, smeltInputPos, smeltOutputPos))
            level.setBlockAndUpdate(chest, Blocks.CHEST.defaultBlockState());
        level.setBlockAndUpdate(furnacePos, Blocks.FURNACE.defaultBlockState());
        if (level.getBlockEntity(toolPos) instanceof ChestBlockEntity tools) {
            tools.setItem(0, new ItemStack(Items.STONE_AXE)); tools.setItem(1, new ItemStack(Items.STONE_AXE));
            tools.setItem(2, new ItemStack(Items.STONE_HOE)); tools.setChanged();
        }

        for (int x = 8; x < 24; x++) for (int z = 4; z < 12; z++) {
            BlockPos soil = origin.offset(x, -1, z);
            level.setBlockAndUpdate(soil, Blocks.FARMLAND.defaultBlockState());
            // Half wheat for staple/resource testing, half carrots for a renewable
            // physical food loop. Replanting still consumes one real harvested item.
            CropBlock crop = z < 11 ? (CropBlock)Blocks.CARROTS : (CropBlock)Blocks.WHEAT;
            level.setBlockAndUpdate(soil.above(), crop.getStateForAge(7));
        }
        // A real water source keeps the physical farmland hydrated. Leave its
        // column unplanted so neither crops nor water are simulated in a ledger.
        for (BlockPos irrigation : List.of(origin.offset(11, -1, 7), origin.offset(19, -1, 7))) {
            level.setBlockAndUpdate(irrigation, Blocks.WATER.defaultBlockState());
            level.setBlockAndUpdate(irrigation.above(), Blocks.AIR.defaultBlockState());
        }
        // Sparse edge rows keep bushes physically accessible without asking the
        // navigator to cross a damaging berry thicket.
        for (int i = 0; i < 16; i++) {
            BlockPos berry = origin.offset(8 + (i % 8) * 2, 0, 13 + (i / 8) * 3);
            level.setBlockAndUpdate(berry.below(), Blocks.DIRT.defaultBlockState());
            level.setBlockAndUpdate(berry, Blocks.SWEET_BERRY_BUSH.defaultBlockState().setValue(SweetBerryBushBlock.AGE, 3));
        }
        for (int i = 0; i < 8; i++) for (int y = 0; y < 3; y++) {
            level.setBlockAndUpdate(origin.offset(8 + i * 2, y, 21), Blocks.OAK_LOG.defaultBlockState());
        }

        SettlementSavedData settlementData = SettlementSavedData.get(source.getServer());
        SettlementState settlement = settlementData.registry().create("Founders Camp", level.getGameTime(), AssignedLocation.at(level, origin));
        settlement.addStorage(AssignedLocation.at(level, storagePos));
        settlementData.setDirty();
        var economy = CivilisationServices.economy(source.getServer());
        InventoryEndpoint general = economy.registerEndpoint(settlement.settlementId(), AssignedLocation.at(level, storagePos), InventoryEndpointType.SETTLEMENT_STORAGE, InventoryOwnership.SETTLEMENT);
        InventoryEndpoint craftIn = economy.registerEndpoint(settlement.settlementId(), AssignedLocation.at(level, inputPos), InventoryEndpointType.WORKSHOP_INPUT, InventoryOwnership.WORKPLACE);
        InventoryEndpoint craftOut = economy.registerEndpoint(settlement.settlementId(), AssignedLocation.at(level, outputPos), InventoryEndpointType.WORKSHOP_OUTPUT, InventoryOwnership.WORKPLACE);
        economy.registerEndpoint(settlement.settlementId(), AssignedLocation.at(level, toolPos), InventoryEndpointType.TOOL_STORAGE, InventoryOwnership.SETTLEMENT);
        InventoryEndpoint smeltIn = economy.registerEndpoint(settlement.settlementId(), AssignedLocation.at(level, smeltInputPos), InventoryEndpointType.WORKSHOP_INPUT, InventoryOwnership.WORKPLACE);
        InventoryEndpoint smeltOut = economy.registerEndpoint(settlement.settlementId(), AssignedLocation.at(level, smeltOutputPos), InventoryEndpointType.WORKSHOP_OUTPUT, InventoryOwnership.WORKPLACE);
        economy.registerWorkshop(settlement.settlementId(), WorkshopType.GENERAL_CRAFTING, AssignedLocation.at(level, workplace), craftIn, craftOut);
        economy.registerWorkshop(settlement.settlementId(), WorkshopType.SMELTING, AssignedLocation.at(level, furnacePos), smeltIn, smeltOut);
        CitizenSavedData citizenData = CitizenSavedData.get(source.getServer());
        int created = 0;
        for (int i = 0; i < 12; i++) {
            BlockPos bedFoot = origin.offset((i % 6) * 2, 0, i < 6 ? -7 : -3);
            BlockPos bedHead = bedFoot.relative(Direction.SOUTH);
            level.setBlockAndUpdate(bedFoot, Blocks.RED_BED.defaultBlockState()
                    .setValue(BedBlock.FACING, Direction.SOUTH).setValue(BedBlock.PART, BedPart.FOOT));
            level.setBlockAndUpdate(bedHead, Blocks.RED_BED.defaultBlockState()
                    .setValue(BedBlock.FACING, Direction.SOUTH).setValue(BedBlock.PART, BedPart.HEAD));
            CitizenRecord citizen = citizenData.registry().create();
            citizen.assignments().setHome(AssignedLocation.at(level, bedFoot));
            citizen.assignments().setBed(AssignedLocation.at(level, bedFoot));
            citizen.assignments().setFoodStorage(AssignedLocation.at(level, storagePos));
            citizen.assignments().setWorkplace(AssignedLocation.at(level, workplace));
            citizen.setWorkRole(switch (i % 8) {
                case 0 -> WorkRole.FARMER;
                case 1 -> WorkRole.FORAGER;
                case 2 -> WorkRole.LUMBERJACK;
                case 3 -> WorkRole.HAULER;
                case 4 -> WorkRole.COURIER;
                case 5 -> WorkRole.CRAFTER;
                case 6 -> WorkRole.SMELTER;
                default -> WorkRole.GENERAL_LABOURER;
            });
            SettlementMembershipService.join(source.getServer(), citizen, settlement);
            if (CitizenBindingService.materialize(level, citizen, origin.getX() + (i % 4) + 0.5D,
                    origin.getY(), origin.getZ() + (i / 4) + 0.5D).isPresent()) created++;
        }
        citizenData.setDirty();
        CivilisationServices.demography(source.getServer()).reconcileNow();
        CivilisationServices.buildingDemand(source.getServer()).evaluateNow(settlement);
        int result = created;
        source.sendSuccess(() -> Component.literal("Created Phase 3 test settlement " + settlement.readableId()
                + " with " + result + "/12 citizens, explicit storage ownership, craft/smelt workshops, finite tools, crops, forage and bounded logs"), true);
        return created;
    }

    private static int colonyEconomy(CommandSourceStack source, String selector) {
        SettlementState s = resolveSettlement(source, selector); if (s == null) return 0;
        var economy = CivilisationServices.economy(source.getServer()); var state = economy.data().forSettlement(s.settlementId());
        source.sendSuccess(() -> Component.literal(s.readableId() + " economy"
                + "\nEndpoints: " + state.endpoints().size() + " | workshops: " + state.workshops().size()
                + "\nPhysical stock is authoritative; reservations: " + state.reservations().size()
                + " | in-flight deliveries: " + state.deliveryRequests().stream().filter(d -> d.state() != com.tyes.civilisation.economy.logistics.DeliveryState.COMPLETE && d.state() != com.tyes.civilisation.economy.logistics.DeliveryState.CANCELLED).count()
                + "\nProtected reserves: " + state.protectedReserves()
                + "\nShortages: " + state.shortages()), false);
        return 1;
    }

    private static int colonyProduction(CommandSourceStack source, String selector) {
        SettlementState s = resolveSettlement(source, selector); if (s == null) return 0;
        var requests = CivilisationServices.economy(source.getServer()).data().forSettlement(s.settlementId()).productionRequests();
        source.sendSuccess(() -> Component.literal(s.readableId() + " production requests: " + requests.size()), false);
        requests.forEach(r -> source.sendSuccess(() -> Component.literal(r.requestId() + " | " + r.output() + " "
                + r.completedQuantity() + "/" + r.desiredQuantity() + " | " + r.state() + " | blocker=" + r.blocker()
                + " | priority=" + r.priority() + " | method=" + r.method() + " | reason=" + r.reason()), false)); return 1;
    }

    private static int requestProduction(CommandSourceStack source, String selector, String item, int quantity) {
        SettlementState s = resolveSettlement(source, selector); if (s == null) return 0;
        try {
            var request = CivilisationServices.economy(source.getServer()).request(s.settlementId(), ItemKey.parse(item), quantity);
            source.sendSuccess(() -> Component.literal("Created production request " + request.requestId() + " for " + quantity + " " + request.output()), true); return 1;
        } catch (IllegalArgumentException error) { source.sendFailure(Component.literal(error.getMessage())); return 0; }
    }

    private static int economyDebug(CommandSourceStack source, String selector, String kind) {
        SettlementState s = resolveSettlement(source, selector); if (s == null) return 0;
        var state = CivilisationServices.economy(source.getServer()).data().forSettlement(s.settlementId());
        switch (kind) {
            case "reservations" -> { source.sendSuccess(() -> Component.literal("Reservations: " + state.reservations().size()), false); state.reservations().forEach(r -> source.sendSuccess(() -> Component.literal(r.reservationId()+" | "+r.item()+" x"+r.quantity()+" | source="+r.source()+" | purpose="+r.purpose()+" | expires="+r.expiryTick()), false)); }
            case "deliveries" -> { source.sendSuccess(() -> Component.literal("Deliveries: " + state.deliveryRequests().size()), false); state.deliveryRequests().forEach(d -> source.sendSuccess(() -> Component.literal(d.id()+" | "+d.item()+" x"+d.quantity()+" | priority="+d.priority()+" cargo="+d.cargoQuantity()+" | "+d.state()+" | "+d.source()+" -> "+d.destination()), false)); }
            default -> { source.sendSuccess(() -> Component.literal("Workshops: " + state.workshops().size()), false); state.workshops().forEach(w -> source.sendSuccess(() -> Component.literal(w.id()+" | "+w.type()+" | "+w.workstation()+" | input="+w.input()+" output="+w.output()+" claimed="+w.claimedBy().orElse(null)), false)); }
        }
        return 1;
    }

    private static int build(CommandSourceStack source, String selector, String type) {
        SettlementState s=resolveSettlement(source,selector);if(s==null)return 0;if(!type.equals("starter_house")&&!type.equals("house")){source.sendFailure(Component.literal("Supported Phase 4 type: starter_house"));return 0;}
        try {var p=CivilisationServices.construction(source.getServer()).createStarterHouse(s);source.sendSuccess(()->Component.literal("Created "+p.readableId()+" starter house: "+p.totalPlacements()+" physical placements, stockpile="+p.stockpile().pos().toShortString()),true);return 1;}catch(RuntimeException e){source.sendFailure(Component.literal("Construction project rejected: "+e.getMessage()));return 0;}
    }
    private static int constructionList(CommandSourceStack source,String selector){SettlementState s=resolveSettlement(source,selector);if(s==null)return 0;var construction=CivilisationServices.construction(source.getServer());var ps=construction.data().projects().stream().filter(p->p.settlementId().equals(s.settlementId())).toList();var housing=CivilisationServices.buildingDemand(source.getServer()).housingStatus(s);source.sendSuccess(()->Component.literal(s.readableId()+" construction projects: "+ps.size()+"\nHousing: households="+housing.householdCount()+" needing homes="+housing.householdsNeedingHomes()+" required="+housing.requiredHouseholdCapacity()+" completed="+housing.completedCapacity()+" future="+housing.futureCapacity()+" occupied="+housing.occupiedCapacity()+" free="+housing.availableCapacity()+" remaining deficit="+housing.deficit()+"\nBuilders: assigned="+construction.assignedBuilders(s.settlementId())+" desired="+construction.desiredBuilders(s.settlementId())+" available="+construction.availableBuilders(s.settlementId())),false);construction.data().demand(s.settlementId()).ifPresent(d->source.sendSuccess(()->Component.literal("Current demand: "+d.reason()+" severity="+d.severity()+" activeProject="+d.activeProjectId().map(UUID::toString).orElse("waiting for site")),false));ps.forEach(p->{source.sendSuccess(()->Component.literal(p.readableId()+" | "+p.state()+" blocker="+p.blocker()+" | priority="+p.priority()+" because "+p.priorityReason()+" | "+p.completedPlacements()+"/"+p.totalPlacements()+" | style="+p.blueprint().style().value()+" | building="+p.registeredBuildingId().map(UUID::toString).orElse("none")+" | anchor="+p.anchor().pos().toShortString()+" | stockpile="+p.stockpile().pos().toShortString()),false);construction.accounting(p).forEach((item,a)->source.sendSuccess(()->Component.literal("  "+item+": required="+a.required()+" placed="+a.placed()+" stockpile="+a.stockpiled()+" builder-held="+a.builderHeld()+" missing="+a.missing()),false));});return 1;}
    private static int buildingList(CommandSourceStack source,String selector){SettlementState s=resolveSettlement(source,selector);if(s==null)return 0;var bs=CivilisationServices.construction(source.getServer()).data().buildings().stream().filter(b->b.settlementId().equals(s.settlementId())).toList();source.sendSuccess(()->Component.literal(s.readableId()+" registered buildings: "+bs.size()),false);bs.forEach(b->source.sendSuccess(()->Component.literal(b.buildingId()+" | "+b.type()+" | "+b.condition()+" | capacity="+b.capacity()+" | style="+b.style().value()),false));return 1;}
    private static int debugProject(CommandSourceStack source,String id){try{UUID u=UUID.fromString(id);var construction=CivilisationServices.construction(source.getServer());var p=construction.data().project(u).orElse(null);if(p==null){source.sendFailure(Component.literal("Unknown project"));return 0;}source.sendSuccess(()->Component.literal(p.readableId()+" "+p.state()+" blocker="+p.blocker()+" priority="+p.priority()+" reason=\""+p.priorityReason()+"\" changedTick="+p.priorityChangedTick()+" progress="+p.completedPlacements()+"/"+p.totalPlacements()+" anchor="+p.anchor()+" stockpile="+p.stockpile()+" palette="+p.palette().asMap()+" building="+p.registeredBuildingId().map(UUID::toString).orElse("none")+" claims="+p.claims()+" failures="+p.failures()),false);construction.accounting(p).forEach((item,a)->source.sendSuccess(()->Component.literal(item+": required="+a.required()+" placed="+a.placed()+" stockpile="+a.stockpiled()+" builder-held="+a.builderHeld()+" missing="+a.missing()),false));return 1;}catch(IllegalArgumentException e){source.sendFailure(Component.literal("Project ID must be a UUID"));return 0;}}

    private static int buildingDemand(CommandSourceStack source, String selector) {
        SettlementState settlement = resolveSettlement(source, selector); if (settlement == null) return 0;
        var service = CivilisationServices.buildingDemand(source.getServer());
        service.evaluateNow(settlement);
        var status = service.housingStatus(settlement);
        source.sendSuccess(() -> Component.literal("Housing for " + settlement.readableId()
                + "\nPopulation: " + status.population() + "\nHouseholds: " + status.householdCount()
                + "\nHouseholds needing homes: " + status.householdsNeedingHomes()
                + "\nRequired household capacity: " + status.requiredHouseholdCapacity()
                + "\nCompleted capacity: " + status.completedCapacity()
                + "\nFuture capacity (including valid projects): " + status.futureCapacity()
                + "\nOccupied: " + status.occupiedCapacity() + "\nAvailable: " + status.availableCapacity()
                + "\nRemaining deficit: " + status.deficit()), false);
        CivilisationServices.construction(source.getServer()).data().demand(settlement.settlementId())
                .ifPresent(d -> source.sendSuccess(() -> Component.literal("Demand: " + d.reason()
                        + " | " + d.severity() + " | project="
                        + d.activeProjectId().map(UUID::toString).orElse("waiting for site")), false));
        return 1;
    }

    private static int householdList(CommandSourceStack source, String selector) {
        SettlementState settlement = resolveSettlement(source, selector); if (settlement == null) return 0;
        var households = CivilisationServices.demography(source.getServer()).data().households()
                .forSettlement(settlement.settlementId());
        source.sendSuccess(() -> Component.literal(settlement.readableId() + " households: " + households.size()), false);
        households.forEach(h -> source.sendSuccess(() -> Component.literal(h.readableId() + " | " + h.state()
                + " | members=" + h.members().size() + " guardians=" + h.guardians().size()
                + " dependants=" + h.dependants().size() + " home="
                + h.homeBuildingId().map(UUID::toString).orElse("none")
                + "\n  memberIds=" + citizenRefs(source, h.members())
                + " guardians=" + citizenRefs(source, h.guardians())
                + " dependants=" + citizenRefs(source, h.dependants())), false));
        return households.size();
    }

    private static String citizenRefs(CommandSourceStack source, Collection<UUID> ids) {
        var registry = CitizenSavedData.get(source.getServer()).registry();
        return ids.stream().map(id -> registry.find(id).map(CitizenRecord::readableId)
                .orElse(id.toString())).sorted().toList().toString();
    }

    private static int debugDemography(CommandSourceStack source, String selector) {
        CitizenRecord citizen = resolve(source, selector); if (citizen == null) return 0;
        var dev = citizen.development();
        var apprenticeship = CivilisationServices.demography(source.getServer()).data()
                .forApprentice(citizen.citizenId()).orElse(null);
        source.sendSuccess(() -> Component.literal(citizen.readableId() + " | stage=" + dev.lifeStage()
                + " birthTick=" + dev.birthTick() + " nextTransition=" + dev.nextTransitionTick()
                + "\nhousehold=" + dev.householdId().map(UUID::toString).orElse("none")
                + "\neducation=" + dev.education() + " career=" + dev.career() + " level=" + dev.careerLevel()
                + "\nconstruction proficiency=" + com.tyes.civilisation.society.CareerLevel.fromExperience(dev.experience(com.tyes.civilisation.settlement.WorkRole.BUILDER))
                + " construction experience=" + dev.experience(com.tyes.civilisation.settlement.WorkRole.BUILDER)
                + "\nexperience=" + dev.experience() + "\napprenticeship="
                + (apprenticeship == null ? "none" : apprenticeship.apprenticeshipId() + " "
                + apprenticeship.focus() + " " + apprenticeship.state() + " mentor="
                + apprenticeship.mentorId().map(UUID::toString).orElse("none"))), false);
        return 1;
    }

    private static int debugKnowledge(CommandSourceStack source, String selector) {
        CitizenRecord citizen = resolve(source, selector); if (citizen == null) return 0;
        StringBuilder report = new StringBuilder(citizen.readableId()).append(" knowledge");
        if (citizen.knowledge().concepts().isEmpty()) report.append("\nConcepts: none");
        else citizen.knowledge().concepts().forEach((id, points) -> report.append("\nConcept ").append(id)
                .append(" = ").append(com.tyes.civilisation.knowledge.KnowledgeLevel.fromPoints(points))
                .append(" (").append(points).append(" points)"));
        if (citizen.knowledge().recipes().isEmpty()) report.append("\nRecipes: none");
        else citizen.knowledge().recipes().forEach((id, points) -> report.append("\nRecipe ").append(id)
                .append(" = ").append(com.tyes.civilisation.knowledge.KnowledgeLevel.fromPoints(points))
                .append(" (").append(points).append(" points)"));
        report.append("\nHistory entries: ").append(citizen.history().events().size());
        source.sendSuccess(() -> Component.literal(report.toString()), false); return 1;
    }

    private static int createTestChild(CommandSourceStack source, String selector) {
        SettlementState settlement = resolveSettlement(source, selector); if (settlement == null) return 0;
        var service = CivilisationServices.demography(source.getServer());
        HouseholdRecord household = service.data().households().forSettlement(settlement.settlementId()).stream()
                .filter(h -> !h.guardians().isEmpty())
                .filter(h -> h.homeBuildingId().flatMap(id -> CivilisationServices.construction(source.getServer())
                                .data().buildings().stream().filter(b -> b.buildingId().equals(id)).findFirst())
                        .map(home -> home.capacity() > h.members().size()).orElse(true))
                .findFirst().orElse(null);
        if (household == null) {
            List<CitizenRecord> adults = settlement.members().stream()
                    .map(id -> CitizenSavedData.get(source.getServer()).registry().find(id).orElse(null))
                    .filter(c -> c != null && c.development().lifeStage().mayPerformFullWork()).limit(2).toList();
            if (adults.isEmpty()) { source.sendFailure(Component.literal("No adult guardian is available")); return 0; }
            household = service.createHousehold(settlement, adults, source.getLevel().getGameTime());
        }
        CitizenRecord child = service.createChild(settlement, household, source.getLevel().getGameTime());
        source.sendSuccess(() -> Component.literal("Created abstract demographic test child " + child.readableId()
                + " in " + child.development().householdId().flatMap(service.data().households()::find)
                .map(HouseholdRecord::readableId).orElse("unknown household")
                + "; no explicit reproduction mechanics are simulated"), true);
        return 1;
    }

    private static int advanceLife(CommandSourceStack source, String selector) {
        CitizenRecord citizen = resolve(source, selector); if (citizen == null) return 0;
        LifeStage next = switch (citizen.development().lifeStage()) {
            case CHILD -> LifeStage.ADOLESCENT;
            case ADOLESCENT -> LifeStage.ADULT;
            case ADULT, ELDER -> LifeStage.ELDER;
        };
        CivilisationServices.demography(source.getServer()).forceStage(citizen, next, source.getLevel().getGameTime());
        source.sendSuccess(() -> Component.literal("Advanced " + citizen.readableId() + " to " + next
                + " for controlled testing"), true);
        return 1;
    }

    private static Optional<CivilianEntity> findActiveEntity(CommandSourceStack source, UUID citizenId) {
        UUID entityId = CivilisationServices.activeCitizens(source.getServer()).bodyFor(citizenId).orElse(null);
        if (entityId == null) return Optional.empty();
        for (ServerLevel level : source.getServer().getAllLevels()) {
            if (level.getEntity(entityId) instanceof CivilianEntity civilian) return Optional.of(civilian);
        }
        return Optional.empty();
    }

    private static int validate(CommandSourceStack source) {
        CivilisationValidation.Result result = CivilisationValidation.validate(source.getServer());
        String summary = "Citizen records: " + result.citizenCount()
                + "\nActive bodies: " + result.activeBodyCount()
                + "\nDuplicate bodies: " + result.duplicateBodyCount()
                + "\nMissing/broken references: " + result.brokenReferenceCount()
                + "\nInvalid assignments: " + result.invalidAssignmentCount()
                + "\nSettlement records: " + result.settlementCount()
                + "\nMembership errors: " + result.membershipErrorCount()
                + "\nInvalid settlement storage: " + result.storageErrorCount()
                + "\nActive work claims: " + result.activeWorkClaims()
                + "\nAppearance errors: " + result.appearanceErrorCount()
                + "\nEconomy invariant errors: " + result.economyErrorCount()
                + "\nHousehold/demography errors: " + result.demographyErrorCount()
                + "\nKnowledge/research errors: " + result.knowledgeErrorCount()
                + "\nOverall integrity: " + (result.valid() ? "PASS" : "FAIL");
        if (result.valid()) {
            source.sendSuccess(() -> Component.literal(summary), false);
            return 1;
        }
        source.sendFailure(Component.literal(summary));
        result.problems().forEach(problem -> source.sendFailure(Component.literal("- " + problem)));
        return 0;
    }
}
