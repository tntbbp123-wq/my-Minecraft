package com.tntbbp.myminecraft.gui.admin;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.HashMap;
import java.util.Map;

/**
 * 관리자 메뉴의 첫 화면과 분류별 아이템 화면이 함께 쓰는 Holder.
 *
 * <p>화면마다 Holder를 따로 두지 않는 이유는, 클릭·드래그 막기를 {@code GUIListener}의 기존 분기
 * 하나로 그대로 받기 위해서다. 어느 화면인지는 {@link #getCategory()}로 가른다(첫 화면이면 null).
 */
public class AdminMenuHolder implements InventoryHolder {

    /** 아이템 칸이 아닌 이동용 칸. */
    public enum Nav {
        CLOSE,
        /** 분류 화면에서 관리자 메뉴 첫 화면으로 */
        BACK,
        PREV_PAGE,
        NEXT_PAGE
    }

    private Inventory inventory;
    private final AdminItemCategory category;
    private final int page;
    private final Map<Integer, String> slotToSuggestedCommand = new HashMap<>();
    private final Map<Integer, String> slotToGiveItemName = new HashMap<>();
    private final Map<Integer, AdminItemCategory> slotToCategory = new HashMap<>();
    private final Map<Integer, Nav> slotToNav = new HashMap<>();

    /** 첫 화면. */
    public AdminMenuHolder() {
        this(null, 0);
    }

    /** 분류별 아이템 화면. */
    public AdminMenuHolder(AdminItemCategory category, int page) {
        this.category = category;
        this.page = page;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    /** 지금 보고 있는 분류. 첫 화면이면 null. */
    public AdminItemCategory getCategory() {
        return category;
    }

    public int getPage() {
        return page;
    }

    public void mapCommand(int slot, String suggestedCommand) {
        slotToSuggestedCommand.put(slot, suggestedCommand);
    }

    public String getSuggestedCommand(int slot) {
        return slotToSuggestedCommand.get(slot);
    }

    public void mapGiveItem(int slot, String itemName) {
        slotToGiveItemName.put(slot, itemName);
    }

    public String getGiveItemName(int slot) {
        return slotToGiveItemName.get(slot);
    }

    public void mapCategory(int slot, AdminItemCategory category) {
        slotToCategory.put(slot, category);
    }

    public AdminItemCategory getCategoryButton(int slot) {
        return slotToCategory.get(slot);
    }

    public void mapNav(int slot, Nav nav) {
        slotToNav.put(slot, nav);
    }

    public Nav getNav(int slot) {
        return slotToNav.get(slot);
    }
}
