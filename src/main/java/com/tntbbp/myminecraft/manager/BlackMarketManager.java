package com.tntbbp.myminecraft.manager;

import com.tntbbp.myminecraft.MyMinecraftPlugin;
import com.tntbbp.myminecraft.util.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.block.DoubleChest;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;
import java.util.UUID;

/**
 * 암시장 아이템(1단계: 일괄 약탈 주문서, 치명적 덫 상자, 화염병/연막탄) 관리.
 * 위치 추적류(나침반들)와 디스코드 알림은 다음 단계에서 별도로 다룬다.
 */
public class BlackMarketManager {

    private final MyMinecraftPlugin plugin;
    private final NamespacedKey lootAllScrollKey;
    private final NamespacedKey trapKitKey;
    private final NamespacedKey molotovKey;
    private final NamespacedKey smokeBombKey;
    private final NamespacedKey trapOwnerKey;
    private final NamespacedKey trapModeKey;

    public BlackMarketManager(MyMinecraftPlugin plugin) {
        this.plugin = plugin;
        this.lootAllScrollKey = new NamespacedKey(plugin, "loot_all_scroll");
        this.trapKitKey = new NamespacedKey(plugin, "trap_kit");
        this.molotovKey = new NamespacedKey(plugin, "molotov");
        this.smokeBombKey = new NamespacedKey(plugin, "smoke_bomb");
        this.trapOwnerKey = new NamespacedKey(plugin, "trap_owner");
        this.trapModeKey = new NamespacedKey(plugin, "trap_mode");
    }

    // ----- 일괄 약탈 주문서 -----

    public ItemStack createLootAllScroll(int amount) {
        ItemStack item = new ItemBuilder(matchOrDefault("blackmarket.loot-all-scroll.material", Material.PAPER))
                .name("§6일괄 약탈 주문서")
                .lore(List.of(
                        "§7상자를 들고 §e쉬프트+우클릭§7하면",
                        "§7상자 안의 모든 아이템을 즉시",
                        "§7내 인벤토리로 쓸어 담습니다.",
                        "§7(사용 시 1개 소모)"
                ))
                .amount(amount)
                .glow()
                .build();
        return tagBoolean(item, lootAllScrollKey, modelData("loot-all-scroll"));
    }

    public boolean isLootAllScroll(ItemStack item) {
        return hasFlag(item, lootAllScrollKey);
    }

    // ----- 함정 설치 키트 -----

    public ItemStack createTrapKit(int amount) {
        ItemStack item = new ItemBuilder(matchOrDefault("blackmarket.trap-kit.material", Material.TRIPWIRE_HOOK))
                .name("§4함정 설치 키트")
                .lore(List.of(
                        "§7상자를 들고 우클릭하면 그 상자에",
                        "§7함정을 설치합니다 (함정 상자로 변경됨).",
                        "§7설치자 본인이 아닌 다른 사람이 열면",
                        trapModeDescription(),
                        "§7(설치 1회당 1개 소모, 발동 시 함정은 해제됨)"
                ))
                .amount(amount)
                .glow()
                .build();
        return tagBoolean(item, trapKitKey, modelData("trap-kit"));
    }

    public boolean isTrapKit(ItemStack item) {
        return hasFlag(item, trapKitKey);
    }

    private String trapModeDescription() {
        return isTntMode()
                ? "§c강한 폭발(TNT)이 즉시 발동됩니다."
                : "§c강한 독 + 위더 디버프가 발동됩니다.";
    }

    private boolean isTntMode() {
        return "TNT".equalsIgnoreCase(plugin.getConfig().getString("blackmarket.trap-kit.mode", "DEBUFF"));
    }

    public int poisonDurationSeconds() {
        return plugin.getConfig().getInt("blackmarket.trap-kit.poison-duration-seconds", 10);
    }

    public int witherDurationSeconds() {
        return plugin.getConfig().getInt("blackmarket.trap-kit.wither-duration-seconds", 10);
    }

    public boolean trapModeIsTnt() {
        return isTntMode();
    }

    /**
     * 상자에 함정을 설치한다. 이미 다른 함정이 있으면 덮어쓴다.
     * 블록 재질(일반 상자/덫 상자)은 그대로 두고 PDC 태그만 붙인다 — 재질을 바꾸면
     * 상자 내용물이 사라지는 문제가 있어서, 실제 발동 로직은 재질과 무관하게 동작한다.
     */
    public void installTrap(Block chestBlock, UUID owner) {
        if (!(chestBlock.getState() instanceof Chest chest)) {
            return;
        }
        chest.getPersistentDataContainer().set(trapOwnerKey, PersistentDataType.STRING, owner.toString());
        chest.getPersistentDataContainer().set(trapModeKey, PersistentDataType.STRING, isTntMode() ? "TNT" : "DEBUFF");
        chest.update();
    }

