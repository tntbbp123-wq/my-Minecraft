package com.tntbbp.myminecraft.listener;

import com.tntbbp.myminecraft.MyMinecraftPlugin;
import com.tntbbp.myminecraft.manager.BalmungManager;
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

/** 발뭉의 패시브(용살의 긍지)와 [F] 그람의 참격 / [웅크리기+우클릭] 발뭉의 종말 처리. */
public class BalmungListener implements Listener {

    private final MyMinecraftPlugin plugin;

    public BalmungListener(MyMinecraftPlugin plugin) {
        this.plugin = plugin;
    }

    /** F키(기본 손 바꾸기 키) = 그람의 참격. */
    @EventHandler
    public void onSwapHands(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        BalmungManager manager = plugin.getBalmungManager();
        if (!manager.isBalmung(player.getInventory().getItemInMainHand())) {
            return;
        }

        event.setCancelled(true);
        if (plugin.getCurseManager().blockSkill(player)) {
            return;
        }

        long remaining = manager.remainingGramCooldown(player.getUniqueId());
        if (remaining > 0) {
            player.sendMessage(ChatColor.RED + "그람의 참격 재사용 대기 중입니다. (" + remaining + "초)");
            return;
        }

        manager.useGramSlash(player);
        player.sendMessage(ChatColor.WHITE + "그람의 참격! " + ChatColor.GRAY + "파프니르의 저주를 남깁니다.");
    }

    /** 웅크리기 + 우클릭 = 발뭉의 종말. */
    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Player player = event.getPlayer();
        if (!player.isSneaking()) {
            return;
        }
        BalmungManager manager = plugin.getBalmungManager();
        if (!manager.isBalmung(player.getInventory().getItemInMainHand())) {
            return;
        }

        event.setCancelled(true);
        if (plugin.getCurseManager().blockSkill(player)) {
            return;
        }

        long remaining = manager.remainingDoomCooldown(player.getUniqueId());
        if (remaining > 0) {
            player.sendMessage(ChatColor.RED + "발뭉의 종말 재사용 대기 중입니다. (" + remaining + "초)");
            return;
        }

        manager.useDoom(player);
        player.sendMessage(ChatColor.WHITE + "발뭉의 종말! " + ChatColor.GRAY + "고대의 파멸이 풀려납니다.");
    }

    /** 용살의 긍지: 상대 방어력에 비례한 방어관통 + 갑주 내구도 마모. */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onMeleeDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker) || !(event.getEntity() instanceof LivingEntity target)) {
            return;
        }
        if (OpImmunity.isImmune(target)) {
            return;
        }
        BalmungManager manager = plugin.getBalmungManager();
        if (!manager.isBalmung(attacker.getInventory().getItemInMainHand())) {
            return;
        }

        if (event.isApplicable(EntityDamageEvent.DamageModifier.ARMOR)) {
            double factor = 1.0 - Math.min(manager.penetrationPercentAgainst(target), 100.0) / 100.0;
            event.setDamage(EntityDamageEvent.DamageModifier.ARMOR,
                    event.getDamage(EntityDamageEvent.DamageModifier.ARMOR) * factor);
        }
        manager.wearDownArmor(target);
    }
}
