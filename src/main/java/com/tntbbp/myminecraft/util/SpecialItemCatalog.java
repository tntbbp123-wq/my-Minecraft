package com.tntbbp.myminecraft.util;

import com.tntbbp.myminecraft.MyMinecraftPlugin;
import com.tntbbp.myminecraft.manager.economy.BlackMarketManager;
import com.tntbbp.myminecraft.manager.economy.CurrencyManager;
import com.tntbbp.myminecraft.manager.item.EnhanceManager;
import com.tntbbp.myminecraft.manager.item.MaterialManager;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 강화석/등급별 확률 강화 두루마리/레바테인/화폐 동전 등 플러그인이 만드는 모든 "특수 아이템"을
 * 이름으로 찾아 생성하는 공용 헬퍼. /특수아이템소환 명령어와 관리자메뉴 GUI(직접 꺼내기)에서
 * 공통으로 사용한다.
 */
public final class SpecialItemCatalog {

    /** 웹 카탈로그 분류용: 신화 무기 / 암시장 아이템 이름. */
    private static final Set<String> WEAPON_NAMES =
            Set.of("레바테인", "드라켄피어스", "글레이프니르", "사인참사검", "발뭉", "자하신검",
                    "말룡도", "타이탄", "방천화극", "볼트세이버");
    private static final Set<String> BLACKMARKET_NAMES = Set.of("일괄약탈주문서", "함정설치키트", "화염병", "연막탄",
            "밀도나침반", "발자국추적기", "혈흔나침반", "소음차단포션");

    /**
     * {@link #categoryOf}가 돌려줄 수 있는 분류 전부. 웹 카탈로그와 관리자 메뉴가 같은 값을 쓴다.
     * 새 분류를 만들면 여기에 넣어야 관리자 메뉴 분류 시험이 빠진 칸을 잡아낸다.
     */
    public static final List<String> CATEGORY_IDS = List.of(
            "weapon", "armor", "material", "enhance", "starforce", "scroll", "blackmarket", "coin");

    public record Resolved(ItemStack item, String displayName) {
    }

    private SpecialItemCatalog() {
    }

