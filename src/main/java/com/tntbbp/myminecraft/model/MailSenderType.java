package com.tntbbp.myminecraft.model;

/**
 * 우편을 보낸 곳. 웹/기록에 쓰는 값은 {@link #id()}(system|admin|quest).
 * 관리자 우편은 우편함 상한에서 빠지고 보관 기간이 길다(api-bridge.md §3).
 */
public enum MailSenderType {
    SYSTEM("system", "시스템"),
    ADMIN("admin", "운영자"),
    QUEST("quest", "퀘스트");

    private final String id;
    private final String label;

    MailSenderType(String id, String label) {
        this.id = id;
        this.label = label;
    }

    /** 저장·웹 응답용 값 (system|admin|quest). */
    public String id() {
        return id;
    }

    /** 보낸 사람 이름이 없을 때 쓰는 기본 표시 이름. */
    public String label() {
        return label;
    }

    public boolean isAdmin() {
        return this == ADMIN;
    }

    /** 저장된 값을 읽는다. 모르는 값이면 {@link #SYSTEM}. */
    public static MailSenderType fromId(String id) {
        if (id != null) {
            for (MailSenderType type : values()) {
                if (type.id.equalsIgnoreCase(id.strip())) {
                    return type;
                }
            }
        }
        return SYSTEM;
    }
}
