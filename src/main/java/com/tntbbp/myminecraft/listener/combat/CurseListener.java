package com.tntbbp.myminecraft.listener.combat;

import com.tntbbp.myminecraft.MyMinecraftPlugin;
import com.tntbbp.myminecraft.manager.combat.CurseManager;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.player.PlayerMoveEvent;

import java.util.List;

/**
 * {@link CurseManager}가 들고 있는 상태(저주/제압/면역)를 실제 게임 규칙에 반영한다.
 * 어떤 무기가 걸었든 상관없이 여기서 한 번에 처리한다.
 */
public class CurseListener implements Listener {

    private final MyMinecraftPlugin plugin;

    public CurseListener(MyMinecraftPlugin plugin) {
        this.plugin = plugin;
    }

    /** 저주에 걸린 대상은 방어력이 깎인 것처럼 피해를 더 받는다. */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onDamaged(EntityDamageByEntityEvent event) {
        double reducePercent = plugin.getCurseManager().defenseReducePercent(event.getEntity().getUniqueId());
        if (reducePercent <= 0) {
            return;
        }
        if (!event.isApplicable(EntityDamageEvent.DamageModifier.ARMOR)) {
            return;
        }
        double factor = 1.0 - Math.min(reducePercent, 100.0) / 100.0;
        event.setDamage(EntityDamageEvent.DamageModifier.ARMOR,
                event.getDamage(EntityDamageEvent.DamageModifier.ARMOR) * factor);
    }

    /** 고정 피해로 들어온 공격은 방어구·저항·보호 마법 감면을 전부 0으로 만든다. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTrueDamage(EntityDamageEvent event) {
        if (!plugin.getCurseManager().isTrueDamagePending(event.getEntity().getUniqueId())) {
            return;
        }
        for (EntityDamageEvent.DamageModifier modifier : List.of(
                EntityDamageEvent.DamageModifier.ARMOR,
                EntityDamageEvent.DamageModifier.RESISTANCE,
                EntityDamageEvent.DamageModifier.MAGIC,
                EntityDamageEvent.DamageModifier.BLOCKING)) {
            if (event.isApplicable(modifier)) {
                event.setDamage(modifier, 0.0);
            }
        }
    }

    /** 제압당한 대상은 남을 때릴 수 없다. */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onStunnedAttack(EntityDamageByEntityEvent event) {
        if (!plugin.getCurseManager().isStunned(event.getDamager().getUniqueId())) {
            return;
        }
        event.setCancelled(true);
        if (event.getDamager() instanceof Player player) {
            player.sendMessage(ChatColor.DARK_RED + "제압당해 공격할 수 없습니다.");
        }
    }

    /** 저주에 걸린 동안은 회복량이 깎인다 (100%면 아예 회복되지 않는다). */
    @EventHandler(ignoreCancelled = true)
    public void onRegainHealth(EntityRegainHealthEvent event) {
        if (!(event.getEntity() instanceof LivingEntity entity)) {
            return;
        }
        double reducePercent = plugin.getCurseManager().healReducePercent(entity.getUniqueId());
        if (reducePercent <= 0) {
            return;
        }
        if (reducePercent >= 100.0) {
            event.setCancelled(true);
            return;
        }
        event.setAmount(Math.max(0.0, event.getAmount() * (1.0 - reducePercent / 100.0)));
    }

    /** 제압당한 플레이어는 시선만 돌릴 수 있고 제자리를 벗어나지 못한다. */
    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        CurseManager curseManager = plugin.getCurseManager();
        if (!curseManager.isStunned(event.getPlayer().getUniqueId())) {
            return;
        }
        Location anchor = curseManager.stunAnchor(event.getPlayer().getUniqueId());
        if (anchor == null) {
            return;
        }
        Location to = event.getTo();
        if (to.getX() == anchor.getX() && to.getY() == anchor.getY() && to.getZ() == anchor.getZ()) {
            return;
        }
        Location locked = anchor.clone();
        locked.setYaw(to.getYaw());
        locked.setPitch(to.getPitch());
        event.setTo(locked);
    }

    /** 궁극기 시전 중(면역)에는 나쁜 포션 효과가 아예 걸리지 않는다. */
    @EventHandler(ignoreCancelled = true)
    public void onPotionEffect(EntityPotionEffectEvent event) {
        if (event.getAction() != EntityPotionEffectEvent.Action.ADDED
                && event.getAction() != EntityPotionEffectEvent.Action.CHANGED) {
            return;
        }
        if (event.getNewEffect() == null || !CurseManager.isHarmful(event.getNewEffect().getType())) {
            return;
        }
        if (plugin.getCurseManager().isImmune(event.getEntity().getUniqueId())) {
            event.setCancelled(true);
        }
    }
}
