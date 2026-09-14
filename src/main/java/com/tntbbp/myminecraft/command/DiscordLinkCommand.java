package com.tntbbp.myminecraft.command;

import com.tntbbp.myminecraft.MyMinecraftPlugin;
import com.tntbbp.myminecraft.manager.DiscordLinkManager;
import com.tntbbp.myminecraft.manager.DiscordManager;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * /디스코드연동 <디스코드ID> — 인증 코드를 해당 디스코드 계정으로 DM 발송.
 * /디스코드연동확인 <코드> — 받은 코드를 입력해 연동을 완료한다.
 */
public class DiscordLinkCommand implements CommandExecutor {

    private final MyMinecraftPlugin plugin;

    public DiscordLinkCommand(MyMinecraftPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "플레이어만 사용할 수 있는 명령어입니다.");
            return true;
        }

        DiscordManager discordManager = plugin.getDiscordManager();
        if (!discordManager.isEnabled()) {
            player.sendMessage(ChatColor.RED + "디스코드 봇이 설정되어 있지 않아 이 기능을 사용할 수 없습니다.");
            return true;
        }

        DiscordLinkManager linkManager = plugin.getDiscordLinkManager();

        if (label.equalsIgnoreCase("디스코드연동")) {
            if (args.length < 1) {
                player.sendMessage(ChatColor.YELLOW + "사용법: /디스코드연동 <디스코드 사용자 ID>");
                return true;
            }
            String discordId = args[0];
            String code = linkManager.startVerification(player.getUniqueId(), discordId);
            discordManager.sendDirectMessage(discordId,
                    "MyMinecraft 서버 연동 인증 코드: " + code + " (게임 내에서 /디스코드연동확인 " + code + " 입력, "
                            + linkManager.verificationExpirySeconds() + "초 이내)",
                    () -> player.sendMessage(ChatColor.RED
                            + "DM 전송에 실패했습니다. 디스코드 ID를 확인하거나, 봇과 같은 서버에 있는지 확인해주세요."));
            player.sendMessage(ChatColor.YELLOW + "인증 코드를 디스코드 DM으로 보냈습니다. "
                    + "받은 코드를 /디스코드연동확인 <코드> 로 입력해주세요.");
            return true;
        }

        if (label.equalsIgnoreCase("디스코드연동확인")) {
            if (args.length < 1) {
                player.sendMessage(ChatColor.YELLOW + "사용법: /디스코드연동확인 <코드>");
                return true;
            }
            DiscordLinkManager.ConfirmResult result = linkManager.confirm(player.getUniqueId(), args[0]);
            switch (result) {
                case SUCCESS -> player.sendMessage(ChatColor.GREEN + "디스코드 계정 연동이 완료되었습니다!");
                case NO_PENDING -> player.sendMessage(ChatColor.RED + "먼저 /디스코드연동 <디스코드ID>로 인증 코드를 받아주세요.");
                case EXPIRED -> player.sendMessage(ChatColor.RED + "인증 코드가 만료되었습니다. 다시 시도해주세요.");
                case WRONG_CODE -> player.sendMessage(ChatColor.RED + "코드가 일치하지 않습니다.");
            }
            return true;
        }

        return false;
    }
}
