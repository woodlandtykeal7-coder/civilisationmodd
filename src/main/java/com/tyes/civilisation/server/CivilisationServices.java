package com.tyes.civilisation.server;

import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.server.MinecraftServer;
import com.tyes.civilisation.work.SettlementWorkBoard;
import com.tyes.civilisation.work.SettlementResourceIndex;
import com.tyes.civilisation.economy.SettlementEconomyService;
import com.tyes.civilisation.construction.ConstructionService;
import com.tyes.civilisation.construction.demand.BuildingDemandService;
import com.tyes.civilisation.society.DemographyService;
import com.tyes.civilisation.knowledge.KnowledgeService;

public final class CivilisationServices {
    private static final Map<MinecraftServer, ActiveCitizenRegistry> ACTIVE = new WeakHashMap<>();
    private static final Map<MinecraftServer, SettlementWorkBoard> WORK = new WeakHashMap<>();
    private static final Map<MinecraftServer, SettlementResourceIndex> RESOURCES = new WeakHashMap<>();
    private static final Map<MinecraftServer, SettlementEconomyService> ECONOMY = new WeakHashMap<>();
    private static final Map<MinecraftServer, ConstructionService> CONSTRUCTION = new WeakHashMap<>();
    private static final Map<MinecraftServer, BuildingDemandService> BUILDING_DEMAND = new WeakHashMap<>();
    private static final Map<MinecraftServer, DemographyService> DEMOGRAPHY = new WeakHashMap<>();
    private static final Map<MinecraftServer, KnowledgeService> KNOWLEDGE = new WeakHashMap<>();

    private CivilisationServices() {
    }

    public static synchronized ActiveCitizenRegistry activeCitizens(MinecraftServer server) {
        return ACTIVE.computeIfAbsent(server, ignored -> new ActiveCitizenRegistry());
    }

    public static synchronized SettlementWorkBoard workBoard(MinecraftServer server) {
        return WORK.computeIfAbsent(server, SettlementWorkBoard::new);
    }

    public static synchronized SettlementResourceIndex resourceIndex(MinecraftServer server) {
        return RESOURCES.computeIfAbsent(server, ignored -> new SettlementResourceIndex());
    }

    public static synchronized SettlementEconomyService economy(MinecraftServer server) {
        return ECONOMY.computeIfAbsent(server, key -> new SettlementEconomyService(key, resourceIndex(key)));
    }
    public static synchronized ConstructionService construction(MinecraftServer server) { return CONSTRUCTION.computeIfAbsent(server, ConstructionService::new); }
    public static synchronized BuildingDemandService buildingDemand(MinecraftServer server) {
        return BUILDING_DEMAND.computeIfAbsent(server, key -> new BuildingDemandService(key, construction(key)));
    }
    public static synchronized DemographyService demography(MinecraftServer server) {
        return DEMOGRAPHY.computeIfAbsent(server, DemographyService::new);
    }
    public static synchronized KnowledgeService knowledge(MinecraftServer server) {
        return KNOWLEDGE.computeIfAbsent(server, KnowledgeService::new);
    }

    public static synchronized void remove(MinecraftServer server) {
        ACTIVE.remove(server);
        WORK.remove(server);
        RESOURCES.remove(server);
        ECONOMY.remove(server);
        CONSTRUCTION.remove(server);
        BUILDING_DEMAND.remove(server);
        DEMOGRAPHY.remove(server);
        KNOWLEDGE.remove(server);
    }
}
