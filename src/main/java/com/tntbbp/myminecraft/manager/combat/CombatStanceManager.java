package com.tntbbp.myminecraft.manager.combat;

import com.tntbbp.myminecraft.MyMinecraftPlugin;
import com.tntbbp.myminecraft.util.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;

/**
 * 웅크리기 + L키(도전과제 화면 열기)로 진입/해제하는 전투 스킬 모드.
 * 진입 시 1~4번 슬롯의 원래 아이템을 저장해두고 스킬 아이템으로 임시 교체하며, 해제 시 복구한다.
 */
public class CombatStanceManager {

    private static final int SKILL_SLOT_COUNT = 4;

    private final MyMinecraftPlugin plugin;
    private final NamespacedKey skillItemKey;
    private final NamespacedKey skillSlotKey;
    private final Map<UUID, ItemStack[]> savedHotbars = new HashMap<>();
    private final Map<UUID, Long> lastToggleMillis = new HashMap<>();

    public CombatStanceManager(MyMinecraftPlugin plugin) {
        this.plugin = plugin;
        this.skillItemKey = new NamespacedKey(plugin, "combat_stance_skill");
        this.skillSlotKey = new NamespacedKey(plugin, "combat_stance_skill_slot");
    }

    private long toggleCooldownMillis() {
        return plugin.getConfig().getLong("combat-stance.toggle-cooldown-millis", 500);
    }

    public boolean isInStance(UUID uuid) {
        return savedHotbars.containsKey(uuid);
    }

    /** 쿨다운을 통과했다면 진입/해제를 전환한다. */
    public void toggle(Player player) {
        UUID uuid = player.getUniqueId();
        // 봉인 중에는 전투모드에 새로 진입할 수 없다 (이미 진입한 상태의 해제는 허용).
        if (!isInStance(uuid) && plugin.getGleipnirManager().isSealed(uuid)) {
            player.sendMessage("§7봉인되어 전투모드에 진입할 수 없습니다.");
            return;
        }
        long now = System.currentTimeMillis();
        Long last = lastToggleMillis.get(uuid);
        if (last != null && now - last < toggleCooldownMillis()) {
            return;
        }
        lastToggleMillis.put(uuid, now);

        if (isInStance(uuid)) {
            exit(player);
        } else {
            enter(player);
        }
    }

    public void enter(Player player) {
        UUID uuid = player.getUniqueId();
        if (isInStance(uuid)) {
            return;
        }

        PlayerInventory inventory = player.getInventory();
        ItemStack[] saved = new ItemStack[SKILL_SLOT_COUNT];
        for (int slot = 0; slot < SKILL_SLOT_COUNT; slot++) {
            saved[slot] = inventory.getItem(slot);
            inventory.setItem(slot, createSkillItem(slot));
        }
        savedHotbars.put(uuid, saved);

        player.playSound(player.getLocation(), Sound.ITEM_TRIDENT_RIPTIDE_3, 1.0f, 1.4f);
        player.sendMessage("§b§l[전투모드] §f진입했습니다. §7(1~4번 슬롯: 스킬)");
    }

    public void exit(Player player) {
        ItemStack[] saved = savedHotbars.remove(player.getUniqueId());
        if (saved == null) {
            return;
        }
        restoreHotbar(player, saved);
        player.playSound(player.getLocation(), Sound.ITEM_TRIDENT_RIPTIDE_1, 1.0f, 0.8f);
        player.sendMessage("§7§l[전투모드] §f해제되었습니다.");
    }

    /** 사망 등 인벤토리를 직접 복구하면 안 되는 상황에서, 상태만 정리하고 저장된 원본 아이템을 반환한다. */
    public ItemStack[] clearStanceAndGetSaved(UUID uuid) {
        return savedHotbars.remove(uuid);
    }

    /** 저장해 둔 원래 아이템을 핫바로 되돌린다. 사망 시 keepInventory 처리에서도 쓴다. */
    public void restoreSaved(Player player, ItemStack[] saved) {
        restoreHotbar(player, saved);
    }

