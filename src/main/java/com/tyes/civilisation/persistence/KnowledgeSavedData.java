package com.tyes.civilisation.persistence;

import com.tyes.civilisation.CivilisationMod;
import com.tyes.civilisation.knowledge.research.ResearchProject;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/** Research/institution coordination. Personal knowledge remains inside each CitizenRecord. */
public final class KnowledgeSavedData extends SavedData {
    public static final int CURRENT_SCHEMA_VERSION = 1;
    private static final String DATA_NAME = CivilisationMod.MOD_ID + "_knowledge";
    private final Map<UUID, ResearchProject> research = new LinkedHashMap<>();
    private int nextResearchNumber = 1;

    public static KnowledgeSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(new Factory<>(KnowledgeSavedData::new,
                KnowledgeSavedData::load), DATA_NAME);
    }
    public Collection<ResearchProject> research() { return List.copyOf(research.values()); }
    public Optional<ResearchProject> research(UUID id) { return Optional.ofNullable(research.get(id)); }
    public void add(ResearchProject project) { research.put(project.id(), project); setDirty(); }
    public String nextReadableId() { setDirty(); return "R-%04d".formatted(nextResearchNumber++); }

    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("schema", CURRENT_SCHEMA_VERSION); tag.putInt("next", nextResearchNumber);
        ListTag list = new ListTag(); research.values().forEach(project -> list.add(project.save())); tag.put("research", list); return tag;
    }
    private static KnowledgeSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        if (tag.getInt("schema") > CURRENT_SCHEMA_VERSION) throw new IllegalStateException("Unsupported knowledge schema");
        KnowledgeSavedData result = new KnowledgeSavedData(); result.nextResearchNumber = Math.max(1, tag.getInt("next"));
        ListTag list = tag.getList("research", Tag.TAG_COMPOUND); for (int i = 0; i < list.size(); i++) {
            ResearchProject project = ResearchProject.load(list.getCompound(i)); result.research.put(project.id(), project); }
        return result;
    }
}
