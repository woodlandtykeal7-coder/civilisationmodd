package com.tyes.civilisation.work;

import com.tyes.civilisation.citizen.AssignedLocation;
import com.tyes.civilisation.citizen.CitizenRecord;
import com.tyes.civilisation.persistence.SettlementSavedData;
import com.tyes.civilisation.settlement.SettlementState;
import com.tyes.civilisation.settlement.WorkRole;
import com.tyes.civilisation.server.CivilisationServices;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.state.BlockState;

/** Runtime, bounded task board. Targets are cached observations; physical blocks remain authoritative. */
public final class SettlementWorkBoard {
    private static final int RADIUS = 24;
    private static final int VERTICAL_RADIUS = 4;
    /** Number of x/z columns examined in one staggered scan pass. */
    private static final int SCAN_COLUMN_BUDGET = 96;
    private static final long CLAIM_TTL = 1200L;
    private final MinecraftServer server;
    private final Map<UUID, ScanState> scans = new HashMap<>();
    private final Map<TargetKey, Claim> claims = new HashMap<>();

    public SettlementWorkBoard(MinecraftServer server) { this.server = server; }

    public Optional<WorkAssignment> claim(CitizenRecord citizen, ServerLevel level) {
        UUID settlementId = citizen.settlementId().orElse(null);
        if (settlementId == null) return Optional.empty();
        SettlementState settlement = SettlementSavedData.get(server).registry().find(settlementId).orElse(null);
        if (settlement == null || !settlement.centre().isIn(level)) return Optional.empty();
        ScanState scan = scans.computeIfAbsent(settlementId, ignored -> new ScanState());
        scan(level, settlement, scan);
        expire(level.getGameTime());
        return claimFromTypes(citizen, level, settlementId, scan, order(citizen.workRole()));
    }

    /** Emergency food production is still real work: it claims a physical crop/bush target. */
    public Optional<WorkAssignment> claimFood(CitizenRecord citizen, ServerLevel level) {
        UUID settlementId = citizen.settlementId().orElse(null);
        if (settlementId == null) return Optional.empty();
        SettlementState settlement = SettlementSavedData.get(server).registry().find(settlementId).orElse(null);
        if (settlement == null || !settlement.centre().isIn(level)) return Optional.empty();
        ScanState scan = scans.computeIfAbsent(settlementId, ignored -> new ScanState());
        scan(level, settlement, scan);
        expire(level.getGameTime());
        return claimFromTypes(citizen, level, settlementId, scan, List.of(WorkType.FORAGE, WorkType.FARM));
    }

    private Optional<WorkAssignment> claimFromTypes(CitizenRecord citizen, ServerLevel level, UUID settlementId,
                                                     ScanState scan, List<WorkType> types) {
        for (WorkType type : types) {
            List<BlockPos> targets = scan.targets.get(type);
            for (int i = 0; i < targets.size(); i++) {
                BlockPos pos = targets.get(i);
                if (!valid(type, level.getBlockState(pos)) || type == WorkType.LUMBER && CivilisationServices.construction(server).protects(settlementId, level, pos)) continue;
                TargetKey key = new TargetKey(settlementId, pos.asLong());
                if (claims.containsKey(key)) continue;
                claims.put(key, new Claim(citizen.citizenId(), level.getGameTime() + CLAIM_TTL));
                return Optional.of(new WorkAssignment(settlementId, unitId(type), type, AssignedLocation.at(level, pos)));
            }
        }
        return Optional.empty();
    }

    public boolean claimSpecific(UUID citizenId, WorkAssignment assignment, long now) {
        TargetKey key = new TargetKey(assignment.settlementId(), assignment.target().pos().asLong());
        Claim current = claims.get(key);
        if (current != null && current.expiry > now && !current.citizenId.equals(citizenId)) return false;
        claims.put(key, new Claim(citizenId, now + CLAIM_TTL));
        return true;
    }

    public void release(UUID citizenId, WorkAssignment assignment) {
        TargetKey key = new TargetKey(assignment.settlementId(), assignment.target().pos().asLong());
        Claim claim = claims.get(key);
        if (claim != null && claim.citizenId.equals(citizenId)) claims.remove(key);
    }

    public int activeClaims() { return claims.size(); }

    private void scan(ServerLevel level, SettlementState settlement, ScanState scan) {
        if (level.getGameTime() - scan.lastScanTick < 20L && !scan.targets.values().stream().allMatch(List::isEmpty)) return;
        BlockPos centre = settlement.centre().pos();
        int width = RADIUS * 2 + 1;
        int total = width * width;
        if (scan.cursor == 0) scan.targets.values().forEach(List::clear);
        for (int examined = 0; examined < SCAN_COLUMN_BUDGET; examined++) {
            int index = scan.cursor++;
            if (scan.cursor >= total) scan.cursor = 0;
            int zIndex = index / width;
            int xIndex = index % width;
            for (int dy = -VERTICAL_RADIUS; dy <= VERTICAL_RADIUS; dy++) {
                BlockPos pos = centre.offset(xIndex - RADIUS, dy, zIndex - RADIUS);
                if (!level.isLoaded(pos)) continue;
                BlockState state = level.getBlockState(pos);
                for (WorkType type : WorkType.values()) {
                    if (valid(type, state) && !(type == WorkType.LUMBER && CivilisationServices.construction(server).protects(settlement.settlementId(), level, pos))) scan.targets.get(type).add(pos.immutable());
                }
            }
            if (scan.cursor == 0) break;
        }
        scan.lastScanTick = level.getGameTime();
    }

    public static boolean valid(WorkType type, BlockState state) {
        return switch (type) {
            case LUMBER -> state.is(BlockTags.LOGS);
            case FARM -> state.getBlock() instanceof CropBlock crop && crop.isMaxAge(state);
            case FORAGE -> state.is(Blocks.SWEET_BERRY_BUSH)
                    && state.getValue(SweetBerryBushBlock.AGE) >= 2;
        };
    }

    private void expire(long now) { claims.entrySet().removeIf(e -> e.getValue().expiry <= now); }
    private static String unitId(WorkType type) {
        return switch (type) {
            case FARM -> "Harvest local field section";
            case FORAGE -> "Gather food in camp foraging zone";
            case LUMBER -> "Harvest bounded forest target";
        };
    }
    private static List<WorkType> order(WorkRole role) {
        return switch (role) {
            case FARMER -> List.of(WorkType.FARM);
            case FORAGER -> List.of(WorkType.FORAGE, WorkType.FARM);
            case LUMBERJACK -> List.of(WorkType.LUMBER);
            case HAULER -> List.of();
            case CRAFTER, SMELTER, COURIER, BUILDER, TEACHER -> List.of();
            case MINER -> List.of();
            case GENERAL_LABOURER -> List.of(WorkType.FARM, WorkType.FORAGE, WorkType.LUMBER);
        };
    }
    private static final class ScanState {
        private final Map<WorkType, List<BlockPos>> targets = new EnumMap<>(WorkType.class);
        private int cursor;
        private long lastScanTick = Long.MIN_VALUE;
        private ScanState() { for (WorkType type : WorkType.values()) targets.put(type, new ArrayList<>()); }
    }
    private record TargetKey(UUID settlementId, long blockPos) { }
    private record Claim(UUID citizenId, long expiry) { }
}
