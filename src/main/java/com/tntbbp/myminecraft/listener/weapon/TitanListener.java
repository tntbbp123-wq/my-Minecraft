package com.tntbbp.myminecraft.listener.weapon;

import com.tntbbp.myminecraft.MyMinecraftPlugin;
import com.tntbbp.myminecraft.manager.weapon.TitanManager;
import com.tntbbp.myminecraft.util.OpImmunity;
import org.bukkit.ChatColor;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;

/** 타이탄의 갑옷 분쇄 패시브와 [F] 지진타 / [웅크리기+F] 타이탄 크래시. */
public class TitanListener implements Listener {

    private final MyMinecraftPlugin plugin;

    public TitanListener(MyMinecraftPlugin plugin) {
        this.plugin = plugin;
    }

    /** F = 지진타, 웅크리기+F = 타이탄 크래시. */
    @EventHandler
    public void onSwapHands(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        TitanManager manager = plugin.getTitanManager();
        if (!manager.isTitan(player.getInventory().getItemInMainHand())) {
            return;
        }

        event.setCancelled(true);
        if (plugin.getCurseManager().blockSkill(player)) {
            return;
        }

        if (player.isSneaking()) {
            if (manager.useCrash(player)) {
                player.sendMessage(ChatColor.GOLD + "타이탄 크래시! " + ChatColor.GRAY + "대지가 갈라집니다.");
            } else {
                player.sendMessage(ChatColor.RED + "타이탄 크래시 재사용 대기 중입니다. ("
                        + manager.remainingCrashCooldown(player.getUniqueId()) + "초)");
            }
            return;
        }

        if (manager.useQuake(player)) {
            player.sendMessage(ChatColor.GOLD + "지진타!");
        } else {
            player.sendMessage(ChatColor.RED + "지진타 재사용 대기 중입니다. ("
                    + manager.remainingQuakeCooldown(player.getUniqueId()) + "초)");
        }
    }

    /**
     * 갑옷 분쇄: 방어력과 방패 가드를 일정 비율 무시하고, 치명타 피해를 설정값으로 고정한다.
     *
     * <p>바닐라 치명타는 1.5배라 아이템 설명에 적은 수치와 어긋난다. 설명이 곧 약속이므로
     * 치명타로 들어갈 때는 적힌 값이 그대로 나오게 맞춘다.
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onMeleeDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker) || !(event.getEntity() instanceof LivingEntity target)) {
            return;
        }
        TitanManager manager = plugin.getTitanManager();
        if (!manager.isTitan(attacker.getInventory().getItemInMainHand())) {
            return;
        }

        if (event.isCritical()) {
            event.setDamage(manager.critDamage());
        }
        if (OpImmunity.isImmune(target)) {
            return;
        }

        double keep = 1.0 - Math.min(100.0, manager.armorIgnorePercent()) / 100.0;
        if (event.isApplicable(EntityDamageEvent.DamageModifier.ARMOR)) {
            event.setDamage(EntityDamageEvent.DamageModifier.ARMOR,
                    event.getDamage(EntityDamageEvent.DamageModifier.ARMOR) * keep);
        }
        if (event.isApplicable(EntityDamageEvent.DamageModifier.BLOCKING)) {
            event.setDamage(EntityDamageEvent.DamageModifier.BLOCKING,
                    event.getDamage(EntityDamageEvent.DamageModifier.BLOCKING) * keep);
        }
        manager.applyCrushSlow(target);
    }
}