    /** 이 블록(또는 더블 상자의 반대편)에 함정이 걸려 있는지, 걸려 있다면 어떤 설정인지 반환한다. */
    public TrapInfo findTrap(Block chestBlock) {
        if (!(chestBlock.getState() instanceof Chest chest)) {
            return null;
        }
        TrapInfo direct = readTrap(chest);
        if (direct != null) {
            return direct;
        }
        if (chest.getInventory().getHolder() instanceof DoubleChest doubleChest) {
            TrapInfo left = asTrapInfo(doubleChest.getLeftSide());
            if (left != null) {
                return left;
            }
            TrapInfo right = asTrapInfo(doubleChest.getRightSide());
            if (right != null) {
                return right;
            }
        }
        return null;
    }

    private TrapInfo asTrapInfo(InventoryHolder holder) {
        return holder instanceof Chest sideChest ? readTrap(sideChest) : null;
    }

    private TrapInfo readTrap(Chest chest) {
        String ownerRaw = chest.getPersistentDataContainer().get(trapOwnerKey, PersistentDataType.STRING);
        if (ownerRaw == null) {
            return null;
        }
        String mode = chest.getPersistentDataContainer().get(trapModeKey, PersistentDataType.STRING);
        try {
            return new TrapInfo(chest, UUID.fromString(ownerRaw), "TNT".equalsIgnoreCase(mode));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    /** 함정을 1회용으로 해제한다 (발동 후 호출). */
    public void clearTrap(Chest chest) {
        chest.getPersistentDataContainer().remove(trapOwnerKey);
        chest.getPersistentDataContainer().remove(trapModeKey);
        chest.update();
    }

    public record TrapInfo(Chest chest, UUID owner, boolean tnt) {
    }

    // ----- 화염병 -----

    public ItemStack createMolotov(int amount) {
        ItemStack item = new ItemBuilder(matchOrDefault("blackmarket.molotov.material", Material.SPLASH_POTION))
                .name("§c화염병")
                .lore(List.of(
                        "§7던지면 착탄 지점 주변을 불태우고,",
                        "§7주변 생물에게 짧게 불이 붙습니다.",
                        "§7목재 건물 안에서 특히 위협적입니다."
                ))
                .amount(amount)
                .build();
        return tagBoolean(item, molotovKey, modelData("molotov"));
    }

    public boolean isMolotov(ItemStack item) {
        return hasFlag(item, molotovKey);
    }

    public double molotovRadius() {
        return plugin.getConfig().getDouble("blackmarket.molotov.radius", 3.0);
    }

    public int molotovFireTicks() {
        return plugin.getConfig().getInt("blackmarket.molotov.entity-fire-ticks", 100);
    }

    // ----- 연막탄 -----

    public ItemStack createSmokeBomb(int amount) {
        ItemStack item = new ItemBuilder(matchOrDefault("blackmarket.smoke-bomb.material", Material.SPLASH_POTION))
                .name("§7연막탄")
                .lore(List.of(
                        "§7던지면 착탄 지점에 짙은 연막이 퍼져",
                        "§7범위 안의 대상에게 실명 효과를 겁니다.",
                        "§7전투 중 도주/교란 용도로 유용합니다."
                ))
                .amount(amount)
                .build();
        return tagBoolean(item, smokeBombKey, modelData("smoke-bomb"));
    }

    public boolean isSmokeBomb(ItemStack item) {
        return hasFlag(item, smokeBombKey);
    }

    public double smokeBombRadius() {
        return plugin.getConfig().getDouble("blackmarket.smoke-bomb.radius", 4.0);
    }

    public int smokeBombDurationTicks() {
        return plugin.getConfig().getInt("blackmarket.smoke-bomb.duration-ticks", 100);
    }

    // ----- 공통 헬퍼 -----

    private Material matchOrDefault(String configPath, Material fallback) {
        Material material = Material.matchMaterial(plugin.getConfig().getString(configPath, fallback.name()));
        return material != null ? material : fallback;
    }

    private int modelData(String path) {
        return plugin.getConfig().getInt("blackmarket." + path + ".model-data", 0);
    }

    private ItemStack tagBoolean(ItemStack item, NamespacedKey key, int modelData) {
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
        if (modelData != 0) {
            meta.setCustomModelData(modelData);
        }
        item.setItemMeta(meta);
        return item;
    }

    private boolean hasFlag(ItemStack item, NamespacedKey key) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        Byte flag = item.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.BYTE);
        return flag != null && flag == (byte) 1;
    }

    public NamespacedKey molotovKey() {
        return molotovKey;
    }

    public NamespacedKey smokeBombKey() {
        return smokeBombKey;
    }
}
