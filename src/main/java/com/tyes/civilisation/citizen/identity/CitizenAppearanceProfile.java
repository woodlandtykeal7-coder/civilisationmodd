package com.tyes.civilisation.citizen.identity;

import java.util.Objects;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

/**
 * Persisted, immutable visual identity for a citizen. The individual indices are deliberately
 * retained even while Phase 2 uses complete base skins so future render layers can consume them.
 */
public record CitizenAppearanceProfile(ResourceLocation profileId, int skinVariant, int hairVariant,
                                       int hairColour, int clothingVariant, int clothingColour) {
    public CitizenAppearanceProfile {
        Objects.requireNonNull(profileId, "profileId");
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("profileId", profileId.toString());
        tag.putInt("skinVariant", skinVariant);
        tag.putInt("hairVariant", hairVariant);
        tag.putInt("hairColour", hairColour);
        tag.putInt("clothingVariant", clothingVariant);
        tag.putInt("clothingColour", clothingColour);
        return tag;
    }

    public static CitizenAppearanceProfile load(CompoundTag tag) {
        ResourceLocation profileId = ResourceLocation.tryParse(tag.getString("profileId"));
        if (profileId == null) {
            throw new IllegalArgumentException("Citizen appearance is missing a valid profileId");
        }
        return new CitizenAppearanceProfile(profileId, tag.getInt("skinVariant"),
                tag.getInt("hairVariant"), tag.getInt("hairColour"),
                tag.getInt("clothingVariant"), tag.getInt("clothingColour"));
    }
}
