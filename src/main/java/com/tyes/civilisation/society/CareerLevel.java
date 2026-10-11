package com.tyes.civilisation.society;

public enum CareerLevel {
    NOVICE,
    TRAINED,
    EXPERIENCED,
    MASTER;

    public static CareerLevel fromExperience(int experience) {
        if (experience >= 500) return MASTER;
        if (experience >= 200) return EXPERIENCED;
        if (experience >= 60) return TRAINED;
        return NOVICE;
    }
}
