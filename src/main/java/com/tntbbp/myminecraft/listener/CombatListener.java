package com.tntbbp.myminecraft.listener;

import com.tntbbp.myminecraft.MyMinecraftPlugin;
import com.tntbbp.myminecraft.manager.CombatManager;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * 플레이어 간 전투(PvP) 태그를 부여/해제하고, 태그된 동안 명령어 사용을 막으며,
 * 태그된 상태로 접속을 종료하면 전투 로그 방지를 위해 즉시 사망 처리한다.
 */
public class CombatListener implements Listener {

    private final MyMinecraftPlugin plugin;

    public CombatListener(MyMinecraftPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true)
    public void onPvpDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }

        Player attacker = null;
        if (event.getDamager() instanceof Player player) {
            attacker = player;
        } else if (event.getDamager() instanceof Projectile projectile
                && projectile.getShooter() instanceof Player shooter) {
            attacker = shooter;
        }

        if (attacker == null || attacker.equals(victim)) {
            return;
        }

        CombatManager combatManager = plugin.getCombatManager();
        combatManager.tag(victim.getUniqueId());
        combatManager.tag(attacker.getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (player.hasPermission("myminecraft.admin")) {
            return;
        }

        CombatManager combatManager = plugin.getCombatManager();
        if (!combatManager.isTagged(player.getUniqueId())) {
            return;
        }

        event.setCancelled(true);
        player.sendMessage(ChatColor.RED + "전투 중에는 명령어를 사용할 수 없습니다. ("
                + combatManager.remainingSeconds(player.getUniqueId()) + "초 후 가능)");
    }

    @EventHandler
    public void onDeath(EntityDeathEvent event) {
        if (event.getEntity() instanceof Player player) {
            plugin.getCombatManager().untag(player.getUniqueId());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        CombatManager combatManager = plugin.getCombatManager();
        if (!combatManager.isTagged(player.getUniqueId())) {
            return;
        }

        combatManager.untag(player.getUniqueId());
        if (player.isOnline() && player.getHealth() > 0) {
            player.setHealth(0.0);
        }
    }
}
