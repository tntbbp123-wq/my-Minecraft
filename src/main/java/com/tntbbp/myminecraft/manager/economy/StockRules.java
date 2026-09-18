package com.tntbbp.myminecraft.manager.economy;

import java.util.Locale;

/**
 * 주식 종목 정의·가격에 관한 순수 규칙(Bukkit 비의존, 단위 테스트 대상).
 * 게임 명령(/주식종류추가)과 웹 관리 통로가 같은 규칙을 쓴다.
 */
public final class StockRules {

    /** 종목 이름 최대 길이(코드 포인트). */
    public static final int MAX_NAME_LENGTH = 32;
    /** 업종 설명 최대 길이(코드 포인트). */
    public static final int MAX_BUSINESS_LENGTH = 100;
    /** 가격 직접 설정·거래 중지 사유 최대 길이. */
    public static final int MAX_REASON_LENGTH = 200;

    private StockRules() {
    }

    /** 이름으로 종목 id를 만든다: {@code custom_<소문자, 공백→_>}. */
    public static String idForName(String name) {
        return "custom_" + name.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", "_");
    }

    /**
     * 아이콘 팔레트 칸 번호. 예전 {@code addCustomStock}이 쓰던 {@code abs(id.hashCode()) % size}와 같은 값이고,
     * hashCode가 {@code Integer.MIN_VALUE}여도 음수가 되지 않는다.
     */
    public static int paletteIndex(String id, int size) {
        return Math.abs(id.hashCode() % size);
    }

    /** 이름 검사. 문제가 없으면 null, 있으면 사람이 읽는 메시지. */
    public static String validateName(String name) {
        if (name == null || name.isBlank()) {
            return "name 은 비어 있을 수 없습니다.";
        }
        String trimmed = name.strip();
        if (trimmed.codePointCount(0, trimmed.length()) > MAX_NAME_LENGTH) {
            return "name 은 " + MAX_NAME_LENGTH + "자 이하여야 합니다.";
        }
        if (hasControlChars(trimmed)) {
            return "name 에 줄바꿈 같은 제어문자를 쓸 수 없습니다.";
        }
        return null;
    }

    /** 업종 설명 검사(빈 값 허용). */
    public static String validateBusiness(String business) {
        if (business == null) {
            return null;
        }
        if (business.codePointCount(0, business.length()) > MAX_BUSINESS_LENGTH) {
            return "business 는 " + MAX_BUSINESS_LENGTH + "자 이하여야 합니다.";
        }
        if (hasControlChars(business)) {
            return "business 에 줄바꿈 같은 제어문자를 쓸 수 없습니다.";
        }
        return null;
    }

    /**
     * 종목 정의(가격 범위·변동 단위) 검사. 문제가 없으면 null.
     *
     * <ul>
     *   <li>관리자 추가 종목(custom): {@code min_price ≥ 0}, {@code max_price > min_price}, {@code change_unit > 0}.</li>
     *   <li>퍼센트 기반 종목: {@code min_price ≥ 0}, {@code max_price}는 0(상한 없음)이거나 {@code > min_price}.</li>
     * </ul>
     */
    public static String validateDefinition(boolean custom, double minPrice, double maxPrice, double changeUnit) {
        if (!Double.isFinite(minPrice) || !Double.isFinite(maxPrice) || !Double.isFinite(changeUnit)) {
            return "가격 범위와 변동 단위는 숫자여야 합니다.";
        }
        if (minPrice < 0) {
            return "min_price 는 0 이상이어야 합니다.";
        }
        if (custom) {
            if (maxPrice <= minPrice) {
                return "max_price 는 min_price 보다 커야 합니다.";
            }
            if (changeUnit <= 0) {
                return "change_unit 은 0보다 커야 합니다.";
            }
        } else if (maxPrice != 0 && maxPrice <= minPrice) {
            return "max_price 는 0(상한 없음)이거나 min_price 보다 커야 합니다.";
        }
        return null;
    }

    /** 가격을 범위 안으로 맞춘다. {@code maxPrice ≤ 0}이면 상한 없음. */
    public static double clamp(double price, double minPrice, double maxPrice) {
        double value = Math.max(minPrice, price);
        if (maxPrice > 0) {
            value = Math.min(maxPrice, value);
        }
        return value;
    }

    /** 소수 둘째 자리까지 반올림. */
    public static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    /**
     * {@code If-Match} 헤더에서 revision을 읽는다. {@code 7}, {@code "7"}, {@code W/"7"}를 받는다.
     * 없거나 정수가 아니면 null.
     */
    public static Integer parseRevision(String ifMatch) {
        if (ifMatch == null) {
            return null;
        }
        String value = ifMatch.strip();
        if (value.startsWith("W/") || value.startsWith("w/")) {
            value = value.substring(2).strip();
        }
        if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
            value = value.substring(1, value.length() - 1).strip();
        }
        if (value.isEmpty()) {
            return null;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 사유 검사: 필수면 1자 이상. 최대 {@value #MAX_REASON_LENGTH}자, 제어문자 금지. 문제 없으면 null. */
    public static String validateReason(String reason, boolean required) {
        if (reason == null || reason.isBlank()) {
            return required ? "reason 은 필수입니다 (1~" + MAX_REASON_LENGTH + "자)." : null;
        }
        String trimmed = reason.strip();
        if (trimmed.codePointCount(0, trimmed.length()) > MAX_REASON_LENGTH) {
            return "reason 은 " + MAX_REASON_LENGTH + "자 이하여야 합니다.";
        }
        if (hasControlChars(trimmed)) {
            return "reason 에 줄바꿈 같은 제어문자를 쓸 수 없습니다.";
        }
        return null;
    }

    static boolean hasControlChars(String value) {
        return value.codePoints().anyMatch(Character::isISOControl);
    }
}
