package com.tntbbp.myminecraft.listener.weapon;

import com.tntbbp.myminecraft.MyMinecraftPlugin;
import com.tntbbp.myminecraft.manager.weapon.DrakenPierceManager;
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
import org.bukkit.inventory.ItemStack;

public class DrakenPierceListener implements Listener {

    private final MyMinecraftPlugin plugin;

    public DrakenPierceListener(MyMinecraftPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * F키(기본 손 바꾸기 키) 하나로 두 스킬을 모두 발동한다.
     * 그냥 F는 드라켄 라이트닝, 웅크린 채로 F는 드라코닉 팽이다.
     */
    @EventHandler
    public void onSwapHands(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        ItemStack mainHand = player.getInventory().getItemInMainHand();
        DrakenPierceManager manager = plugin.getDrakenPierceManager();
        if (!manager.isDrakenPierce(mainHand)) {
            return;
        }

        event.setCancelled(true);
        if (plugin.getCurseManager().blockSkill(player)) {
            return;
        }

        if (player.isSneaking()) {
            useDraconicFang(player, manager);
        } else {
            useDrakenLightning(player, manager);
        }
    }

    /** [F] 드라켄 라이트닝. */
    private void useDrakenLightning(Player player, DrakenPierceManager manager) {
        long remaining = manager.remainingLightningCooldownSeconds(player.getUniqueId());
        if (remaining > 0) {
            player.sendMessage(ChatColor.RED + "드라켄 라이트닝 재사용 대기 중입니다. (" + remaining + "초)");
            return;
        }

        manager.useDrakenLightning(player);
        player.sendMessage(ChatColor.AQUA + "드라켄 라이트닝을 발동했습니다!");
    }

    /** [웅크리기+F] 드라코닉 팽. */
    private void useDraconicFang(Player player, DrakenPierceManager manager) {
        long remaining = manager.remainingMetamorphosisCooldownSeconds(player.getUniqueId());
        if (remaining > 0) {
            player.sendMessage(ChatColor.RED + "드라코닉 팽 재사용 대기 중입니다. (" + remaining + "초)");
            return;
        }

        manager.useMetamorphosis(player);
        player.sendMessage(ChatColor.LIGHT_PURPLE + "드라코닉 팽을 발동했습니다!");
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
