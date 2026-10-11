package com.tyes.civilisation.society;

import com.tyes.civilisation.settlement.WorkRole;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;

/** Persistent bounded demographic, education and career state for one citizen. */
public final class CitizenDevelopment {
    public static final long CHILD_DURATION = 7L * 24000L;
    public static final long ADOLESCENT_DURATION = 5L * 24000L;
    public static final long ADULT_DURATION = 60L * 24000L;

    private LifeStage lifeStage;
    private long birthTick;
    private long nextTransitionTick;
    @Nullable private UUID householdId;
    private WorkRole career;
    private int education;
    private final EnumMap<WorkRole, Integer> experience = new EnumMap<>(WorkRole.class);

    public CitizenDevelopment() {
        this(LifeStage.ADULT, 0L, -1L, null, WorkRole.GENERAL_LABOURER, 0);
    }

    private CitizenDevelopment(LifeStage lifeStage, long birthTick, long nextTransitionTick,
                               @Nullable UUID householdId, WorkRole career, int education) {
        this.lifeStage = lifeStage;
        this.birthTick = birthTick;
        this.nextTransitionTick = nextTransitionTick;
        this.householdId = householdId;
        this.career = career;
        this.education = Math.max(0, education);
    }

    public static CitizenDevelopment child(long now) {
        return new CitizenDevelopment(LifeStage.CHILD, now, now + CHILD_DURATION, null,
                WorkRole.GENERAL_LABOURER, 0);
    }

    public LifeStage lifeStage() { return lifeStage; }
    public long birthTick() { return birthTick; }
    public long nextTransitionTick() { return nextTransitionTick; }
    public Optional<UUID> householdId() { return Optional.ofNullable(householdId); }
    public WorkRole career() { return career; }
    public int education() { return education; }
    public Map<WorkRole, Integer> experience() { return Map.copyOf(experience); }
    public int experience(WorkRole role) { return experience.getOrDefault(role, 0); }
    public CareerLevel careerLevel() { return CareerLevel.fromExperience(experience(career)); }

    public void setHousehold(@Nullable UUID householdId) { this.householdId = householdId; }
    public void setCareer(WorkRole career) { this.career = career; }
    public void addEducation(int points) { education = Math.max(0, education + points); }
    public void addExperience(WorkRole role, int points) {
        if (points > 0) experience.merge(role, points, Integer::sum);
    }

    public boolean advanceIfDue(long now) {
        if (nextTransitionTick < 0L || now < nextTransitionTick || lifeStage == LifeStage.ELDER) return false;
        switch (lifeStage) {
            case CHILD -> {
                lifeStage = LifeStage.ADOLESCENT;
                nextTransitionTick = now + ADOLESCENT_DURATION;
            }
            case ADOLESCENT -> {
                lifeStage = LifeStage.ADULT;
                nextTransitionTick = now + ADULT_DURATION;
            }
            case ADULT -> {
                lifeStage = LifeStage.ELDER;
                nextTransitionTick = -1L;
            }
            case ELDER -> nextTransitionTick = -1L;
        }
        return true;
    }

    /** Debug/test transition preserving scheduled production behaviour in normal play. */
    public void setStage(LifeStage stage, long now) {
        lifeStage = stage;
        nextTransitionTick = switch (stage) {
            case CHILD -> now + CHILD_DURATION;
            case ADOLESCENT -> now + ADOLESCENT_DURATION;
            case ADULT -> now + ADULT_DURATION;
            case ELDER -> -1L;
        };
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("stage", lifeStage.name());
        tag.putLong("birthTick", birthTick);
        tag.putLong("nextTransitionTick", nextTransitionTick);
        if (householdId != null) tag.putUUID("household", householdId);
        tag.putString("career", career.name());
        tag.putInt("education", education);
        CompoundTag skills = new CompoundTag();
        experience.forEach((role, score) -> skills.putInt(role.name(), score));
        tag.put("experience", skills);
        return tag;
    }

    public static CitizenDevelopment load(CompoundTag tag) {
        LifeStage stage;
        try { stage = LifeStage.valueOf(tag.getString("stage")); }
        catch (IllegalArgumentException ignored) { stage = LifeStage.ADULT; }
        CitizenDevelopment result = new CitizenDevelopment(stage, tag.getLong("birthTick"),
                tag.contains("nextTransitionTick") ? tag.getLong("nextTransitionTick") : -1L,
                tag.hasUUID("household") ? tag.getUUID("household") : null,
                WorkRole.parse(tag.getString("career")), tag.getInt("education"));
        CompoundTag skills = tag.getCompound("experience");
        for (WorkRole role : WorkRole.values()) {
            int score = skills.getInt(role.name());
            if (score > 0) result.experience.put(role, score);
        }
        return result;
    }
}