    public static Resolved resolve(MyMinecraftPlugin plugin, String itemName, int amount) {
        EnhanceManager enhanceManager = plugin.getEnhanceManager();
        CurrencyManager currencyManager = plugin.getCurrencyManager();
        BlackMarketManager blackMarketManager = plugin.getBlackMarketManager();

        if (itemName.equals("강화석")) {
            return new Resolved(enhanceManager.createEnhanceStone(amount), "강화석");
        }
        if (itemName.equals("별가루")) {
            return new Resolved(plugin.getStarforceManager().createStardust(amount), "별가루");
        }
        if (itemName.equals("레바테인")) {
            return mythicWeapon(plugin.getLaevateinnManager().createItem(), "레바테인", amount);
        }
        if (itemName.equals("드라켄피어스")) {
            return mythicWeapon(plugin.getDrakenPierceManager().createItem(), "드라켄피어스", amount);
        }
        if (itemName.equals("글레이프니르")) {
            return mythicWeapon(plugin.getGleipnirManager().createItem(), "글레이프니르", amount);
        }
        MaterialManager.MaterialDef material = plugin.getMaterialManager().find(itemName);
        if (material != null) {
            return new Resolved(plugin.getMaterialManager().createItem(material, amount), material.displayName());
        }
        if (itemName.equals("타이탄")) {
            return mythicWeapon(plugin.getTitanManager().createItem(), "타이탄", amount);
        }
        if (itemName.equals("방천화극")) {
            return mythicWeapon(plugin.getFangtianJiManager().createItem(), "방천화극", amount);
        }
        if (itemName.equals("볼트세이버")) {
            return mythicWeapon(plugin.getVoltSaberManager().createItem(), "볼트세이버", amount);
        }
        for (com.tntbbp.myminecraft.manager.item.ArtemisSetManager.Piece piece
                : com.tntbbp.myminecraft.manager.item.ArtemisSetManager.Piece.values()) {
            if (itemName.equals("아르테미스" + piece.displayName())) {
                return mythicWeapon(plugin.getArtemisSetManager().createPiece(piece),
                        "아르테미스의 " + piece.displayName(), amount);
            }
        }
        if (itemName.equals("말룡도")) {
            return mythicWeapon(plugin.getMalyongdoManager().createItem(), "말룡도", amount);
        }
        if (itemName.equals("사인참사검")) {
            return mythicWeapon(plugin.getSainchamsagumManager().createItem(), "사인참사검", amount);
        }
        if (itemName.equals("발뭉")) {
            return mythicWeapon(plugin.getBalmungManager().createItem(), "발뭉", amount);
        }
        if (itemName.equals("자하신검")) {
            return mythicWeapon(plugin.getJahaShingeomManager().createItem(), "자하신검", amount);
        }
        if (itemName.equals("일괄약탈주문서")) {
            return new Resolved(blackMarketManager.createLootAllScroll(amount), "일괄 약탈 주문서");
        }
        if (itemName.equals("함정설치키트")) {
            return new Resolved(blackMarketManager.createTrapKit(amount), "함정 설치 키트");
        }
        if (itemName.equals("화염병")) {
            return new Resolved(blackMarketManager.createMolotov(amount), "화염병");
        }
        if (itemName.equals("연막탄")) {
            return new Resolved(blackMarketManager.createSmokeBomb(amount), "연막탄");
        }
        if (itemName.equals("밀도나침반")) {
            return new Resolved(blackMarketManager.createDensityCompass(amount), "타일 밀도 나침반");
        }
        if (itemName.equals("발자국추적기")) {
            return new Resolved(blackMarketManager.createFootprintTracker(amount), "발자국 추적기");
        }
        if (itemName.equals("혈흔나침반")) {
            return new Resolved(blackMarketManager.createBloodCompass(amount), "혈흔 나침반");
        }
        if (itemName.equals("소음차단포션")) {
            return new Resolved(blackMarketManager.createSilencePotion(amount), "소음 차단 포션");
        }

        EnhanceManager.ScrollGrade grade = findScrollGrade(enhanceManager, itemName);
        if (grade != null) {
            return new Resolved(enhanceManager.createProbabilityScroll(grade, amount), grade.displayName());
        }

        CurrencyManager.CoinDenomination coin = findCoin(currencyManager, itemName);
        if (coin != null) {
            return new Resolved(currencyManager.createCoin(coin, amount), coin.displayName());
        }

        return null;
    }

    private static Resolved mythicWeapon(ItemStack item, String displayName, int amount) {
        item.setAmount(amount);
        return new Resolved(item, displayName);
    }

    public static EnhanceManager.ScrollGrade findScrollGrade(EnhanceManager enhanceManager, String itemName) {
        for (EnhanceManager.ScrollGrade grade : enhanceManager.scrollGrades()) {
            if (itemName.equalsIgnoreCase(grade.id()) || itemName.equals(scrollShortName(grade))) {
                return grade;
            }
        }
        return null;
    }

    public static String scrollShortName(EnhanceManager.ScrollGrade grade) {
        return grade.displayName().replace("등급", "").replace(" ", "");
    }

    public static CurrencyManager.CoinDenomination findCoin(CurrencyManager currencyManager, String itemName) {
        for (CurrencyManager.CoinDenomination coin : currencyManager.denominations()) {
            if (itemName.equalsIgnoreCase(coin.id()) || itemName.equalsIgnoreCase(coinShortName(coin))) {
                return coin;
            }
        }
        return null;
    }

    public static String coinShortName(CurrencyManager.CoinDenomination coin) {
        return coin.value() + "G";
    }

    /**
     * 웹 관리자(우편 첨부·지급 선택기)용 카탈로그 한 줄.
     *
     * @param name        /특수아이템소환·우편 첨부에 쓰는 정확한 아이템명 (예: 강화석, 일반두루마리, 1G)
     * @param category    enhance | scroll | starforce | weapon | armor | blackmarket | coin | material
     * @param modelData   커스텀 모델 데이터 (없으면 0)
     */
    public record CatalogEntry(String name, String displayName, String material, int modelData, String category) {
    }

