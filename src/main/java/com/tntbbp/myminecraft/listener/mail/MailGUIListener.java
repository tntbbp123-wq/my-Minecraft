package com.tntbbp.myminecraft.listener.mail;

import com.tntbbp.myminecraft.MyMinecraftPlugin;
import com.tntbbp.myminecraft.gui.mail.MailGUI;
import com.tntbbp.myminecraft.gui.mail.MailHolder;
import com.tntbbp.myminecraft.gui.mail.MailText;
import com.tntbbp.myminecraft.gui.menu.MenuGUI;
import com.tntbbp.myminecraft.manager.mail.MailManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

/** 우편함 GUI 클릭 처리: 우편 받기 · 모두 받기 · 페이지 이동 · 메뉴로. 우편함 칸의 아이템은 꺼낼 수 없다. */
public class MailGUIListener implements Listener {

    private final MyMinecraftPlugin plugin;

    public MailGUIListener(MyMinecraftPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof MailHolder holder)) {
            return;
        }
        event.setCancelled(true);
        if (event.getClickedInventory() != event.getInventory() || !(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        int slot = event.getSlot();
        switch (slot) {
            case MailGUI.BACK_SLOT -> new MenuGUI(plugin, player).open();
            case MailGUI.PREV_SLOT -> new MailGUI(plugin, player, holder.getPage() - 1).open();
            case MailGUI.NEXT_SLOT -> new MailGUI(plugin, player, holder.getPage() + 1).open();
            case MailGUI.CLAIM_ALL_SLOT -> claimAll(player, holder);
            default -> {
                Long mailId = holder.getMailId(slot);
                if (mailId != null) {
                    claim(player, holder, mailId);
                }
            }
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof MailHolder) {
            int topSize = event.getInventory().getSize();
            for (int slot : event.getRawSlots()) {
                if (slot < topSize) {
                    event.setCancelled(true);
                    return;
                }
            }
        }
    }

    private void claim(Player player, MailHolder holder, long mailId) {
        MailManager mailManager = plugin.getMailManager();
        MailManager.ClaimResult result = mailManager.claim(player, mailId);
        String currency = plugin.getEconomyManager().currencyName();
        switch (result.status()) {
            case CLAIMED -> {
                if (result.received().isEmpty() && result.receivedG() == 0) {
                    player.sendMessage(prefix().append(Component.text("우편을 읽었습니다.", NamedTextColor.GREEN)));
                } else {
                    player.sendMessage(prefix().append(Component.text("우편을 받았습니다: ", NamedTextColor.GREEN))
                            .append(MailText.receivedSummary(result.received(), result.receivedG(), currency)
                                    .color(NamedTextColor.WHITE)));
                    pickupSound(player);
                }
            }
            case PARTIAL -> {
                player.sendMessage(prefix()
                        .append(Component.text("인벤토리가 부족해 일부만 받았습니다: ", NamedTextColor.YELLOW))
                        .append(MailText.receivedSummary(result.received(), result.receivedG(), currency)
                                .color(NamedTextColor.WHITE)));
                player.sendMessage(prefix().append(Component.text(
                        "남은 첨부는 우편함에 그대로 있습니다. 인벤토리를 비운 뒤 다시 받아 주세요.", NamedTextColor.GRAY)));
                pickupSound(player);
            }
            case NO_SPACE -> player.sendMessage(prefix().append(Component.text(
                    "인벤토리에 빈 공간이 없어 받을 수 없습니다.", NamedTextColor.RED)));
            case NOT_FOUND, ALREADY_CLAIMED -> player.sendMessage(prefix().append(Component.text(
                    "이미 받았거나 없어진 우편입니다.", NamedTextColor.GRAY)));
        }
        new MailGUI(plugin, player, holder.getPage()).open();
    }

    private void claimAll(Player player, MailHolder holder) {
        MailManager.ClaimAllResult result = plugin.getMailManager().claimAll(player);
        String currency = plugin.getEconomyManager().currencyName();
        int touched = result.claimed() + result.partial();
        if (touched == 0) {
            player.sendMessage(prefix().append(Component.text(result.remaining() > 0
                    ? "인벤토리에 빈 공간이 없어 받을 수 없습니다." : "받을 우편이 없습니다.", NamedTextColor.RED)));
        } else {
            Component message = prefix().append(Component.text("우편 " + touched + "통을 받았습니다", NamedTextColor.GREEN));
            if (!result.received().isEmpty() || result.receivedG() > 0) {
                message = message.append(Component.text(": ", NamedTextColor.GREEN))
                        .append(MailText.receivedSummary(result.received(), result.receivedG(), currency)
                                .color(NamedTextColor.WHITE));
            }
            player.sendMessage(message);
            if (result.remaining() > 0) {
                player.sendMessage(prefix().append(Component.text("인벤토리가 부족해 우편 " + result.remaining()
                        + "통이 남았습니다. 인벤토리를 비운 뒤 다시 받아 주세요.", NamedTextColor.YELLOW)));
            }
            pickupSound(player);
        }
        new MailGUI(plugin, player, holder.getPage()).open();
    }

    private static void pickupSound(Player player) {
        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.6f, 1.2f);
    }

    private static Component prefix() {
        return Component.text("[우편] ", NamedTextColor.GOLD);
    }
}
