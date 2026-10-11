package com.tyes.civilisation.knowledge.research;

import net.minecraft.nbt.CompoundTag;

/** One bounded experiment result. Negative signatures prevent identical blind retries later. */
public record ResearchAttempt(long tick, String approachSignature, ResearchOutcome outcome, String notes) {
    public CompoundTag save() { CompoundTag tag = new CompoundTag(); tag.putLong("tick", tick);
        tag.putString("approach", approachSignature); tag.putString("outcome", outcome.name());
        tag.putString("notes", notes); return tag; }
    public static ResearchAttempt load(CompoundTag tag) {
        ResearchOutcome outcome; try { outcome = ResearchOutcome.valueOf(tag.getString("outcome")); }
        catch (IllegalArgumentException ignored) { outcome = ResearchOutcome.INCONCLUSIVE; }
        return new ResearchAttempt(tag.getLong("tick"), tag.getString("approach"), outcome, tag.getString("notes"));
    }
}
