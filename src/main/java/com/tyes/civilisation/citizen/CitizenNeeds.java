package com.tyes.civilisation.citizen;

import net.minecraft.nbt.CompoundTag;

public final class CitizenNeeds {
    public static final double HUNGRY = 55.0D;
    public static final double CRITICAL_HUNGER = 78.0D;
    public static final double TIRED = 60.0D;
    public static final double CRITICAL_FATIGUE = 85.0D;

    private double hunger;
    private double fatigue;

    public CitizenNeeds() {
        this(25.0D, 20.0D);
    }

    public CitizenNeeds(double hunger, double fatigue) {
        this.hunger = clamp(hunger);
        this.fatigue = clamp(fatigue);
    }

    public double hunger() { return hunger; }
    public double fatigue() { return fatigue; }
    public boolean isHungry() { return hunger >= HUNGRY; }
    public boolean isCriticallyHungry() { return hunger >= CRITICAL_HUNGER; }
    public boolean isTired() { return fatigue >= TIRED; }
    public boolean isCriticallyTired() { return fatigue >= CRITICAL_FATIGUE; }

    public void tickAwake() {
        tickAwake(20);
    }

    public void tickAwake(int elapsedTicks) {
        double factor = Math.max(1, elapsedTicks) / 20.0D;
        hunger = clamp(hunger + 0.055D * factor);
        fatigue = clamp(fatigue + 0.045D * factor);
    }

    public void tickSleeping() {
        tickSleeping(20);
    }

    public void tickSleeping(int elapsedTicks) {
        double factor = Math.max(1, elapsedTicks) / 20.0D;
        hunger = clamp(hunger + 0.025D * factor);
        fatigue = clamp(fatigue - 0.42D * factor);
    }

    public void eat(int nutrition) {
        hunger = clamp(hunger - Math.max(1, nutrition) * 7.0D);
    }

    public void set(double hunger, double fatigue) {
        this.hunger = clamp(hunger);
        this.fatigue = clamp(fatigue);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putDouble("hunger", hunger);
        tag.putDouble("fatigue", fatigue);
        return tag;
    }

    public static CitizenNeeds load(CompoundTag tag) {
        if (tag.isEmpty()) return new CitizenNeeds();
        return new CitizenNeeds(tag.getDouble("hunger"), tag.getDouble("fatigue"));
    }

    private static double clamp(double value) {
        return Math.max(0.0D, Math.min(100.0D, value));
    }
}
