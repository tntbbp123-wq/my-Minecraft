package com.tntbbp.myminecraft.listener;

import com.tntbbp.myminecraft.MyMinecraftPlugin;
import com.tntbbp.myminecraft.manager.JahaShingeomManager;
import com.tntbbp.myminecraft.util.OpImmunity;
import org.bukkit.ChatColor;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;

/** 자하신검의 패시브(더블 타격·파사자하)와 낙매성우 처리. */
public class JahaShingeomListener implements Listener {

    private final MyMinecraftPlugin plugin;

    public JahaShingeomListener(MyMinecraftPlugin plugin) {
        this.plugin = plugin;
    }

    /** F키 = 낙매성우 시전. */
    @EventHandler
    public void onSwapHands(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        if (!plugin.getJahaShingeomManager().isJahaShingeom(player.getInventory().getItemInMainHand())) {
            return;
        }
        event.setCancelled(true);
        armPlumRain(player);
    }

    /** 웅크리기 + 우클릭 = 낙매성우 시전 (F키와 같은 스킬, 같은 쿨다운). */
    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Player player = event.getPlayer();
        if (!player.isSneaking()
                || !plugin.getJahaShingeomManager().isJahaShingeom(player.getInventory().getItemInMainHand())) {
            return;
        }
        event.setCancelled(true);
        armPlumRain(player);
    }

    private void armPlumRain(Player player) {
        JahaShingeomManager manager = plugin.getJahaShingeomManager();
        if (plugin.getCurseManager().blockSkill(player)) {
            return;
        }
        if (manager.isArmed(player.getUniqueId())) {
            return;
        }

        long remaining = manager.remainingCooldownSeconds(player.getUniqueId());
        if (remaining > 0) {
            player.sendMessage(ChatColor.RED + "낙매성우 재사용 대기 중입니다. (" + remaining + "초)");
            return;
        }

        manager.armPlumRain(player);
        player.sendMessage(ChatColor.LIGHT_PURPLE + "낙매성우 준비! " + ChatColor.GRAY
                + manager.windowSeconds() + "초 안에 적을 타격하세요.");
    }

    /**
     * 평타 처리. 파사자하(감면 무시) → 낙매성우 7연타 → 더블 타격 → 추가 넉백 순으로 적용한다.
     * 추가 타격이 다시 이 핸들러를 부르는 것을 막기 위해 진행 중일 때는 그냥 빠져나간다.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMeleeDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker) || !(event.getEntity() instanceof LivingEntity target)) {
            return;
        }
        JahaShingeomManager manager = plugin.getJahaShingeomManager();
        if (!manager.isJahaShingeom(attacker.getInventory().getItemInMainHand())) {
            return;
        }
        if (manager.isExtraHitInProgress(attacker.getUniqueId())) {
            return;
        }

        // 파사자하: 저항 포션·보호 마법·방패 막기로 깎이는 몫을 전부 없앤다.
        for (EntityDamageEvent.DamageModifier modifier : java.util.List.of(
                EntityDamageEvent.DamageModifier.RESISTANCE,
                EntityDamageEvent.DamageModifier.MAGIC,
                EntityDamageEvent.DamageModifier.BLOCKING)) {
            if (event.isApplicable(modifier)) {
                event.setDamage(modifier, 0.0);
            }
        }

        if (OpImmunity.isImmune(target)) {
            return;
        }

        double baseDamage = event.getDamage(EntityDamageEvent.DamageModifier.BASE);
        if (manager.isArmed(attacker.getUniqueId())) {
            manager.triggerPlumRain(attacker, target, baseDamage);
        } else {
            manager.scheduleSecondHit(attacker, target, baseDamage);
        }
        manager.applyExtraKnockback(attacker, target);
    }
}
