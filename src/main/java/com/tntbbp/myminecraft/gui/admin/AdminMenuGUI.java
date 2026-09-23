package com.tntbbp.myminecraft.gui.admin;

import com.tntbbp.myminecraft.MyMinecraftPlugin;
import com.tntbbp.myminecraft.manager.economy.NewsManager;
import com.tntbbp.myminecraft.manager.economy.StockManager;
import com.tntbbp.myminecraft.model.Stock;
import com.tntbbp.myminecraft.util.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 관리자 전용 안내/현황 GUI. 명령어 안내 항목은 클릭 시 채팅창에 명령어를 입력해준다.
 *
 * <p>특수 아이템은 <b>분류별 칸</b>(무기·방어구·재료·강화 아이템·암시장 아이템·화폐)으로 나눠 두고,
 * 칸을 누르면 {@link AdminItemCategoryGUI}에서 그 분류의 아이템을 꺼낼 수 있다. 예전에는 모든 아이템을
 * 한 화면 27칸에 순서대로 넣어서, 아이템이 27개를 넘자 뒤쪽(신화 무기 일부·암시장·두루마리·화폐)이
 * 메뉴에서 아예 사라졌다.
 */
public class AdminMenuGUI {

    public static final String TITLE = "§4관리자 메뉴";
    public static final int SIZE = 54;

    public static final int STOCK_GIVE_SLOT = 4;
    public static final int CLOSE_SLOT = 8;
    public static final int STOCK_ADD_SLOT = 10;
    public static final int NEWS_SLOT = 12;
    public static final int FAKE_NEWS_SLOT = 14;
    public static final int SPECIAL_ITEM_SLOT = 16;
    /** 분류 칸 자리. {@link AdminItemCategory} 순서대로 채운다 (3칸씩 두 줄). */
    static final int[] CATEGORY_SLOTS = {20, 22, 24, 29, 31, 33};
    public static final int STOCK_STATUS_SLOT = 49;

    /** 분류 칸 설명에 미리 보여줄 아이템 이름 수. 넘으면 "외 n개"로 줄인다. */
    private static final int PREVIEW_NAMES = 8;

    private final MyMinecraftPlugin plugin;
    private final Player player;

    public AdminMenuGUI(MyMinecraftPlugin plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
    }

