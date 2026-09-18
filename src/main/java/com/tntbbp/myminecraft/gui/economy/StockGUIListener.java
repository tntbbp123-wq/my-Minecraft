package com.tntbbp.myminecraft.gui.economy;

import com.tntbbp.myminecraft.MyMinecraftPlugin;
import com.tntbbp.myminecraft.model.Stock;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;

/**
 * 주식 거래소 GUI의 페이지 넘김과 거래 중지 종목 안내.
 *
 * <p>매수·매도·뒤로가기는 기존 {@code GUIListener}가 그대로 처리한다. 이 리스너는 그보다 먼저(LOW) 돌아서
 * 이전/다음 버튼이면 해당 페이지를 열고, 거래 중지 종목을 누르면 안내 메시지를 보낸다
 * (그 뒤 {@code GUIListener}의 매매 시도는 {@code StockManager}가 {@code HALTED}로 막는다).
 * 페이지 버튼 칸은 종목과 연결돼 있지 않아 {@code GUIListener}는 아무것도 하지 않는다.
 */
public class StockGUIListener implements Listener {

    private final MyMinecraftPlugin plugin;

    public StockGUIListener(MyMinecraftPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof StockHolder holder)) {
            return;
        }
        if (event.getClickedInventory() != event.getInventory()) {
            return;
        }
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        int slot = event.getSlot();
        if (slot == StockGUI.PREV_SLOT || slot == StockGUI.NEXT_SLOT) {
            event.setCancelled(true);
            int target = holder.getPage() + (slot == StockGUI.NEXT_SLOT ? 1 : -1);
            int total = plugin.getStockManager().getStocks().size();
            int perPage = StockGUI.STOCK_SLOTS.length;
            if (target < 0 || target >= StockPages.pageCount(total, perPage)) {
                return;
            }
            new StockGUI(plugin, player, target).open();
            return;
        }
        String stockId = holder.getStockId(slot);
        if (stockId == null) {
            return;
        }
        Stock stock = plugin.getStockManager().getStock(stockId);
        if (stock != null && stock.isHalted()) {
            player.sendMessage(ChatColor.RED + "'" + stock.getName() + "' 종목은 거래가 중지되어 매수·매도할 수 없습니다.");
        }
    }
}
