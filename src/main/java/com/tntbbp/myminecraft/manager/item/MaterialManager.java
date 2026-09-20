package com.tntbbp.myminecraft.manager.item;

import com.tntbbp.myminecraft.MyMinecraftPlugin;
import com.tntbbp.myminecraft.util.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

/**
 * 무기 제작·강화에 쓰는 재료 아이템.
 *
 * <p>목록은 전부 {@code config.yml}의 {@code materials} 항목에서 읽는다. 재료를 추가하거나 빼려면
 * 자바 코드를 고칠 필요 없이 설정만 손보면 되고, {@code /특수아이템소환}과 관리자메뉴·우편 첨부에
 * 쓰는 이름도 따라서 늘어난다.
 *
 * <p>지급용 이름은 표시 이름에서 <b>공백을 뺀 형태</b>다 (예: "단단한 돌 조각" → {@code 단단한돌조각}).
 * 명령어 인자가 공백으로 잘리기 때문이고, 확률 강화 두루마리가 쓰는 규칙과 같다.
 *
 * <p>각 재료는 {@code PersistentDataContainer}에 자기 id를 달고 다닌다. 재질이 겹쳐도
 * ({@code 녹슨 철광석 조각}과 바닐라 조철 등) 이 태그로 구분한다.
 */
public class MaterialManager {

    /**
     * 재료 하나의 정의.
     *
     * @param id          설정 키이자 아이템에 심는 식별자 (예: {@code hard_stone_fragment})
     * @param displayName 표시 이름 (예: "단단한 돌 조각")
     * @param grade       등급. 이름 색과 등급 줄에 쓰인다
     * @param material    리소스팩이 없을 때 보이는 바닐라 재질
     * @param modelData   커스텀 모델 데이터. 0이면 붙이지 않는다
     * @param description 아이템 설명(로어)에 들어갈 줄들
     */
    public record MaterialDef(String id, String displayName, GradeManager.Grade grade, Material material,
                              int modelData, List<String> description) {

        /** {@code /특수아이템소환}에 쓰는 이름 (공백 없음). */
        public String shortName() {
            return displayName.replace(" ", "");
        }
    }

    private final MyMinecraftPlugin plugin;
    private final NamespacedKey idKey;
    private final List<MaterialDef> materials = new ArrayList<>();

    public MaterialManager(MyMinecraftPlugin plugin) {
        this.plugin = plugin;
        this.idKey = new NamespacedKey(plugin, "material_id");
        load();
    }

    /** 설정에서 재료 목록을 다시 읽는다. */
    public void load() {
        materials.clear();
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("materials.list");
        if (section == null) {
            return;
        }
        for (String id : section.getKeys(false)) {
            ConfigurationSection entry = section.getConfigurationSection(id);
            if (entry == null) {
                continue;
            }
            String displayName = entry.getString("name", id);

            GradeManager.Grade grade;
            try {
                grade = GradeManager.Grade.valueOf(entry.getString("grade", "COMMON").toUpperCase());
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("재료 '" + id + "'의 등급 값이 잘못됐습니다: "
                        + entry.getString("grade") + " (일반으로 둡니다)");
                grade = GradeManager.Grade.COMMON;
            }

            Material material = Material.matchMaterial(entry.getString("material", "STONE"));
            if (material == null) {
                plugin.getLogger().warning("재료 '" + id + "'의 재질 이름이 잘못됐습니다: "
                        + entry.getString("material") + " (STONE으로 둡니다)");
                material = Material.STONE;
            }

            materials.add(new MaterialDef(id, displayName, grade, material,
                    entry.getInt("model-data", 0), entry.getStringList("description")));
        }
    }

    public List<MaterialDef> materials() {
        return List.copyOf(materials);
    }

    /** id 또는 지급용 이름(공백 없는 표시 이름)으로 찾는다. 없으면 null. */
    public MaterialDef find(String name) {
        for (MaterialDef def : materials) {
            if (def.id().equalsIgnoreCase(name) || def.shortName().equalsIgnoreCase(name)) {
                return def;
            }
        }
        return null;
    }

    public ItemStack createItem(MaterialDef def, int amount) {
        List<String> lore = new ArrayList<>();
        lore.add("§" + def.grade().colorCode() + "§l" + def.grade().displayName() + " §8| §7제작 재료");
        if (!def.description().isEmpty()) {
            lore.add("");
            lore.addAll(def.description().stream().map(line -> "§7" + line).toList());
        }

        ItemBuilder builder = new ItemBuilder(def.material())
                .name("§" + def.grade().colorCode() + def.displayName())
                .lore(lore)
                .amount(amount);
        // 고대 이상은 한눈에 띄도록 빛나게 둔다.
        if (def.grade().ordinal() >= GradeManager.Grade.ANCIENT.ordinal()) {
            builder.glow();
        }

        ItemStack item = builder.build();
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(idKey, PersistentDataType.STRING, def.id());
        if (def.modelData() != 0) {
            meta.setCustomModelData(def.modelData());
        }
        item.setItemMeta(meta);
        return item;
    }

    /** 이 아이템이 제작 재료면 그 정의를, 아니면 null을 돌려준다. */
    public MaterialDef definitionOf(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }
        String id = item.getItemMeta().getPersistentDataContainer().get(idKey, PersistentDataType.STRING);
        return id == null ? null : find(id);
    }

    public boolean isMaterial(ItemStack item) {
        return definitionOf(item) != null;
    }
}
