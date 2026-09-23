package com.tntbbp.myminecraft.listener.combat;

import com.tntbbp.myminecraft.MyMinecraftPlugin;
import com.tntbbp.myminecraft.manager.combat.CombatStanceManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
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
            return;
        }
        // 숫자키로 핫바와 바꾸는 경우엔 옮겨지는 스킬 아이템이 "누른 칸"도 "커서"도 아니라 위 검사를 빠져나간다.
        if (event.getClick() == ClickType.NUMBER_KEY && event.getHotbarButton() >= 0
                && manager.isSkillItem(player.getInventory().getItem(event.getHotbarButton()))) {
            event.setCancelled(true);
        }
    }

    /** 스킬 아이템을 든 채 F(손 바꾸기)를 누르면 보조손 아이템이 스킬 칸으로 들어온다. 막는다. */
    @EventHandler
    public void onSwapHands(PlayerSwapHandItemsEvent event) {
        CombatStanceManager manager = plugin.getCombatStanceManager();
        if (!manager.isInStance(event.getPlayer().getUniqueId())) {
            return;
        }
        if (manager.isSkillItem(event.getMainHandItem()) || manager.isSkillItem(event.getOffHandItem())) {
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
        if (event.getKeepInventory()) {
            // 인벤토리를 지키는 설정이면 아이템이 떨어지지 않고 그대로 남는다. 원래 아이템을 바닥에 흘리지 말고
            // 핫바에 되돌려야 한다(예전에는 스킬 아이템이 핫바에 남고 원래 아이템은 바닥에 떨어졌다).
            if (saved != null) {
                manager.restoreSaved(player, saved);
            }
            return;
        }
        if (saved != null) {
            for (ItemStack item : saved) {
                if (item != null) {
                    event.getDrops().add(item);
                }
            }
        }
    }
}
