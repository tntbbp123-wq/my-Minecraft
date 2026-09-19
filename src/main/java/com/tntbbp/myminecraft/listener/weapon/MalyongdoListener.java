package com.tntbbp.myminecraft.listener.weapon;

import com.tntbbp.myminecraft.MyMinecraftPlugin;
import com.tntbbp.myminecraft.manager.weapon.MalyongdoManager;
import org.bukkit.ChatColor;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;

/**
 * 말룡도의 세 초식 발동과 '말' 스택 적립.
 *
 * <ul>
 *   <li>{@code F} = 1초식 말 / {@code 웅크리기+F} = 2초식 말룡</li>
 *   <li>{@code 웅크리기+우클릭} = 3초식 종말룡</li>
 * </ul>
 */
public class MalyongdoListener implements Listener {

    private final MyMinecraftPlugin plugin;

    public MalyongdoListener(MyMinecraftPlugin plugin) {
        this.plugin = plugin;
    }

    /** F키 하나로 1·2초식을 나눠 쓴다. 그냥 F는 1초식, 웅크린 채 F는 2초식이다. */
    @EventHandler
    public void onSwapHands(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        MalyongdoManager manager = plugin.getMalyongdoManager();
        if (!manager.isMalyongdo(player.getInventory().getItemInMainHand())) {
            return;
        }

        event.setCancelled(true);
        if (plugin.getCurseManager().blockSkill(player)) {
            return;
        }

        String failure = player.isSneaking() ? manager.useSecond(player) : manager.useFirst(player);
        if (failure != null) {
            player.sendMessage(ChatColor.RED + failure);
        }
    }

    /** 웅크리기 + 우클릭 = 3초식 종말룡. */
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
        MalyongdoManager manager = plugin.getMalyongdoManager();
        if (!manager.isMalyongdo(player.getInventory().getItemInMainHand())) {
            return;
        }

        event.setCancelled(true);
        if (plugin.getCurseManager().blockSkill(player)) {
            return;
        }

        String failure = manager.useThird(player);
        if (failure != null) {
            player.sendMessage(ChatColor.RED + failure);
        }
    }

    /**
     * 말룡도로 때리면 '말'이 1 쌓인다. '종말의 화신' 중이라면 입힌 피해가 파괴력으로도 쌓인다.
     *
     * <p>MONITOR로 받는 이유는, 다른 리스너가 피해를 깎거나 취소한 <b>최종 결과</b>를 봐야
     * 실제로 들어간 만큼만 파괴력으로 쌓을 수 있기 때문이다.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMeleeDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker) || !(event.getEntity() instanceof LivingEntity)) {
            return;
        }
        MalyongdoManager manager = plugin.getMalyongdoManager();
        if (!manager.isMalyongdo(attacker.getInventory().getItemInMainHand())) {
            return;
        }
        manager.addHitStack(attacker.getUniqueId());
        if (manager.isAvatar(attacker.getUniqueId())) {
            manager.accumulatePower(attacker.getUniqueId(), event.getFinalDamage());
        }
    }
}
