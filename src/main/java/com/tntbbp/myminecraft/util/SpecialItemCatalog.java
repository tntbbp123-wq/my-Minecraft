package com.tntbbp.myminecraft.util;

import com.tntbbp.myminecraft.MyMinecraftPlugin;
import com.tntbbp.myminecraft.manager.economy.BlackMarketManager;
import com.tntbbp.myminecraft.manager.economy.CurrencyManager;
import com.tntbbp.myminecraft.manager.item.EnhanceManager;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 강화석/등급별 확률 강화 두루마리/레바테인/화폐 동전 등 플러그인이 만드는 모든 "특수 아이템"을
 * 이름으로 찾아 생성하는 공용 헬퍼. /특수아이템소환 명령어와 관리자메뉴 GUI(직접 꺼내기)에서
 * 공통으로 사용한다.
 */
public final class SpecialItemCatalog {

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

    /** 명령어/GUI에서 선택 가능한 아이템명 전체 목록. */
    public static List<String> allItemNames(MyMinecraftPlugin plugin) {
        EnhanceManager enhanceManager = plugin.getEnhanceManager();
        CurrencyManager currencyManager = plugin.getCurrencyManager();
        List<String> names = new ArrayList<>();
        names.add("강화석");
        names.add("별가루");
        names.add("레바테인");
        names.add("드라켄피어스");
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
