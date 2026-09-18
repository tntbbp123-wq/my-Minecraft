package com.tntbbp.myminecraft.listener.mail;

import com.tntbbp.myminecraft.MyMinecraftPlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

/** 접속하면 잠시 뒤(접속 메시지에 묻히지 않게) "새 우편 N통" 알림을 띄운다. */
public class MailJoinListener implements Listener {

    private static final long NOTIFY_DELAY_TICKS = 40L;

    private final MyMinecraftPlugin plugin;

    public MailJoinListener(MyMinecraftPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                plugin.getMailManager().notifyOnJoin(player);
            }
        }, NOTIFY_DELAY_TICKS);
    }
}
