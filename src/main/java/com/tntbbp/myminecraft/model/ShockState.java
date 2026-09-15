package com.tntbbp.myminecraft.model;

import java.util.UUID;

/** 대상 하나에게 걸린 '감전(드라켄피어스)' 지속 효과 상태. */
public class ShockState {

    private final UUID sourceId;
    private int remainingTicks;
    private final double damagePerSecond;
    private final int slowAmplifier;

    public ShockState(UUID sourceId, int remainingTicks, double damagePerSecond, int slowAmplifier) {
        this.sourceId = sourceId;
        this.remainingTicks = remainingTicks;
        this.damagePerSecond = damagePerSecond;
        this.slowAmplifier = slowAmplifier;
    }

    public UUID getSourceId() {
        return sourceId;
    }

    public int getRemainingTicks() {
        return remainingTicks;
    }

    public void reduceTicks(int amount) {
        remainingTicks -= amount;
    }

    public boolean isExpired() {
        return remainingTicks <= 0;
    }

    public double getDamagePerSecond() {
        return damagePerSecond;
    }

    public int getSlowAmplifier() {
        return slowAmplifier;
    }
}
