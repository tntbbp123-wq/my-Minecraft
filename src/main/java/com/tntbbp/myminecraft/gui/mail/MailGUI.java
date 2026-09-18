package com.tntbbp.myminecraft.gui.mail;

import com.tntbbp.myminecraft.MyMinecraftPlugin;
import com.tntbbp.myminecraft.manager.mail.MailManager;
import com.tntbbp.myminecraft.manager.mail.MailRules;
import com.tntbbp.myminecraft.model.Mail;
import com.tntbbp.myminecraft.model.MailAttachment;
import com.tntbbp.myminecraft.model.MailLimits;
import com.tntbbp.myminecraft.model.MailSenderType;
import com.tntbbp.myminecraft.util.ItemBuilder;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 우편함 GUI. 위 5줄(45칸)에 받을 수 있는 우편을 최신순으로 보여 주고, 클릭하면 그 우편을 받는다.
 * 맨 아래 줄: 메뉴로 · 이전/다음 페이지 · 모두 받기 · 우편함 정보.
 */
public class MailGUI {

    public static final String TITLE = "§8우편함";
    public static final int SIZE = 54;
    public static final int PAGE_SIZE = 45;
    public static final int BACK_SLOT = 45;
    public static final int PREV_SLOT = 47;
    public static final int CLAIM_ALL_SLOT = 49;
    public static final int NEXT_SLOT = 51;
    public static final int INFO_SLOT = 53;
    private static final int EMPTY_SLOT = 22;

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("MM-dd HH:mm")
            .withZone(ZoneId.of("Asia/Seoul"));

    private final MyMinecraftPlugin plugin;
    private final Player player;
    private final int requestedPage;

    public MailGUI(MyMinecraftPlugin plugin, Player player) {
        this(plugin, player, 0);
    }

    public MailGUI(MyMinecraftPlugin plugin, Player player, int page) {
        this.plugin = plugin;
        this.player = player;
        this.requestedPage = page;
    }

