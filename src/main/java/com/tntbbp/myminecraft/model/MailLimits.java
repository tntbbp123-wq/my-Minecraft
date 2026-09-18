package com.tntbbp.myminecraft.model;

/**
 * 우편함 상한·보관 기간 규칙 (config {@code mail.*}, 기획서 06 §4.6 + 관리자 우편 보완).
 *
 * <ul>
 *   <li>일반(시스템·퀘스트) 우편: 우편함에 받지 않은 <b>일반</b> 우편이 {@code regularMaxCount}통 이상이면
 *       새 우편은 보류. 관리자 우편은 이 수에 넣지 않는다(상한 제외).</li>
 *   <li>관리자 우편: 상한 제외({@code adminMaxCount <= 0}, 기본). 0보다 크면 받지 않은 우편 <b>전체</b> 수에 대한
 *       안전 상한으로 쓴다.</li>
 * </ul>
 *
 * @param regularMaxCount      일반 우편 상한 (0 이하 = 무제한)
 * @param adminMaxCount        관리자 우편 상한 (0 이하 = 무제한, 기본)
 * @param regularRetentionDays 일반 우편 보관 일수
 * @param adminRetentionDays   관리자 우편 보관 일수(요청에 기간이 없을 때)
 */
public record MailLimits(int regularMaxCount, int adminMaxCount, int regularRetentionDays, int adminRetentionDays) {

    public static final MailLimits DEFAULT = new MailLimits(50, 0, 7, 30);

    public MailLimits {
        regularRetentionDays = Math.max(1, regularRetentionDays);
        adminRetentionDays = Math.max(1, adminRetentionDays);
    }

    /** 이 종류 우편의 상한(0 이하 = 무제한). */
    public int maxCountFor(MailSenderType type) {
        return type != null && type.isAdmin() ? adminMaxCount : regularMaxCount;
    }

    /** 이 종류 우편의 기본 보관 일수. */
    public int retentionDaysFor(MailSenderType type) {
        return type != null && type.isAdmin() ? adminRetentionDays : regularRetentionDays;
    }

    /**
     * 이 종류 우편을 우편함에 바로 넣을 수 있는지.
     *
     * @param regularActive 받지 않은 일반(관리자 외) 우편 수
     * @param totalActive   받지 않은 우편 전체 수(관리자 우편 포함)
     */
    public boolean hasRoom(MailSenderType type, int regularActive, int totalActive) {
        if (type != null && type.isAdmin()) {
            return adminMaxCount <= 0 || totalActive < adminMaxCount;
        }
        return regularMaxCount <= 0 || regularActive < regularMaxCount;
    }
}
