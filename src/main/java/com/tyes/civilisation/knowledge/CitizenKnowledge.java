package com.tyes.civilisation.knowledge;

import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

/** Persistent personal knowledge. It is deliberately not stored on the settlement. */
public final class CitizenKnowledge {
    private static final int MAX_ENTRIES = 256;
    private static final int MAX_POINTS = 10_000;
    private final Map<ResourceLocation, Integer> concepts = new LinkedHashMap<>();
    private final Map<ResourceLocation, Integer> recipes = new LinkedHashMap<>();

    public Map<ResourceLocation, Integer> concepts() { return Map.copyOf(concepts); }
    public Map<ResourceLocation, Integer> recipes() { return Map.copyOf(recipes); }
    public int conceptPoints(ResourceLocation id) { return concepts.getOrDefault(id, 0); }
    public int recipePoints(ResourceLocation id) { return recipes.getOrDefault(id, 0); }
    public KnowledgeLevel conceptLevel(ResourceLocation id) { return KnowledgeLevel.fromPoints(conceptPoints(id)); }
    public KnowledgeLevel recipeLevel(ResourceLocation id) { return KnowledgeLevel.fromPoints(recipePoints(id)); }

    public boolean addConcept(ResourceLocation id, int amount) { return add(concepts, id, amount); }
    public boolean addRecipe(ResourceLocation id, int amount) { return add(recipes, id, amount); }

    private boolean add(Map<ResourceLocation, Integer> target, ResourceLocation id, int amount) {
        if (amount <= 0) return false;
        int before = target.getOrDefault(id, 0);
        if (before == 0 && target.size() >= MAX_ENTRIES) return false;
        int after = Math.min(MAX_POINTS, before + amount);
        if (after == before) return false;
        target.put(id, after);
        return true;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.put("concepts", saveMap(concepts));
        tag.put("recipes", saveMap(recipes));
        return tag;
    }

    public static CitizenKnowledge load(CompoundTag tag) {
        CitizenKnowledge result = new CitizenKnowledge();
        loadMap(tag.getCompound("concepts"), result.concepts);
        loadMap(tag.getCompound("recipes"), result.recipes);
        return result;
    }

    private static CompoundTag saveMap(Map<ResourceLocation, Integer> values) {
        CompoundTag tag = new CompoundTag();
        values.forEach((id, points) -> tag.putInt(id.toString(), points));
        return tag;
    }

    private static void loadMap(CompoundTag source, Map<ResourceLocation, Integer> target) {
        for (String key : source.getAllKeys()) {
            ResourceLocation id = ResourceLocation.tryParse(key);
            int points = source.getInt(key);
            if (id != null && points > 0 && target.size() < MAX_ENTRIES)
                target.put(id, Math.min(MAX_POINTS, points));
        }
    }
}
