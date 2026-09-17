package com.tntbbp.myminecraft.manager.world;

import com.tntbbp.myminecraft.MyMinecraftPlugin;
import com.tntbbp.myminecraft.util.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Beacon;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

/**
 * '코어' 아이템/블록 정의. 신호기(Beacon)를 변형해 만든 팀 거점 겸 상점.
 * 제작법: 네더별-드래곤의 숨결-네더별 / 바다의 심장-유리-바다의 심장 / 철블럭x3 (신호기와 유사한 배치).
 * 바닥에 설치하면 실제 신호기 블록이 되고, 그 위치가 설치한 사람의 팀 거점(홈)이 된다.
 * 우클릭하면 바닐라 신호기 GUI 대신 전용 상점 GUI(CoreGUI)가 열린다.
 */
public class CoreManager {

    private final MyMinecraftPlugin plugin;
    private final NamespacedKey itemKey;
    private final NamespacedKey blockTeamKey;

    public CoreManager(MyMinecraftPlugin plugin) {
        this.plugin = plugin;
        this.itemKey = new NamespacedKey(plugin, "core_item");
        this.blockTeamKey = new NamespacedKey(plugin, "core_team");
    }

    public ItemStack createItem() {
        ItemStack item = new ItemBuilder(Material.BEACON)
                .name("§b§l코어")
                .lore(List.of(
                        "§7팀의 거점을 표시하는 블록입니다.",
                        "§7원하는 곳에 설치하면 그 위치가",
                        "§7우리 팀의 거점(홈)이 됩니다.",
                        "§7우클릭하면 강화석/두루마리/별가루 구매,",
                        "§7암시장 아이템 구매, 직업 전직을 할 수 있는",
                        "§7전용 상점 GUI가 열립니다."
                ))
                .glow()
                .build();

        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(itemKey, PersistentDataType.BYTE, (byte) 1);
        int modelData = plugin.getConfig().getInt("core.model-data", 0);
        if (modelData != 0) {
            meta.setCustomModelData(modelData);
        }
        item.setItemMeta(meta);
        return item;
    }

    public boolean isCoreItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        Byte flag = item.getItemMeta().getPersistentDataContainer().get(itemKey, PersistentDataType.BYTE);
        return flag != null && flag == (byte) 1;
    }

    public ShapedRecipe recipe() {
        NamespacedKey key = new NamespacedKey(plugin, "core");
        ShapedRecipe recipe = new ShapedRecipe(key, createItem());
        recipe.shape("NDN", "HGH", "III");
        recipe.setIngredient('N', Material.NETHER_STAR);
        recipe.setIngredient('D', Material.DRAGON_BREATH);
        recipe.setIngredient('H', Material.HEART_OF_THE_SEA);
        recipe.setIngredient('G', Material.GLASS);
        recipe.setIngredient('I', Material.IRON_BLOCK);
        return recipe;
    }

    /** 설치된 신호기 블록에 소유 팀을 표시한다. */
    public void markBlock(Beacon beacon, String teamName) {
        beacon.getPersistentDataContainer().set(blockTeamKey, PersistentDataType.STRING, teamName);
        beacon.update();
    }

    /** 이 신호기 블록이 코어라면 소유 팀 이름을, 그냥 바닐라 신호기라면 null을 반환한다. */
    public String getBlockTeam(Beacon beacon) {
        return beacon.getPersistentDataContainer().get(blockTeamKey, PersistentDataType.STRING);
    }

    // ----- 상점 가격 (포인트) -----

    /** SpecialItemCatalog와 동일한 아이템명 기준으로 가격을 조회한다. */
    public double price(String itemName) {
        return plugin.getConfig().getDouble("core.shop.prices." + itemName, 9999.0);
    }
}