    public void open() {
        MailManager mailManager = plugin.getMailManager();
        List<Mail> mails = mailManager.claimable(player.getUniqueId());
        int pages = Math.max(1, (mails.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int page = Math.max(0, Math.min(requestedPage, pages - 1));

        MailHolder holder = new MailHolder(page);
        String title = pages > 1 ? TITLE + " §7(" + (page + 1) + "/" + pages + ")" : TITLE;
        Inventory inventory = Bukkit.createInventory(holder, SIZE, title);
        holder.setInventory(inventory);

        ItemStack filler = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build();
        for (int i = PAGE_SIZE; i < SIZE; i++) {
            inventory.setItem(i, filler);
        }

        long now = System.currentTimeMillis();
        String currency = plugin.getEconomyManager().currencyName();
        int from = page * PAGE_SIZE;
        for (int i = 0; i < PAGE_SIZE && from + i < mails.size(); i++) {
            Mail mail = mails.get(from + i);
            inventory.setItem(i, mailIcon(mail, now, currency));
            holder.mapMail(i, mail.id());
        }
        if (mails.isEmpty()) {
            inventory.setItem(EMPTY_SLOT, new ItemBuilder(Material.PAPER)
                    .name("§7받을 우편이 없습니다.")
                    .build());
        }

        inventory.setItem(BACK_SLOT, new ItemBuilder(Material.ARROW)
                .name("§7« 메뉴로 돌아가기")
                .build());
        if (page > 0) {
            inventory.setItem(PREV_SLOT, new ItemBuilder(Material.SPECTRAL_ARROW)
                    .name("§e« 이전 페이지")
                    .lore(List.of("§7" + page + "/" + pages + " 페이지로"))
                    .build());
        }
        if (page < pages - 1) {
            inventory.setItem(NEXT_SLOT, new ItemBuilder(Material.SPECTRAL_ARROW)
                    .name("§e다음 페이지 »")
                    .lore(List.of("§7" + (page + 2) + "/" + pages + " 페이지로"))
                    .build());
        }
        if (!mails.isEmpty()) {
            inventory.setItem(CLAIM_ALL_SLOT, new ItemBuilder(Material.HOPPER)
                    .name("§a모두 받기")
                    .lore(List.of(
                            "§7받지 않은 우편 " + mails.size() + "통을 한 번에 받습니다.",
                            "§7인벤토리가 부족하면 들어가는 만큼만 받고",
                            "§7나머지는 우편함에 그대로 남습니다."
                    ))
                    .build());
        }
        inventory.setItem(INFO_SLOT, infoIcon(mailManager, mails.size()));

        player.openInventory(inventory);
    }

    private ItemStack mailIcon(Mail mail, long now, String currency) {
        boolean hasItems = mail.attachments().stream().anyMatch(a -> a.remaining() > 0);
        Material icon = hasItems ? Material.CHEST : mail.pendingG() > 0 ? Material.GOLD_INGOT : Material.PAPER;
        ItemStack item = new ItemStack(icon);
        ItemMeta meta = item.getItemMeta();

        NamedTextColor senderColor = mail.senderType() == MailSenderType.ADMIN ? NamedTextColor.RED
                : mail.senderType() == MailSenderType.QUEST ? NamedTextColor.AQUA : NamedTextColor.GREEN;
        meta.displayName(Component.text("[" + mail.senderName() + "] ", senderColor)
                .append(Component.text(mail.title(), NamedTextColor.YELLOW))
                .decoration(TextDecoration.ITALIC, false));

        List<Component> lore = new ArrayList<>();
        for (String line : MailText.wrap(mail.body())) {
            lore.add(MailText.line(line, NamedTextColor.WHITE));
        }
        boolean partial = false;
        List<Component> rewards = new ArrayList<>();
        for (MailAttachment attachment : mail.attachments()) {
            if (attachment.remaining() <= 0) {
                continue;
            }
            if (attachment.claimed() > 0) {
                partial = true;
            }
            rewards.add(Component.text("- ", NamedTextColor.GRAY)
                    .append(MailText.itemName(attachment.displayName(), attachment.material())
                            .color(NamedTextColor.WHITE))
                    .append(Component.text(" x" + String.format("%,d", attachment.remaining()), NamedTextColor.GRAY))
                    .decoration(TextDecoration.ITALIC, false));
        }
        if (mail.pendingG() > 0) {
            rewards.add(MailText.line("- " + String.format("%,d", mail.pendingG()) + currency, NamedTextColor.GOLD));
        }
        if (mail.isGClaimed() && mail.attachedG() > 0) {
            partial = true;
        }
        if (!rewards.isEmpty()) {
            if (!lore.isEmpty()) {
                lore.add(Component.empty());
            }
            lore.add(MailText.line(partial ? "남은 첨부 (일부 받음):" : "첨부:", NamedTextColor.GOLD));
            lore.addAll(rewards);
        }
        lore.add(Component.empty());
        lore.add(MailText.line("받은 시각: " + TIME.format(java.time.Instant.ofEpochMilli(mail.createdAt())),
                NamedTextColor.GRAY));
        lore.add(MailText.line("남은 보관 기간: " + MailRules.formatRemaining(mail.expiresAt() - now),
                NamedTextColor.GRAY));
        lore.add(Component.empty());
        lore.add(MailText.line(rewards.isEmpty() ? "클릭하면 읽음 처리합니다." : "클릭하면 받습니다.",
                NamedTextColor.GREEN));
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack infoIcon(MailManager mailManager, int unclaimed) {
        MailLimits limits = mailManager.limits();
        int held = mailManager.heldCount(player.getUniqueId());
        List<String> lore = new ArrayList<>();
        lore.add("§7받지 않은 우편: §f" + unclaimed + "통");
        int max = limits.maxCountFor(MailSenderType.SYSTEM);
        if (max > 0) {
            lore.add("§7일반 우편은 최대 " + max + "통까지 보관합니다.");
            lore.add("§7(운영자 우편은 개수 제한 없음)");
        }
        if (held > 0) {
            lore.add("§c보류된 우편: " + held + "통");
            lore.add("§7우편함에 공간이 나면 자동으로 들어옵니다.");
        }
        lore.add("");
        lore.add("§7보관 기간: 일반 " + limits.regularRetentionDays() + "일 · 운영자 "
                + limits.adminRetentionDays() + "일");
        lore.add("§7기간이 지난 우편은 자동으로 삭제됩니다.");
        return new ItemBuilder(Material.BOOK)
                .name("§e우편함 정보")
                .lore(lore)
                .build();
    }
}
