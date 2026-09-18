package com.tntbbp.myminecraft.manager.mail;

import java.util.Arrays;

/** 우편 계산용 순수 함수(Bukkit 비의존, 단위 테스트 대상). */
public final class MailRules {

    public static final long DAY_MS = 86_400_000L;

    private MailRules() {
    }

    /** 지금부터 {@code days}일 뒤(최소 1일). */
    public static long expiresAt(long now, int days) {
        return now + Math.max(1, days) * DAY_MS;
    }

    /**
     * 인벤토리에 한 번에 넣어 볼 수량. 남은 수량과 "칸 수 × 한 칸 최대 수량" 중 작은 값이라
     * 수량이 아무리 커도 만드는 아이템 묶음은 칸 수를 넘지 않는다.
     */
    public static int attemptCount(int remaining, int maxStack, int slots) {
        if (remaining <= 0) {
            return 0;
        }
        long capacity = (long) Math.max(1, maxStack) * Math.max(0, slots);
        return (int) Math.min(remaining, capacity);
    }

    /** {@code count}개를 한 칸 최대 {@code maxStack}개씩 나눈 칸별 수량 (예: 130, 64 → [64, 64, 2]). */
    public static int[] splitStacks(int count, int maxStack) {
        if (count <= 0) {
            return new int[0];
        }
        int size = Math.max(1, maxStack);
        int stacks = (int) (((long) count + size - 1) / size);
        int[] result = new int[stacks];
        Arrays.fill(result, size);
        int last = count - (stacks - 1) * size;
        result[stacks - 1] = last;
        return result;
    }

    /** 남은 보관 시간 표시 (예: "6일 23시간", "5시간 10분", "3분"). 이미 지났으면 "곧 만료". */
    public static String formatRemaining(long millis) {
        if (millis <= 0) {
            return "곧 만료";
        }
        long minutes = millis / 60_000L;
        long days = minutes / (60 * 24);
        long hours = (minutes / 60) % 24;
        long mins = minutes % 60;
        if (days > 0) {
            return hours > 0 ? days + "일 " + hours + "시간" : days + "일";
        }
        if (hours > 0) {
            return mins > 0 ? hours + "시간 " + mins + "분" : hours + "시간";
        }
        return Math.max(1, mins) + "분";
    }
}
