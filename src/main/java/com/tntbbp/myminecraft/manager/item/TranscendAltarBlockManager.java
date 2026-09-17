package com.tntbbp.myminecraft.manager.item;

import com.tntbbp.myminecraft.MyMinecraftPlugin;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

/**
 * <초월의 제단>을 실제 블록처럼 세계에 세우기 위한 커스텀 블록.
 * 진짜 블록 타입을 새로 만들 수는 없어서, ItemDisplay 엔티티에 커스텀 아이템(리소스팩의
 * CustomModelData로 원하는 모양으로 바꿀 수 있음)을 띄워 "블록"처럼 보이게 하고,
 * 오른쪽 클릭하면 초월의 제단 GUI가 열리도록 표식(PDC)을 붙인다.
 */
public class TranscendAltarBlockManager {

    private final MyMinecraftPlugin plugin;
    private final NamespacedKey markerKey;

    public TranscendAltarBlockManager(MyMinecraftPlugin plugin) {
        this.plugin = plugin;
        this.markerKey = new NamespacedKey(plugin, "transcend_altar_marker");
    }

    public Material displayMaterial() {
        Material material = Material.matchMaterial(plugin.getConfig().getString("transcend-altar.material", "END_CRYSTAL"));
        return material != null ? material : Material.END_CRYSTAL;
    }

    public int modelData() {
        return plugin.getConfig().getInt("transcend-altar.model-data", 0);
    }

    public float scale() {
        return (float) plugin.getConfig().getDouble("transcend-altar.scale", 1.5);
    }

    /** 지정한 위치에 초월의 제단 커스텀 블록(ItemDisplay)을 세운다. */
    public ItemDisplay place(Location location) {
        ItemStack icon = new ItemStack(displayMaterial());
        ItemMeta meta = icon.getItemMeta();
        int modelData = modelData();
        if (modelData != 0) {
            meta.setCustomModelData(modelData);
        }
        icon.setItemMeta(meta);

        float s = scale();
        return location.getWorld().spawn(location, ItemDisplay.class, display -> {
            display.setItemStack(icon);
            display.setBillboard(Display.Billboard.CENTER);
            display.setTransformation(new Transformation(
                    new Vector3f(0, 0, 0),
                    new AxisAngle4f(0, 0, 0, 1),
                    new Vector3f(s, s, s),
                    new AxisAngle4f(0, 0, 0, 1)));
            display.getPersistentDataContainer().set(markerKey, PersistentDataType.BYTE, (byte) 1);
            display.setPersistent(true);
        });
    }

    public boolean isAltarMarker(Entity entity) {
        if (!(entity instanceof ItemDisplay)) {
            return false;
        }
        Byte flag = entity.getPersistentDataContainer().get(markerKey, PersistentDataType.BYTE);
        return flag != null && flag == (byte) 1;
    }
}
