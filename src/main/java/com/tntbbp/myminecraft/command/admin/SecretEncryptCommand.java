package com.tntbbp.myminecraft.command.admin;

import com.tntbbp.myminecraft.MyMinecraftPlugin;
import com.tntbbp.myminecraft.util.SecretResolver;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import java.util.Map;

/**
 * config.yml에 평문으로 적어둔 디스코드 봇 토큰을 enc: 형식으로 바꿔준다.
 *
 * <p>토큰을 명령어 인자로 받지 않는 이유는, 채팅으로 입력하면 그 자체가 채팅 로그와 콘솔에
 * 평문으로 남기 때문이다. 대신 이미 config.yml에 적혀 있는 값을 읽어서 변환 결과만 보여주고,
 * 관리자가 그 결과를 config.yml에 직접 붙여넣도록 한다 (config의 주석을 보존하기 위해서도
 * 플러그인이 파일을 직접 덮어쓰지 않는다).
 */
public class SecretEncryptCommand implements CommandExecutor {

    /** 감출 수 있는 설정 키 목록. 값이 아니라 "어느 항목인지"만 인자로 받는다. */
    private static final Map<String, String> TARGETS = Map.of(
            "discord", "discord.bot-token",
            "ai", "ai.api-key");

    private final MyMinecraftPlugin plugin;

    public SecretEncryptCommand(MyMinecraftPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("myminecraft.admin")) {
            sender.sendMessage(ChatColor.RED + "권한이 없습니다.");
            return true;
        }

        String target = args.length > 0 ? args[0].toLowerCase() : "discord";
        String path = TARGETS.get(target);
        if (path == null) {
            sender.sendMessage(ChatColor.RED + "알 수 없는 항목입니다. 사용 가능: "
                    + String.join(", ", TARGETS.keySet()));
            sender.sendMessage(ChatColor.GRAY + "사용법: /" + label + " <discord|ai>");
            sender.sendMessage(ChatColor.GRAY + "값을 직접 입력하지 마세요 — 채팅 로그와 콘솔에 평문으로 남습니다.");
            return true;
        }

        SecretResolver resolver = plugin.getSecretResolver();
        String raw = plugin.getConfig().getString(path, "");

        if (raw == null || raw.isBlank()) {
            sender.sendMessage(ChatColor.RED + "config.yml의 " + path + " 가 비어있습니다.");
            sender.sendMessage(ChatColor.GRAY + "먼저 값을 적고 서버를 재시작한 뒤 다시 시도하세요.");
            return true;
        }
        if (resolver.isManaged(raw)) {
            sender.sendMessage(ChatColor.YELLOW + "이미 감춰진 형식(env:/file:/enc:)으로 설정되어 있습니다. 변환할 필요가 없습니다.");
            return true;
        }

        String encrypted;
        try {
            encrypted = resolver.encrypt(raw);
        } catch (Exception e) {
            sender.sendMessage(ChatColor.RED + "암호화에 실패했습니다: " + e.getMessage());
            return true;
        }

        sender.sendMessage(ChatColor.GREEN + "아래 값을 config.yml의 " + path + " 에 그대로 붙여넣고 서버를 재시작하세요.");
        sender.sendMessage(ChatColor.WHITE + encrypted);
        sender.sendMessage(ChatColor.GRAY + "복호화에는 plugins/MyMinecraft/secret.key 가 필요합니다. "
                + "이 파일이 함께 유출되면 암호화한 의미가 없으니 백업/공유 시 주의하세요.");
        sender.sendMessage(ChatColor.GRAY + "파일에 아예 남기지 않으려면 env:환경변수이름 형식을 쓰는 편이 더 안전합니다.");
        return true;
    }
}
