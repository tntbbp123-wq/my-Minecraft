package com.tntbbp.myminecraft.util;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;

/**
 * 무기 스킬들이 공통으로 쓰는 대상 탐색 헬퍼.
 * 부채꼴(원뿔) 범위와 직선 관통 범위 두 가지를 제공한다.
 */
public final class SkillTargets {

    private SkillTargets() {
    }

    /** 시전자 눈높이 기준 전방 부채꼴 안에 있는 모든 살아있는 대상. 가까운 순으로 정렬된다. */
    public static List<LivingEntity> inCone(World world, Location eye, Vector direction, double range,
                                            double coneAngleDegrees, Entity exclude) {
        List<LivingEntity> result = new ArrayList<>();
        double halfAngleCos = Math.cos(Math.toRadians(coneAngleDegrees / 2.0));
        for (Entity entity : world.getNearbyEntities(eye, range, range, range)) {
            if (!(entity instanceof LivingEntity target) || entity.equals(exclude)) {
                continue;
            }
            Vector toTarget = center(target).subtract(eye.toVector());
            double distance = toTarget.length();
            if (distance < 0.001 || distance > range) {
                continue;
            }
            if (direction.dot(toTarget.normalize()) < halfAngleCos) {
                continue;
            }
            result.add(target);
        }
        result.sort((a, b) -> Double.compare(
                a.getLocation().distanceSquared(eye), b.getLocation().distanceSquared(eye)));
        return result;
    }

    /** 전방 직선(굵기 halfWidth)을 관통하는 경로에 걸린 모든 대상. 가까운 순으로 정렬된다. */
    public static List<LivingEntity> inLine(World world, Location eye, Vector direction, double range,
                                            double halfWidth, Entity exclude) {
        List<LivingEntity> result = new ArrayList<>();
        Vector unit = direction.clone().normalize();
        for (Entity entity : world.getNearbyEntities(eye, range, range, range)) {
            if (!(entity instanceof LivingEntity target) || entity.equals(exclude)) {
                continue;
            }
            Vector toTarget = center(target).subtract(eye.toVector());
            double forward = unit.dot(toTarget);
            if (forward < 0 || forward > range) {
                continue;
            }
            // 진행 방향 성분을 뺀 나머지가 직선에서 벗어난 거리다.
            double lateral = toTarget.clone().subtract(unit.clone().multiply(forward)).length();
            if (lateral > halfWidth + target.getWidth() / 2.0) {
                continue;
            }
            result.add(target);
        }
        result.sort((a, b) -> Double.compare(
                a.getLocation().distanceSquared(eye), b.getLocation().distanceSquared(eye)));
        return result;
    }

    /** 엔티티의 몸통 중심 좌표 (발밑이 아니라 키의 절반 높이). */
    public static Vector center(LivingEntity entity) {
        return entity.getLocation().toVector().add(new Vector(0, entity.getHeight() / 2.0, 0));
    }
}
