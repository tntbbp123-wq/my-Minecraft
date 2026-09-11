package com.tntbbp.myminecraft.util;

import com.tntbbp.myminecraft.manager.CurrencyManager;
import com.tntbbp.myminecraft.manager.EnhanceManager;
import com.tntbbp.myminecraft.manager.LaevateinnManager;
import com.tntbbp.myminecraft.manager.StarforceManager;
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

    public static Resolved resolve(EnhanceManager enhanceManager, LaevateinnManager laevateinnManager,
                                    CurrencyManager currencyManager, StarforceManager starforceManager,
                                    String itemName, int amount) {
        if (itemName.equals("강화석")) {
            return new Resolved(enhanceManager.createEnhanceStone(amount), "강화석");
        }
        if (itemName.equals("별가루")) {
            return new Resolved(starforceManager.createStardust(amount), "별가루");
        }
        if (itemName.equals("레바테인")) {
            ItemStack item = laevateinnManager.createItem();
            item.setAmount(amount);
            return new Resolved(item, "레바테인");
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
    public static List<String> allItemNames(EnhanceManager enhanceManager, CurrencyManager currencyManager) {
        List<String> names = new ArrayList<>();
        names.add("강화석");
        names.add("별가루");
        names.add("레바테인");
        names.addAll(enhanceManager.scrollGrades().stream()
                .map(SpecialItemCatalog::scrollShortName)
                .collect(Collectors.toList()));
        names.addAll(currencyManager.denominations().stream()
                .map(SpecialItemCatalog::coinShortName)
                .collect(Collectors.toList()));
        return names;
    }
}
