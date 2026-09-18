package com.tntbbp.myminecraft.util;

import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.command.RemoteConsoleCommandSender;
import org.bukkit.entity.Player;

/** 명령을 쓴 사람을 기록(작성자·관리 기록 actor)에 남길 이름으로 바꾼다. */
public final class CommandActors {

    private CommandActors() {
    }

    /**
     * 플레이어면 이름, 서버 콘솔이면 {@code console}, RCON이면 {@code rcon}, 웹 관리 통로의 명령 실행
     * ({@code Bukkit.createCommandSender}로 만든 sender)이면 {@code web}, 그 밖에는 보낸 쪽 이름.
     * 웹에서 누가 실행했는지는 통로가 남기는 {@code command_execute} 기록에 있다.
     */
    public static String actorOf(CommandSender sender) {
        if (sender instanceof Player player) {
            return player.getName();
        }
        if (sender instanceof ConsoleCommandSender) {
            return "console";
        }
        if (sender instanceof RemoteConsoleCommandSender) {
            return "rcon";
        }
        if (sender.getClass().getSimpleName().equals("FeedbackForwardingSender")) {
            return "web";
        }
        String name = sender.getName();
        return name == null || name.isBlank() ? "unknown" : name;
    }
}
