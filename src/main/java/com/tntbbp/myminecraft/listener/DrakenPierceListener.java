package com.tntbbp.myminecraft.listener;

import com.tntbbp.myminecraft.MyMinecraftPlugin;
import com.tntbbp.myminecraft.manager.DrakenPierceManager;
import com.tntbbp.myminecraft.util.OpImmunity;
import org.bukkit.ChatColor;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;

public class DrakenPierceListener implements Listener {

    private final MyMinecraftPlugin plugin;

    public DrakenPierceListener(MyMinecraftPlugin plugin) {
        this.plugin = plugin;
    }

    /** F키(기본 손 바꾸기 키)를 드라켄 라이트닝 발동 키로 사용한다. */
    @EventHandler
    public void onSwapHands(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        ItemStack mainHand = player.getInventory().getItemInMainHand();
        DrakenPierceManager manager = plugin.getDrakenPierceManager();
        if (!manager.isDrakenPierce(mainHand)) {
            return;
        }

        event.setCancelled(true);
        long remaining = manager.remainingLightningCooldownSeconds(player.getUniqueId());
        if (remaining > 0) {
            player.sendMessage(ChatColor.RED + "드라켄 라이트닝 재사용 대기 중입니다. (" + remaining + "초)");
            return;
        }

        manager.useDrakenLightning(player);
        player.sendMessage(ChatColor.AQUA + "드라켄 라이트닝을 발동했습니다!");
    }

    /** Q키(기본 아이템 버리기 키)를 드라코닉 메타모포시스 발동 키로 사용한다. */
    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        ItemStack mainHand = player.getInventory().getItemInMainHand();
        DrakenPierceManager manager = plugin.getDrakenPierceManager();
        if (!manager.isDrakenPierce(mainHand)) {
            return;
        }

        event.setCancelled(true);
        long remaining = manager.remainingMetamorphosisCooldownSeconds(player.getUniqueId());
        if (remaining > 0) {
            player.sendMessage(ChatColor.RED + "드라코닉 메타모포시스 재사용 대기 중입니다. (" + remaining + "초)");
            return;
        }

        manager.useMetamorphosis(player);
        player.sendMessage(ChatColor.LIGHT_PURPLE + "드라코닉 메타모포시스를 발동했습니다!");
    }

    /** 드라켄피어스의 고정 방어관통(%)을 근접 공격 피해에 적용한다. */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onMeleeDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker) || !(event.getEntity() instanceof LivingEntity)) {
            return;
        }
        if (OpImmunity.isImmune(event.getEntity())) {
            return;
        }

        DrakenPierceManager manager = plugin.getDrakenPierceManager();
        ItemStack weapon = attacker.getInventory().getItemInMainHand();
        if (!manager.isDrakenPierce(weapon)) {
            return;
        }

        double factor = 1.0 - Math.min(manager.defensePenetrationPercent(), 100.0) / 100.0;
        if (event.isApplicable(EntityDamageEvent.DamageModifier.ARMOR)) {
            event.setDamage(EntityDamageEvent.DamageModifier.ARMOR,
                    event.getDamage(EntityDamageEvent.DamageModifier.ARMOR) * factor);
        }
    }
}
