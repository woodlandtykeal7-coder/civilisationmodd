package com.tyes.civilisation.brain;

public final class CitizenSchedule {
    private CitizenSchedule() {
    }

    public static boolean isWorkTime(long dayTime, long citizenJitter) {
        long time = Math.floorMod(dayTime + citizenJitter, 24000L);
        return time >= 1000L && time < 11500L;
    }

    public static boolean isSleepTime(long dayTime, long citizenJitter) {
        long time = Math.floorMod(dayTime + citizenJitter, 24000L);
        return time >= 13000L || time < 500L;
    }
}
