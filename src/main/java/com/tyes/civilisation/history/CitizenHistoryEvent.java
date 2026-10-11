package com.tyes.civilisation.history;

import net.minecraft.nbt.CompoundTag;

public record CitizenHistoryEvent(long gameTick, String type, String description) {
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putLong("tick", gameTick);
        tag.putString("type", type);
        tag.putString("description", description);
        return tag;
    }

    public static CitizenHistoryEvent load(CompoundTag tag) {
        return new CitizenHistoryEvent(tag.getLong("tick"), tag.getString("type"),
                tag.getString("description"));
    }
}
