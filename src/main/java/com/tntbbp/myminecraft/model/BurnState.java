package com.tntbbp.myminecraft.model;

import java.util.UUID;

/** 대상 하나에게 걸린 '지옥의 화상' 지속 효과 상태. */
public class BurnState {

    private final UUID sourceId;
    private int remainingTicks;
    private final double damagePerSecond;
    private int wetTicks;

    public BurnState(UUID sourceId, int remainingTicks, double damagePerSecond) {
        this.sourceId = sourceId;
        this.remainingTicks = remainingTicks;
        this.damagePerSecond = damagePerSecond;
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

    public int getWetTicks() {
        return wetTicks;
    }

    public void addWetTicks(int amount) {
        wetTicks += amount;
    }

    public void resetWetTicks() {
        wetTicks = 0;
    }
}
