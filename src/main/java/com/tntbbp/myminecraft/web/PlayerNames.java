package com.tntbbp.myminecraft.web;

import org.bukkit.Bukkit;

import java.util.UUID;

/** 통로 응답용 플레이어 이름 조회(서버에 기록된 이름만, 네트워크 조회 없음). 메인 스레드에서 호출. */
public final class PlayerNames {

    private PlayerNames() {
    }

    /** 서버가 아는 이름. 모르면 null. */
    public static String nameOf(UUID uuid) {
        if (uuid == null) {
            return null;
        }
        return Bukkit.getOfflinePlayer(uuid).getName();
    }

    /** 서버가 아는 이름, 모르면 UUID 문자열(문자열 필드가 null이 되면 안 되는 곳에서 사용). */
    public static String nameOrUuid(UUID uuid) {
        String name = nameOf(uuid);
        return name != null ? name : (uuid == null ? "" : uuid.toString());
    }
}
