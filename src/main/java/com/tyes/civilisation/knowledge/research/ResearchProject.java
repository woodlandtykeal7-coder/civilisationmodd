package com.tyes.civilisation.knowledge.research;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

/** Persistent coordination shell; it grants no knowledge by timer or UI click. */
public final class ResearchProject {
    private static final int MAX_ATTEMPTS = 32;
    private final UUID id, settlementId;
    private final String readableId;
    private final ResourceLocation questionId, targetConcept;
    private final List<ResearchAttempt> attempts = new ArrayList<>();
    private ResearchState state = ResearchState.PROPOSED;
    private String blocker = "";

    public ResearchProject(UUID id, UUID settlementId, String readableId,
                           ResourceLocation questionId, ResourceLocation targetConcept) {
        this.id = id; this.settlementId = settlementId; this.readableId = readableId;
        this.questionId = questionId; this.targetConcept = targetConcept;
    }
    public UUID id() { return id; } public UUID settlementId() { return settlementId; }
    public String readableId() { return readableId; } public ResourceLocation questionId() { return questionId; }
    public ResourceLocation targetConcept() { return targetConcept; } public ResearchState state() { return state; }
    public String blocker() { return blocker; } public List<ResearchAttempt> attempts() { return List.copyOf(attempts); }
    public void setState(ResearchState state, String blocker) { this.state = state; this.blocker = blocker == null ? "" : blocker; }
    public boolean hasFailedApproach(String signature) { return attempts.stream().anyMatch(a -> a.approachSignature().equals(signature)
            && a.outcome() == ResearchOutcome.NEGATIVE); }
    public void addAttempt(ResearchAttempt attempt) { if (attempts.size() >= MAX_ATTEMPTS) attempts.removeFirst(); attempts.add(attempt); }
    public CompoundTag save() { CompoundTag tag = new CompoundTag(); tag.putUUID("id", id); tag.putUUID("settlement", settlementId);
        tag.putString("readable", readableId); tag.putString("question", questionId.toString());
        tag.putString("concept", targetConcept.toString()); tag.putString("state", state.name()); tag.putString("blocker", blocker);
        ListTag list = new ListTag(); attempts.forEach(a -> list.add(a.save())); tag.put("attempts", list); return tag; }
    public static ResearchProject load(CompoundTag tag) { ResearchProject result = new ResearchProject(tag.getUUID("id"),
            tag.getUUID("settlement"), tag.getString("readable"), ResourceLocation.parse(tag.getString("question")),
            ResourceLocation.parse(tag.getString("concept"))); try { result.state = ResearchState.valueOf(tag.getString("state")); }
        catch (IllegalArgumentException ignored) { result.state = ResearchState.BLOCKED; }
        result.blocker = tag.getString("blocker"); ListTag list = tag.getList("attempts", Tag.TAG_COMPOUND);
        for (int i = Math.max(0, list.size() - MAX_ATTEMPTS); i < list.size(); i++) result.attempts.add(ResearchAttempt.load(list.getCompound(i)));
        return result; }
}
