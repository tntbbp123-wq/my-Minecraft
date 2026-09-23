package com.tntbbp.myminecraft.listener.item;

import com.tntbbp.myminecraft.MyMinecraftPlugin;
import com.tntbbp.myminecraft.manager.item.GradeManager;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.inventory.PrepareSmithingEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.scheduler.BukkitTask;

/**
 * 바닐라 장비(무기·방어구·도구 등 내구도가 있는 아이템)에 기본 등급 <b>일반</b>을 붙인다.
 *
 * <p>아이템이 플레이어 손에 들어오는 길은 제작·줍기·상자·주민 거래·{@code /give}·크리에이티브 등 아주 많다.
 * 흔한 길(제작 결과, 줍기)은 그 자리에서 붙이고, 나머지는 {@value #SWEEP_INTERVAL_TICKS}틱마다
 * 접속 중인 플레이어의 인벤토리를 훑어서 등급이 없는 장비에 붙인다. 이미 등급이 있는 아이템
 * (커스텀 무기 등)은 건드리지 않고, 한 번 붙은 아이템은 다시 보지 않는다.
 */
public class GradeListener implements Listener {

    private static final long SWEEP_INTERVAL_TICKS = 40L;
    /** {@link PlayerInventory#getContents()}에서 보조손 칸의 위치 (0~35 보관함, 36~39 방어구, 40 보조손). */
    private static final int OFFHAND_SLOT = 40;
    /** 같은 배열에서 흉갑(겉날개) 칸의 위치. */
    private static final int CHEST_SLOT = 38;

    private final MyMinecraftPlugin plugin;
    private BukkitTask sweepTask;

    public GradeListener(MyMinecraftPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        sweepTask = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            for (Player player : plugin.getServer().getOnlinePlayers()) {
                sweep(player);
            }
        }, SWEEP_INTERVAL_TICKS, SWEEP_INTERVAL_TICKS);
    }

    public void stop() {
        if (sweepTask != null) {
            sweepTask.cancel();
        }
    }

    /**
     * 제작대 결과 칸. 결과를 꺼내기 전부터 "등급: 일반"이 보이게 한다.
     * 다른 리스너(아르테미스 세트 제작 판정 등)가 결과를 정한 뒤에 보도록 가장 늦게 받는다.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        ItemStack result = event.getInventory().getResult();
        ItemStack graded = gradedCopy(result);
        if (graded != null) {
            event.getInventory().setResult(graded);
        }
    }

    /** 대장장이 작업대(다이아 → 네더라이트 등) 결과 칸. */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepareSmithing(PrepareSmithingEvent event) {
        ItemStack graded = gradedCopy(event.getResult());
        if (graded != null) {
            event.setResult(graded);
        }
    }

    /** 바닥에서 줍는 장비(몹 드롭·낚시 등). 인벤토리에 들어가기 전에 붙인다. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player)) {
            return;
        }
        Item entity = event.getItem();
        ItemStack graded = gradedCopy(entity.getItemStack());
        if (graded != null) {
            entity.setItemStack(graded);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        sweep(event.getPlayer());
    }

    /** 등급이 없는 장비면 일반 등급을 붙인 복사본, 아니면 null. */
    private ItemStack gradedCopy(ItemStack item) {
        GradeManager gradeManager = plugin.getGradeManager();
        if (!gradeManager.isGradable(item) || gradeManager.hasGrade(item)) {
            return null;
        }
        ItemStack copy = item.clone();
        gradeManager.ensureDefaultGrade(copy);
        return copy;
    }

    /**
     * 인벤토리 전체(방어구·보조손 포함)를 훑는다.
     *
     * <p>활을 당기거나 방패를 들고 있는 중이면 <b>지금 쓰는 손의 아이템은 건너뛴다.</b> 들고 있는 아이템의
     * 정보를 바꾸면 사용 동작이 끊기기 때문이다. 겉날개로 나는 중이면 흉갑 칸도 같은 이유로 건너뛴다.
     * 건너뛴 아이템은 다음 훑기 때 붙는다.
     */
    private void sweep(Player player) {
        GradeManager gradeManager = plugin.getGradeManager();
        PlayerInventory inventory = player.getInventory();
        boolean usingItem = player.isHandRaised();
        boolean gliding = player.isGliding();
        int heldSlot = inventory.getHeldItemSlot();
        ItemStack[] contents = inventory.getContents();
        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack item = contents[slot];
            if (usingItem && (slot == heldSlot || slot == OFFHAND_SLOT)) {
                continue;
            }
            if (gliding && slot == CHEST_SLOT) {
                continue;
            }
            if (gradeManager.isGradable(item) && !gradeManager.hasGrade(item)) {
                gradeManager.ensureDefaultGrade(item);
                inventory.setItem(slot, item);
            }
        }
    }
}
