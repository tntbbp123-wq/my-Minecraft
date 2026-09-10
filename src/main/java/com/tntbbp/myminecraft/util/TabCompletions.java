package com.tntbbp.myminecraft.util;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/** 명령어 탭 자동완성에서 공통으로 쓰는 헬퍼. */
public final class TabCompletions {

    private TabCompletions() {
    }

    /** 입력한 접두어로 시작하는 후보만 걸러낸다 (대소문자 무시). */
    public static List<String> filterPrefix(List<String> options, String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        return options.stream()
                .filter(option -> option.toLowerCase(Locale.ROOT).startsWith(lower))
                .collect(Collectors.toList());
    }

    public static List<String> onlinePlayerNames() {
        return Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList());
    }
}
