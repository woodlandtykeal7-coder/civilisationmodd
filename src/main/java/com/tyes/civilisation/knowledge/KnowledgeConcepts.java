package com.tyes.civilisation.knowledge;

import com.tyes.civilisation.CivilisationMod;
import java.util.List;
import net.minecraft.resources.ResourceLocation;

/** Small Phase 6 catalogue. IDs remain stable while future datapack-backed definitions are added. */
public final class KnowledgeConcepts {
    public static final ResourceLocation BASIC_CARPENTRY = id("basic_carpentry");
    public static final ResourceLocation STONE_FOUNDATIONS = id("stone_foundations");
    public static final ResourceLocation BASIC_AGRICULTURE = id("basic_agriculture");
    public static final ResourceLocation BASIC_TOOLMAKING = id("basic_toolmaking");
    public static final ResourceLocation BASIC_SCAFFOLDING = id("basic_scaffolding");

    // Reserved hooks only; no Phase 6 behaviour unlocks these concepts.
    public static final ResourceLocation GOLEM_PRINCIPLES = id("golem_principles");
    public static final ResourceLocation IRRIGATION = id("irrigation");
    public static final ResourceLocation ELEVATED_JUNGLE_CONSTRUCTION = id("elevated_jungle_construction");

    public static final List<ResourceLocation> INITIAL = List.of(BASIC_CARPENTRY, STONE_FOUNDATIONS,
            BASIC_AGRICULTURE, BASIC_TOOLMAKING, BASIC_SCAFFOLDING);

    private KnowledgeConcepts() {}
    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(CivilisationMod.MOD_ID, path);
    }

    public static String displayName(ResourceLocation id) {
        String[] words = id.getPath().split("_");
        StringBuilder result = new StringBuilder();
        for (String word : words) {
            if (!result.isEmpty()) result.append(' ');
            result.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return result.toString();
    }
}
