package com.tntbbp.myminecraft.model;

import java.util.UUID;

/**
 * 대상 하나에게 걸린 화염 지속 효과 상태.
 * 레바테인의 '업화'는 중첩될수록 초당 피해가 올라가고, '영원불멸의 화염'은 물로도 꺼지지 않는다.
 */
public class BurnState {

    private final UUID sourceId;
    private int remainingTicks;
    private double damagePerSecond;
    private int wetTicks;
    private int stacks;
    private final boolean eternal;

    public BurnState(UUID sourceId, int remainingTicks, double damagePerSecond) {
        this(sourceId, remainingTicks, damagePerSecond, 1, false);
    }

    public BurnState(UUID sourceId, int remainingTicks, double damagePerSecond, int stacks, boolean eternal) {
        this.sourceId = sourceId;
        this.remainingTicks = remainingTicks;
        this.damagePerSecond = damagePerSecond;
        this.stacks = stacks;
        this.eternal = eternal;
    }

    public UUID getSourceId() {
        return sourceId;
    }

    public int getRemainingTicks() {
        return remainingTicks;
    }

    public void setRemainingTicks(int remainingTicks) {
        this.remainingTicks = remainingTicks;
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

    public void setDamagePerSecond(double damagePerSecond) {
        this.damagePerSecond = damagePerSecond;
    }

    public int getStacks() {
        return stacks;
    }

    public void setStacks(int stacks) {
        this.stacks = stacks;
    }

    /** 영원불멸의 화염은 물에 들어가도 꺼지지 않는다. */
    public boolean isEternal() {
        return eternal;
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