    public void open() {
        AdminMenuHolder holder = new AdminMenuHolder();
        Inventory inventory = Bukkit.createInventory(holder, SIZE, TITLE);
        holder.setInventory(inventory);

        ItemStack filler = new ItemBuilder(Material.RED_STAINED_GLASS_PANE).name(" ").build();
        for (int i = 0; i < SIZE; i++) {
            inventory.setItem(i, filler);
        }

        inventory.setItem(STOCK_ADD_SLOT, new ItemBuilder(Material.EMERALD)
                .name("§a주식 종목 추가")
                .lore(List.of(
                        "§7/주식종류추가 <이름> <최소값> <최대값> <변동단위> <업종>",
                        "§7시작가는 항상 최소값으로 설정됩니다.",
                        "§7업종은 AI가 뉴스를 쓸 때 참고합니다 (예: \"금 채굴 회사\").",
                        "§7클릭하면 채팅창에 명령어가 입력됩니다."
                ))
                .build());
        holder.mapCommand(STOCK_ADD_SLOT, "/주식종류추가 ");

        inventory.setItem(STOCK_GIVE_SLOT, new ItemBuilder(Material.GOLD_NUGGET)
                .name("§6주식 지급")
                .lore(List.of(
                        "§7/주식지급 <종목명> <유저> <수량>",
                        "§7대가 없이 플레이어에게 주식을 바로 지급합니다.",
                        "§7클릭하면 채팅창에 명령어가 입력됩니다."
                ))
                .build());
        holder.mapCommand(STOCK_GIVE_SLOT, "/주식지급 ");

        inventory.setItem(NEWS_SLOT, new ItemBuilder(Material.PAPER)
                .name("§f뉴스 작성")
                .lore(List.of(
                        "§7/뉴스작성 <종목명> <상승|하락> <변동폭%> <내용 또는 AI:주제>",
                        "§7또는: /뉴스작성 <종목명> AI:[주제] - 등락/변동폭까지",
                        "§7종목 업종에 맞게 AI가 직접 정합니다.",
                        "§71시간 뒤 공개, 2시간 뒤 실제 가격에 반영됩니다.",
                        "§7내용에는 뉴스 소식(사건)만 들어가고, 등락/퍼센트는 자동으로 제외됩니다.",
                        "§7클릭하면 채팅창에 명령어가 입력됩니다."
                ))
                .build());
        holder.mapCommand(NEWS_SLOT, "/뉴스작성 ");

        inventory.setItem(FAKE_NEWS_SLOT, new ItemBuilder(Material.GRAY_DYE)
                .name("§7가짜 뉴스 작성")
                .lore(List.of(
                        "§7/가짜뉴스작성 <종목명> <상승|하락> <변동폭%> <내용 또는 AI:주제>",
                        "§7또는: /가짜뉴스작성 <종목명> AI:[주제]",
                        "§71시간 뒤 똑같이 공개되지만 가격에는 반영되지 않습니다.",
                        "§7내용에는 뉴스 소식(사건)만 들어가고, 등락/퍼센트는 자동으로 제외됩니다.",
                        "§7클릭하면 채팅창에 명령어가 입력됩니다."
                ))
                .build());
        holder.mapCommand(FAKE_NEWS_SLOT, "/가짜뉴스작성 ");

        inventory.setItem(SPECIAL_ITEM_SLOT, new ItemBuilder(Material.NETHER_STAR)
                .name("§d다른 플레이어에게 특수 아이템 지급")
                .lore(List.of(
                        "§7/특수아이템소환 <유저> <아이템명> <수량>",
                        "§7강화석/두루마리/레바테인/화폐 동전 모두 지급 가능합니다.",
                        "§7클릭하면 채팅창에 명령어가 입력됩니다.",
                        "§7내 인벤토리로 바로 꺼내려면 아래 분류 칸을 누르세요."
                ))
                .build());
        holder.mapCommand(SPECIAL_ITEM_SLOT, "/특수아이템소환 ");

        Map<AdminItemCategory, List<String>> grouped = AdminItemCategory.group(plugin);
        AdminItemCategory[] categories = AdminItemCategory.values();
        for (int i = 0; i < categories.length && i < CATEGORY_SLOTS.length; i++) {
            AdminItemCategory category = categories[i];
            inventory.setItem(CATEGORY_SLOTS[i], categoryButton(category, grouped.get(category)));
            holder.mapCategory(CATEGORY_SLOTS[i], category);
        }

        StockManager stockManager = plugin.getStockManager();
        NewsManager newsManager = plugin.getNewsManager();
        List<String> statusLore = new ArrayList<>();
        for (Stock stock : stockManager.getStocks()) {
            statusLore.add("§e" + stock.getName() + " §7- 현재가 " + String.format("%,.1f", stock.getPrice())
                    + (stock.isCustom() ? " §8(최소 " + (int) stock.getMinPrice() + "~최대 " + (int) stock.getMaxPrice() + ")" : ""));
        }
        statusLore.add("");
        statusLore.add("§7대기 중인 뉴스: §f" + newsManager.getPendingItems().size() + "건");
        inventory.setItem(STOCK_STATUS_SLOT, new ItemBuilder(Material.BOOK)
                .name("§b현재 종목/뉴스 현황")
                .lore(statusLore)
                .build());

        inventory.setItem(CLOSE_SLOT, new ItemBuilder(Material.BARRIER)
                .name("§c닫기")
                .build());
        holder.mapNav(CLOSE_SLOT, AdminMenuHolder.Nav.CLOSE);

        player.openInventory(inventory);
    }

    /** 분류 칸. 몇 개가 들어 있는지와 앞쪽 아이템 이름을 미리 보여준다. */
    private ItemStack categoryButton(AdminItemCategory category, List<String> names) {
        List<String> lore = new ArrayList<>();
        lore.add("§7아이템 §f" + names.size() + "개");
        lore.add("");
        for (int i = 0; i < names.size() && i < PREVIEW_NAMES; i++) {
            lore.add("§8· §7" + names.get(i));
        }
        if (names.size() > PREVIEW_NAMES) {
            lore.add("§8  외 " + (names.size() - PREVIEW_NAMES) + "개");
        }
        lore.add("");
        lore.add("§a클릭 §7- 열어서 꺼내기");
        return new ItemBuilder(category.icon())
                .name(category.color() + "§l" + category.displayName())
                .lore(lore)
                .build();
    }
}