    /**
     * 핫바 1~4번에 원래 아이템을 되돌린다.
     *
     * <p>그 칸에 스킬 아이템이 아닌 게 들어와 있으면 <b>덮어쓰지 않고 인벤토리로 옮긴다.</b> 예전에는 그냥
     * 덮어써서, 숫자키 바꾸기나 F(손 바꾸기)로 칸에 들어온 검·방패가 전투모드를 끄는 순간 사라졌다.
     * 입구는 막았지만, 다른 경로로 들어와도 아이템이 없어지지 않게 여기서도 지킨다.
     */
    private void restoreHotbar(Player player, ItemStack[] saved) {
        PlayerInventory inventory = player.getInventory();
        List<ItemStack> displaced = new ArrayList<>();
        for (int slot = 0; slot < SKILL_SLOT_COUNT; slot++) {
            ItemStack current = inventory.getItem(slot);
            if (current != null && !current.getType().isAir() && !isSkillItem(current)) {
                displaced.add(current);
            }
            inventory.setItem(slot, saved[slot]);
        }
        // 스킬 아이템이 다른 칸으로 옮겨져 있었다면 함께 치운다.
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            if (isSkillItem(inventory.getItem(slot))) {
                inventory.setItem(slot, null);
            }
        }
        if (isSkillItem(inventory.getItemInOffHand())) {
            inventory.setItemInOffHand(null);
        }
        for (ItemStack item : displaced) {
            inventory.addItem(item).values()
                    .forEach(leftover -> player.getWorld().dropItem(player.getLocation(), leftover));
        }
    }

    /** 서버 종료/플러그인 비활성화 시 전투모드 중인 모든 플레이어의 핫바를 원상복구한다. */
    public void restoreAll() {
        for (Map.Entry<UUID, ItemStack[]> entry : savedHotbars.entrySet()) {
            Player player = plugin.getServer().getPlayer(entry.getKey());
            if (player != null) {
                restoreHotbar(player, entry.getValue());
            }
        }
        savedHotbars.clear();
    }

    public boolean isSkillItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        return item.getItemMeta().getPersistentDataContainer().has(skillItemKey, PersistentDataType.BYTE);
    }

    public int getSkillSlot(ItemStack item) {
        if (!isSkillItem(item)) {
            return -1;
        }
        Integer slot = item.getItemMeta().getPersistentDataContainer().get(skillSlotKey, PersistentDataType.INTEGER);
        return slot == null ? -1 : slot;
    }

    private ItemStack createSkillItem(int slot) {
        List<Map<?, ?>> skills = plugin.getConfig().getMapList("combat-stance.skills");
        Material material = Material.NETHER_STAR;
        String name = "§b§l스킬 " + (slot + 1);
        if (slot < skills.size()) {
            Map<?, ?> entry = skills.get(slot);
            Object materialName = entry.get("material");
            if (materialName != null) {
                Material parsed = Material.matchMaterial(String.valueOf(materialName));
                if (parsed != null) {
                    material = parsed;
                }
            }
            Object entryName = entry.get("name");
            if (entryName != null) {
                name = String.valueOf(entryName);
            }
        }

        ItemStack item = new ItemBuilder(material)
                .name(name)
                .lore(List.of(
                        "§7우클릭: 스킬 발동",
                        "§8(미구현 - 추후 업데이트 예정)"
                ))
                .build();

        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(skillItemKey, PersistentDataType.BYTE, (byte) 1);
        meta.getPersistentDataContainer().set(skillSlotKey, PersistentDataType.INTEGER, slot);
        item.setItemMeta(meta);
        return item;
    }

    /** 스킬 슬롯 우클릭 시 실제 효과를 여기서 구현한다 (현재는 자리표시자). */
    public void executeSkill(Player player, int slot) {
        if (plugin.getGleipnirManager().isSealed(player.getUniqueId())) {
            player.sendMessage("§7봉인되어 스킬을 쓸 수 없습니다.");
            return;
        }
        player.sendMessage("§7[전투모드] 스킬 " + (slot + 1) + "은(는) 아직 구현되지 않았습니다.");
    }
}
