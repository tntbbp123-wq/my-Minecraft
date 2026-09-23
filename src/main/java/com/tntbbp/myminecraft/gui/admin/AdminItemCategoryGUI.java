package com.tntbbp.myminecraft.gui.admin;

import com.tntbbp.myminecraft.MyMinecraftPlugin;
import com.tntbbp.myminecraft.util.ItemBuilder;
import com.tntbbp.myminecraft.util.SpecialItemCatalog;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * 관리자 메뉴의 분류별 아이템 화면. 누르면 그 아이템을 내 인벤토리로 꺼낸다.
 *
 * <p>위 다섯 줄(45칸)에 아이템을 넣고 맨 아랫줄은 이동용으로 쓴다. 한 분류가 45개를 넘으면
 * 쪽을 넘겨 본다. 재료처럼 {@code config.yml}로 늘어나는 분류가 있어서, 칸이 모자라 뒤쪽이
 * 잘리는 일이 없게 하기 위해서다.
 */
public class AdminItemCategoryGUI {

    public static final int SIZE = 54;
    public static final int ITEMS_PER_PAGE = 45;

    public static final int BACK_SLOT = 45;
    public static final int PREV_SLOT = 48;
    public static final int PAGE_INFO_SLOT = 49;
    public static final int NEXT_SLOT = 50;
    public static final int CLOSE_SLOT = 53;

    private final MyMinecraftPlugin plugin;
    private final Player player;
    private final AdminItemCategory category;
    private final int requestedPage;

    public AdminItemCategoryGUI(MyMinecraftPlugin plugin, Player player, AdminItemCategory category, int page) {
        this.plugin = plugin;
        this.player = player;
        this.category = category;
        this.requestedPage = page;
    }

    public void open() {
        List<String> names = AdminItemCategory.group(plugin).get(category);
        int pages = Math.max(1, (names.size() + ITEMS_PER_PAGE - 1) / ITEMS_PER_PAGE);
        // 쪽을 넘기는 사이 설정이 다시 읽혀 아이템 수가 줄었을 수 있다. 범위 밖이면 끝 쪽으로 맞춘다.
        int page = Math.max(0, Math.min(requestedPage, pages - 1));

        AdminMenuHolder holder = new AdminMenuHolder(category, page);
        String title = AdminMenuGUI.TITLE + " §8› " + category.color() + category.displayName()
                + (pages > 1 ? " §8(" + (page + 1) + "/" + pages + ")" : "");
        Inventory inventory = Bukkit.createInventory(holder, SIZE, title);
        holder.setInventory(inventory);

        ItemStack filler = new ItemBuilder(Material.BLACK_STAINED_GLASS_PANE).name(" ").build();
        for (int slot = ITEMS_PER_PAGE; slot < SIZE; slot++) {
            inventory.setItem(slot, filler);
        }

        int start = page * ITEMS_PER_PAGE;
        for (int i = 0; i < ITEMS_PER_PAGE && start + i < names.size(); i++) {
            String itemName = names.get(start + i);
            SpecialItemCatalog.Resolved resolved = SpecialItemCatalog.resolve(plugin, itemName, 1);
            if (resolved == null) {
                continue;
            }
            inventory.setItem(i, takeIcon(resolved.item()));
            holder.mapGiveItem(i, itemName);
        }

        if (names.isEmpty()) {
            inventory.setItem(22, new ItemBuilder(Material.GRAY_DYE)
                    .name("§7이 분류에는 아이템이 없습니다")
                    .build());
        }

        inventory.setItem(BACK_SLOT, new ItemBuilder(Material.ARROW)
                .name("§f← 관리자 메뉴로")
                .build());
        holder.mapNav(BACK_SLOT, AdminMenuHolder.Nav.BACK);

        if (page > 0) {
            inventory.setItem(PREV_SLOT, new ItemBuilder(Material.SPECTRAL_ARROW)
                    .name("§e이전 쪽 §7(" + page + "/" + pages + ")")
                    .build());
            holder.mapNav(PREV_SLOT, AdminMenuHolder.Nav.PREV_PAGE);
        }
        inventory.setItem(PAGE_INFO_SLOT, new ItemBuilder(category.icon())
                .name(category.color() + "§l" + category.displayName())
                .lore(List.of("§7아이템 §f" + names.size() + "개"
                        + (pages > 1 ? " §8· §7" + (page + 1) + "/" + pages + "쪽" : "")))
                .build());
        if (page < pages - 1) {
            inventory.setItem(NEXT_SLOT, new ItemBuilder(Material.SPECTRAL_ARROW)
                    .name("§e다음 쪽 §7(" + (page + 2) + "/" + pages + ")")
                    .build());
            holder.mapNav(NEXT_SLOT, AdminMenuHolder.Nav.NEXT_PAGE);
        }

        inventory.setItem(CLOSE_SLOT, new ItemBuilder(Material.BARRIER)
                .name("§c닫기")
                .build());
        holder.mapNav(CLOSE_SLOT, AdminMenuHolder.Nav.CLOSE);

        player.openInventory(inventory);
    }

    /** 실제 아이템 모양 그대로에 꺼내는 방법만 덧붙인다. */
    private ItemStack takeIcon(ItemStack item) {
        List<String> lore = new ArrayList<>(item.hasItemMeta() && item.getItemMeta().hasLore()
                ? item.getItemMeta().getLore() : List.of());
        lore.add("");
        lore.add("§a좌클릭 §7- 1개 꺼내기");
        lore.add("§a쉬프트+좌클릭 §7- 최대 스택 꺼내기");
        return new ItemBuilder(item).lore(lore).build();
    }
}
