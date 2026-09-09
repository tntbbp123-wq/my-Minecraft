package com.tntbbp.myminecraft.manager;

import com.tntbbp.myminecraft.MyMinecraftPlugin;
import com.tntbbp.myminecraft.util.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** 서버/월드 선택 로비 메뉴에 표시할 항목 정의와 아이템 생성. */
public class WorldSelectManager {

    public record WorldEntry(String id, String name, String nameColor, String tag, String tagColor,
                              Material material, String world, List<String> extraLines) {
    }

    private final MyMinecraftPlugin plugin;
    private final List<WorldEntry> entries = new ArrayList<>();

    public WorldSelectManager(MyMinecraftPlugin plugin) {
        this.plugin = plugin;
        loadEntries();
    }

    private void loadEntries() {
        List<Map<?, ?>> list = plugin.getConfig().getMapList("worldselect.entries");
        for (Map<?, ?> raw : list) {
            String id = String.valueOf(raw.get("id"));
            String name = String.valueOf(raw.get("name"));
            String nameColor = raw.get("name-color") != null ? String.valueOf(raw.get("name-color")) : "f";
            String tag = raw.get("tag") != null ? String.valueOf(raw.get("tag")) : "";
            String tagColor = raw.get("tag-color") != null ? String.valueOf(raw.get("tag-color")) : "7";
            Material material = Material.matchMaterial(String.valueOf(raw.get("material")));
            if (material == null) {
                material = Material.CHEST;
            }
            String world = raw.get("world") != null ? String.valueOf(raw.get("world")) : null;
            List<String> extraLines = new ArrayList<>();
            Object rawExtra = raw.get("extra-lines");
            if (rawExtra instanceof List<?> rawList) {
                for (Object line : rawList) {
                    extraLines.add(String.valueOf(line));
                }
            }
            entries.add(new WorldEntry(id, name, nameColor, tag, tagColor, material, world, extraLines));
        }
    }

    public List<WorldEntry> entries() {
        return entries;
    }

    public String title() {
        return plugin.getConfig().getString("worldselect.title", "서버 선택");
    }

    public int pageSize() {
        return plugin.getConfig().getInt("worldselect.page-size", 7);
    }

    public int onlineCount(WorldEntry entry) {
        if (entry.world() == null) {
            return 0;
        }
        World world = Bukkit.getWorld(entry.world());
        return world != null ? world.getPlayers().size() : 0;
    }

    public ItemStack createDisplayItem(WorldEntry entry) {
        List<String> lore = new ArrayList<>();
        lore.add("§e" + onlineCount(entry) + "§f명 접속중");
        lore.add("§7접속 하시려면, 클릭하세요.");
        if (!entry.extraLines().isEmpty()) {
            lore.add("");
            lore.addAll(entry.extraLines());
        }

        String displayName = "§" + entry.nameColor() + entry.name();
        if (!entry.tag().isEmpty()) {
            displayName += " §f§l[§" + entry.tagColor() + "§l" + entry.tag() + "§f§l]";
        }

        return new ItemBuilder(entry.material())
                .name(displayName)
                .lore(lore)
                .build();
    }
}
