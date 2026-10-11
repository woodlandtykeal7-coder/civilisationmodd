package com.tyes.civilisation.society;

import com.tyes.civilisation.settlement.WorkRole;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;

public final class ApprenticeshipRecord {
    private final UUID apprenticeshipId;
    private final UUID settlementId;
    private final UUID apprenticeId;
    private final WorkRole focus;
    private final long createdTick;
    @Nullable private UUID mentorId;
    private ApprenticeshipState state;
    private int experience;

    public ApprenticeshipRecord(UUID apprenticeshipId, UUID settlementId, UUID apprenticeId,
                                UUID mentorId, WorkRole focus, long createdTick) {
        this.apprenticeshipId = apprenticeshipId;
        this.settlementId = settlementId;
        this.apprenticeId = apprenticeId;
        this.mentorId = mentorId;
        this.focus = focus;
        this.createdTick = createdTick;
        this.state = ApprenticeshipState.ACTIVE;
    }

    public UUID apprenticeshipId() { return apprenticeshipId; }
    public UUID settlementId() { return settlementId; }
    public UUID apprenticeId() { return apprenticeId; }
    public Optional<UUID> mentorId() { return Optional.ofNullable(mentorId); }
    public WorkRole focus() { return focus; }
    public long createdTick() { return createdTick; }
    public ApprenticeshipState state() { return state; }
    public int experience() { return experience; }
    public void addExperience(int amount) { experience = Math.max(0, experience + amount); }
    public void pause() { mentorId = null; state = ApprenticeshipState.PAUSED_NO_MENTOR; }
    public void assignMentor(UUID mentorId) { this.mentorId = mentorId; state = ApprenticeshipState.ACTIVE; }
    public void complete() { state = ApprenticeshipState.COMPLETE; }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("id", apprenticeshipId);
        tag.putUUID("settlement", settlementId);
        tag.putUUID("apprentice", apprenticeId);
        if (mentorId != null) tag.putUUID("mentor", mentorId);
        tag.putString("focus", focus.name());
        tag.putLong("created", createdTick);
        tag.putString("state", state.name());
        tag.putInt("experience", experience);
        return tag;
    }

    public static ApprenticeshipRecord load(CompoundTag tag) {
        ApprenticeshipRecord result = new ApprenticeshipRecord(tag.getUUID("id"), tag.getUUID("settlement"),
                tag.getUUID("apprentice"), tag.hasUUID("mentor") ? tag.getUUID("mentor") : null,
                WorkRole.parse(tag.getString("focus")), tag.getLong("created"));
        try { result.state = ApprenticeshipState.valueOf(tag.getString("state")); }
        catch (IllegalArgumentException ignored) { result.state = ApprenticeshipState.PAUSED_NO_MENTOR; }
        result.experience = Math.max(0, tag.getInt("experience"));
        return result;
    }
}
