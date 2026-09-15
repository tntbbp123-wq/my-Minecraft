package com.tntbbp.myminecraft.listener;

import com.tntbbp.myminecraft.MyMinecraftPlugin;
import com.tntbbp.myminecraft.manager.CombatStanceManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

/** 전투모드 스킬 슬롯의 발동(우클릭)과 보호(드롭·이동 금지, 접속 종료/사망 시 정리)를 처리한다. */
public class CombatStanceListener implements Listener {

    private final MyMinecraftPlugin plugin;

    public CombatStanceListener(MyMinecraftPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Player player = event.getPlayer();
        CombatStanceManager manager = plugin.getCombatStanceManager();
        if (!manager.isInStance(player.getUniqueId())) {
            return;
        }

        int slot = manager.getSkillSlot(event.getItem());
        if (slot < 0) {
            return;
        }

        event.setCancelled(true);
        manager.executeSkill(player, slot);
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        if (plugin.getCombatStanceManager().isSkillItem(event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        CombatStanceManager manager = plugin.getCombatStanceManager();
        if (!(event.getWhoClicked() instanceof Player player) || !manager.isInStance(player.getUniqueId())) {
            return;
        }
        if (manager.isSkillItem(event.getCurrentItem()) || manager.isSkillItem(event.getCursor())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (plugin.getCombatStanceManager().isInStance(player.getUniqueId())) {
            plugin.getCombatStanceManager().exit(player);
        }
    }

    /** 사망 시점에는 인벤토리가 아니라 event.getDrops()가 최종 드롭 목록이므로, 스킬 아이템은
     * 빼고 저장해둔 원래 아이템으로 바꿔치기한다. */
    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        CombatStanceManager manager = plugin.getCombatStanceManager();
        if (!manager.isInStance(player.getUniqueId())) {
            return;
        }

        ItemStack[] saved = manager.clearStanceAndGetSaved(player.getUniqueId());
        event.getDrops().removeIf(manager::isSkillItem);
        if (saved != null) {
            for (ItemStack item : saved) {
                if (item != null) {
                    event.getDrops().add(item);
                }
            }
        }
    }
}
