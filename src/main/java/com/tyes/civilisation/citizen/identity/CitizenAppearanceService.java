package com.tyes.civilisation.citizen.identity;

import com.tyes.civilisation.CivilisationMod;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;

/** Single source of truth for initial appearance assignment and immutable profile definitions. */
public final class CitizenAppearanceService {
    /**
     * Phase 2 uses Mojang's nine wide-arm default human skins. The stable Civilisation IDs keep
     * save data independent of texture paths and allow each definition to become layered later.
     */
    private static final List<Definition> DEFINITIONS = List.of(
            definition(1, "ari", 4, 2, 3, 1, 5),
            definition(2, "alex", 1, 1, 1, 2, 2),
            definition(3, "efe", 6, 4, 4, 3, 7),
            definition(4, "kai", 5, 3, 2, 1, 1),
            definition(5, "steve", 2, 2, 2, 2, 4),
            definition(6, "makena", 7, 5, 5, 3, 6),
            definition(7, "noor", 3, 4, 1, 1, 3),
            definition(8, "sunny", 0, 0, 0, 2, 0),
            definition(9, "zuri", 7, 3, 5, 3, 8));

    private CitizenAppearanceService() {
    }

    /** Generates a profile once. Callers must persist the returned value on the CitizenRecord. */
    public static CitizenAppearanceProfile generate(UUID citizenId, long appearanceSeed) {
        long mixed = mix64(appearanceSeed ^ citizenId.getMostSignificantBits()
                ^ Long.rotateLeft(citizenId.getLeastSignificantBits(), 23));
        Definition definition = DEFINITIONS.get(Math.floorMod(mixed, DEFINITIONS.size()));
        return definition.profile();
    }

    public static Optional<Definition> find(ResourceLocation profileId) {
        return DEFINITIONS.stream().filter(definition -> definition.profile().profileId().equals(profileId)).findFirst();
    }

    public static Definition byNetworkId(int networkId) {
        return networkId >= 0 && networkId < DEFINITIONS.size() ? DEFINITIONS.get(networkId) : DEFINITIONS.get(0);
    }

    public static int networkId(CitizenAppearanceProfile profile) {
        for (int i = 0; i < DEFINITIONS.size(); i++) {
            if (DEFINITIONS.get(i).profile().profileId().equals(profile.profileId())) return i;
        }
        return 0;
    }

    public static boolean isKnown(CitizenAppearanceProfile profile) {
        return find(profile.profileId()).isPresent();
    }

    public static int variantCount() {
        return DEFINITIONS.size();
    }

    private static Definition definition(int number, String vanillaSkin, int skin, int hair,
                                         int hairColour, int clothing, int clothingColour) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(
                CivilisationMod.MOD_ID, "civilian_%02d".formatted(number));
        ResourceLocation texture = ResourceLocation.withDefaultNamespace(
                "textures/entity/player/wide/" + vanillaSkin + ".png");
        return new Definition(new CitizenAppearanceProfile(id, skin, hair, hairColour,
                clothing, clothingColour), texture);
    }

    private static long mix64(long value) {
        value = (value ^ (value >>> 30)) * 0xbf58476d1ce4e5b9L;
        value = (value ^ (value >>> 27)) * 0x94d049bb133111ebL;
        return value ^ (value >>> 31);
    }

    /** Immutable resource-backed definition; more texture layers can be added without changing saves. */
    public record Definition(CitizenAppearanceProfile profile, ResourceLocation baseTexture) {
    }
}