    /**
     * {@link #allItemNames}의 각 아이템을 1개씩 실제로 만들어 이름·표시명·재질·모델 데이터·분류를 돌려준다.
     * 아이템을 만들므로 메인 스레드에서 호출한다.
     */
    public static List<CatalogEntry> catalog(MyMinecraftPlugin plugin) {
        EnhanceManager enhanceManager = plugin.getEnhanceManager();
        CurrencyManager currencyManager = plugin.getCurrencyManager();
        List<CatalogEntry> entries = new ArrayList<>();
        for (String name : allItemNames(plugin)) {
            Resolved resolved = resolve(plugin, name, 1);
            if (resolved == null || resolved.item() == null) {
                continue;
            }
            ItemStack item = resolved.item();
            ItemMeta meta = item.getItemMeta();
            int modelData = meta != null && meta.hasCustomModelData() ? meta.getCustomModelData() : 0;
            String displayName = resolved.displayName() == null ? name
                    : resolved.displayName().replaceAll("(?i)§[0-9A-FK-ORX]", "");
            entries.add(new CatalogEntry(name, displayName, item.getType().name(), modelData,
                    categoryOf(plugin, enhanceManager, currencyManager, name)));
        }
        return entries;
    }

    /** 아이템명의 분류. {@link #CATEGORY_IDS} 중 하나를 돌려준다. */
    public static String categoryOf(MyMinecraftPlugin plugin, String name) {
        return categoryOf(plugin, plugin.getEnhanceManager(), plugin.getCurrencyManager(), name);
    }

    private static String categoryOf(MyMinecraftPlugin plugin, EnhanceManager enhanceManager,
                                     CurrencyManager currencyManager, String name) {
        if (name.equals("강화석")) {
            return "enhance";
        }
        if (name.equals("별가루")) {
            return "starforce";
        }
        if (WEAPON_NAMES.contains(name)) {
            return "weapon";
        }
        if (name.startsWith("아르테미스")) {
            return "armor";
        }
        if (BLACKMARKET_NAMES.contains(name)) {
            return "blackmarket";
        }
        if (findScrollGrade(enhanceManager, name) != null) {
            return "scroll";
        }
        if (findCoin(currencyManager, name) != null) {
            return "coin";
        }
        if (plugin != null && plugin.getMaterialManager().find(name) != null) {
            return "material";
        }
        return "enhance";
    }

    /** 명령어/GUI에서 선택 가능한 아이템명 전체 목록. */
    public static List<String> allItemNames(MyMinecraftPlugin plugin) {
        EnhanceManager enhanceManager = plugin.getEnhanceManager();
        CurrencyManager currencyManager = plugin.getCurrencyManager();
        List<String> names = new ArrayList<>();
        names.add("강화석");
        names.add("별가루");
        names.add("레바테인");
        names.add("드라켄피어스");
        names.add("말룡도");
        names.add("타이탄");
        names.add("방천화극");
        names.add("볼트세이버");
        for (com.tntbbp.myminecraft.manager.item.ArtemisSetManager.Piece piece
                : com.tntbbp.myminecraft.manager.item.ArtemisSetManager.Piece.values()) {
            names.add("아르테미스" + piece.displayName());
        }
        names.addAll(plugin.getMaterialManager().materials().stream()
                .map(MaterialManager.MaterialDef::shortName)
                .collect(Collectors.toList()));
        names.add("글레이프니르");
        names.add("사인참사검");
        names.add("발뭉");
        names.add("자하신검");
        names.add("일괄약탈주문서");
        names.add("함정설치키트");
        names.add("화염병");
        names.add("연막탄");
        names.add("밀도나침반");
        names.add("발자국추적기");
        names.add("혈흔나침반");
        names.add("소음차단포션");
        names.addAll(enhanceManager.scrollGrades().stream()
                .map(SpecialItemCatalog::scrollShortName)
                .collect(Collectors.toList()));
        names.addAll(currencyManager.denominations().stream()
                .map(SpecialItemCatalog::coinShortName)
                .collect(Collectors.toList()));
        return names;
    }
}
