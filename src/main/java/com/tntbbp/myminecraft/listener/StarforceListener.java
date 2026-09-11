package com.tntbbp.myminecraft.listener;

import com.tntbbp.myminecraft.MyMinecraftPlugin;
import com.tntbbp.myminecraft.manager.StarforceManager;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;

/**
 * 스타포스로 붙인 별(성)의 방어관통(%) 효과를 근접 공격 피해에 적용한다.
 * 원거리(화살/삼지창 투척) 피해에 대한 방어관통 적용은 10성 특수 능력과 함께 다음 업데이트에서 다룬다.
 */
public class StarforceListener implements Listener {

    private final MyMinecraftPlugin plugin;

    public StarforceListener(MyMinecraftPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onMeleeDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker) || !(event.getEntity() instanceof LivingEntity)) {
            return;
        }

        StarforceManager starforceManager = plugin.getStarforceManager();
        ItemStack weapon = attacker.getInventory().getItemInMainHand();
        double penetrationPercent = starforceManager.defensePenetrationPercent(weapon);
        if (penetrationPercent <= 0) {
            return;
        }

        double factor = 1.0 - Math.min(penetrationPercent, 100.0) / 100.0;
        if (event.isApplicable(EntityDamageEvent.DamageModifier.ARMOR)) {
            event.setDamage(EntityDamageEvent.DamageModifier.ARMOR,
                    event.getDamage(EntityDamageEvent.DamageModifier.ARMOR) * factor);
        }
    }
}
