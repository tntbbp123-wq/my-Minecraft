package com.tntbbp.myminecraft.manager.economy;

import com.tntbbp.myminecraft.MyMinecraftPlugin;
import com.tntbbp.myminecraft.util.ItemBuilder;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.block.DoubleChest;
import org.bukkit.entity.Player;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 암시장 아이템 관리.
 * 1단계: 일괄 약탈 주문서, 함정 설치 키트, 화염병, 연막탄.
 * 2단계: 타일 밀도 나침반, 발자국 추적기, 혈흔 나침반, 소음 차단 포션.
 * 디스코드 침입 알림은 다음 단계에서 별도로 다룬다.
 */
public class BlackMarketManager {

    private final MyMinecraftPlugin plugin;
    private final NamespacedKey lootAllScrollKey;
    private final NamespacedKey trapKitKey;
    private final NamespacedKey molotovKey;
    private final NamespacedKey smokeBombKey;
    private final NamespacedKey trapOwnerKey;
    private final NamespacedKey trapModeKey;
    private final NamespacedKey densityCompassKey;
    private final NamespacedKey footprintTrackerKey;
    private final NamespacedKey bloodCompassKey;
    private final NamespacedKey silencePotionKey;

    private final Map<UUID, Long> densityCompassCooldowns = new HashMap<>();
    private final Map<UUID, Long> footprintMarked = new HashMap<>();
    private final Map<UUID, UUID> lastKiller = new HashMap<>();
    private final Map<UUID, Long> silenced = new HashMap<>();

    public BlackMarketManager(MyMinecraftPlugin plugin) {
        this.plugin = plugin;
        this.lootAllScrollKey = new NamespacedKey(plugin, "loot_all_scroll");
        this.trapKitKey = new NamespacedKey(plugin, "trap_kit");
        this.molotovKey = new NamespacedKey(plugin, "molotov");
        this.smokeBombKey = new NamespacedKey(plugin, "smoke_bomb");
        this.trapOwnerKey = new NamespacedKey(plugin, "trap_owner");
        this.trapModeKey = new NamespacedKey(plugin, "trap_mode");
        this.densityCompassKey = new NamespacedKey(plugin, "density_compass");
        this.footprintTrackerKey = new NamespacedKey(plugin, "footprint_tracker");
        this.bloodCompassKey = new NamespacedKey(plugin, "blood_compass");
        this.silencePotionKey = new NamespacedKey(plugin, "silence_potion");
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
     * 상자/배럴/셜커 상자 등 컨테이너에 함정을 설치한다. 이미 다른 함정이 있으면 덮어쓴다.
     * 블록 재질(일반 상자/덫 상자)은 그대로 두고 PDC 태그만 붙인다 — 재질을 바꾸면
     * 상자 내용물이 사라지는 문제가 있어서, 실제 발동 로직은 재질과 무관하게 동작한다.
     *
     * @return 설치에 성공하면 true, 컨테이너가 아니라 설치할 수 없으면 false
     */
    public boolean installTrap(Block containerBlock, UUID owner) {
        if (!(containerBlock.getState() instanceof Container container)) {
            return false;
        }
        container.getPersistentDataContainer().set(trapOwnerKey, PersistentDataType.STRING, owner.toString());
        container.getPersistentDataContainer().set(trapModeKey, PersistentDataType.STRING, isTntMode() ? "TNT" : "DEBUFF");
        container.update();
        return true;
    }

    /** 이 블록(또는 더블 상자의 반대편)에 함정이 걸려 있는지, 걸려 있다면 어떤 설정인지 반환한다. */
    public TrapInfo findTrap(Block containerBlock) {
        if (!(containerBlock.getState() instanceof Container container)) {
            return null;
        }
        TrapInfo direct = readTrap(container);
        if (direct != null) {
            return direct;
        }
        if (container.getInventory().getHolder() instanceof DoubleChest doubleChest) {
            TrapInfo left = asTrapInfo(doubleChest.getLeftSide());
            if (left != null) {
                return left;
            }
            return asTrapInfo(doubleChest.getRightSide());
        }
        return null;
    }

    private TrapInfo asTrapInfo(InventoryHolder holder) {
        return holder instanceof Container sideContainer ? readTrap(sideContainer) : null;
    }

    private TrapInfo readTrap(Container container) {
        String ownerRaw = container.getPersistentDataContainer().get(trapOwnerKey, PersistentDataType.STRING);
        if (ownerRaw == null) {
            return null;
        }
        String mode = container.getPersistentDataContainer().get(trapModeKey, PersistentDataType.STRING);
        try {
            return new TrapInfo(container, UUID.fromString(ownerRaw), "TNT".equalsIgnoreCase(mode));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    /** 함정을 1회용으로 해제한다 (발동 후 호출). */
    public void clearTrap(Container container) {
        container.getPersistentDataContainer().remove(trapOwnerKey);
        container.getPersistentDataContainer().remove(trapModeKey);
        container.update();
    }

    public record TrapInfo(Container container, UUID owner, boolean tnt) {
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

    // ----- 타일 밀도 나침반 -----

    public ItemStack createDensityCompass(int amount) {
        ItemStack item = new ItemBuilder(matchOrDefault("blackmarket.density-compass.material", Material.COMPASS))
                .name("§b타일 밀도 나침반")
                .lore(List.of(
                        "§7우클릭하면 주변에 상자/화로가",
                        "§7많이 몰려 있는 방향을 대략적으로",
                        "§7가리키도록 바늘이 맞춰집니다.",
                        "§7(지하 기지, 숨겨진 창고 탐색용)"
                ))
                .amount(amount)
                .glow()
                .build();
        return tagBoolean(item, densityCompassKey, modelData("density-compass"));
    }

    public boolean isDensityCompass(ItemStack item) {
        return hasFlag(item, densityCompassKey);
    }

    public int densityCompassRadius() {
        return plugin.getConfig().getInt("blackmarket.density-compass.radius", 64);
    }

    public int densityCompassCooldownSeconds() {
        return plugin.getConfig().getInt("blackmarket.density-compass.cooldown-seconds", 30);
    }

    public long densityCompassRemainingCooldown(UUID uuid) {
        Long lastUse = densityCompassCooldowns.get(uuid);
        if (lastUse == null) {
            return 0;
        }
        long remaining = densityCompassCooldownSeconds() - (System.currentTimeMillis() - lastUse) / 1000;
        return Math.max(0, remaining);
    }

    /**
     * 플레이어 주변 청크(로드된 것만)를 훑어 상자/화로류 타일 엔티티의 무게중심 위치를 찾는다.
     * 아무것도 못 찾으면 null.
     */
    public Location findDensityTarget(Player player) {
        densityCompassCooldowns.put(player.getUniqueId(), System.currentTimeMillis());

        int radius = densityCompassRadius();
        int chunkRadius = (radius >> 4) + 1;
        Location origin = player.getLocation();
        Chunk centerChunk = origin.getChunk();

        double sumX = 0;
        double sumY = 0;
        double sumZ = 0;
        int count = 0;

        for (int cx = -chunkRadius; cx <= chunkRadius; cx++) {
            for (int cz = -chunkRadius; cz <= chunkRadius; cz++) {
                if (!player.getWorld().isChunkLoaded(centerChunk.getX() + cx, centerChunk.getZ() + cz)) {
                    continue;
                }
                Chunk chunk = player.getWorld().getChunkAt(centerChunk.getX() + cx, centerChunk.getZ() + cz);
                for (BlockState state : chunk.getTileEntities()) {
                    if (!isDensityTarget(state.getType())) {
                        continue;
                    }
                    if (state.getLocation().distanceSquared(origin) > (double) radius * radius) {
                        continue;
                    }
                    sumX += state.getX();
                    sumY += state.getY();
                    sumZ += state.getZ();
                    count++;
                }
            }
        }

        if (count == 0) {
            return null;
        }
        return new Location(player.getWorld(), sumX / count, sumY / count, sumZ / count);
    }

    private boolean isDensityTarget(Material type) {
        return type == Material.CHEST || type == Material.TRAPPED_CHEST || type == Material.BARREL
                || type == Material.FURNACE || type == Material.BLAST_FURNACE || type == Material.SMOKER
                || type.name().endsWith("SHULKER_BOX");
    }

    // ----- 발자국 추적기 -----

    public ItemStack createFootprintTracker(int amount) {
        ItemStack item = new ItemBuilder(matchOrDefault("blackmarket.footprint-tracker.material", Material.ARROW))
                .name("§2발자국 추적기")
                .lore(List.of(
                        "§7던져서 플레이어를 맞히면",
                        "§7그 플레이어가 지나간 자리에",
                        "§7" + (footprintDurationTicks() / 20) + "초 동안 흔적 파티클이 남습니다.",
                        "§7흔적을 따라가 아지트를 찾아낼 수 있습니다."
                ))
                .amount(amount)
                .build();
        return tagBoolean(item, footprintTrackerKey, modelData("footprint-tracker"));
    }

    public boolean isFootprintTracker(ItemStack item) {
        return hasFlag(item, footprintTrackerKey);
    }

    public int footprintDurationTicks() {
        return plugin.getConfig().getInt("blackmarket.footprint-tracker.duration-ticks", 3600);
    }

    public void markFootprint(UUID uuid) {
        footprintMarked.put(uuid, System.currentTimeMillis() + footprintDurationTicks() * 50L);
    }

    public boolean isFootprintMarked(UUID uuid) {
        Long expiry = footprintMarked.get(uuid);
        return expiry != null && expiry > System.currentTimeMillis();
    }

    /** 흔적 파티클 스폰용으로, 현재 표시 중인 UUID 집합을 순회할 때 사용한다. */
    public Map<UUID, Long> footprintMarkedView() {
        return footprintMarked;
    }

    // ----- 흔적 파티클 틱 -----

    private org.bukkit.scheduler.BukkitTask footprintTask;

    public void start() {
        footprintTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tickFootprints, 5L, 5L);
    }

    public void stop() {
        if (footprintTask != null) {
            footprintTask.cancel();
        }
    }

    private void tickFootprints() {
        if (footprintMarked.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        footprintMarked.entrySet().removeIf(entry -> entry.getValue() <= now);
        for (UUID uuid : footprintMarked.keySet()) {
            Player player = plugin.getServer().getPlayer(uuid);
            if (player == null || !player.isOnline()) {
                continue;
            }
            player.getWorld().spawnParticle(org.bukkit.Particle.DUST, player.getLocation(), 6, 0.2, 0.05, 0.2,
                    new org.bukkit.Particle.DustOptions(org.bukkit.Color.fromRGB(200, 30, 30), 1.2f));
        }
    }

    // ----- 혈흔 나침반 -----

    public ItemStack createBloodCompass(int amount) {
        ItemStack item = new ItemBuilder(matchOrDefault("blackmarket.blood-compass.material", Material.COMPASS))
                .name("§4혈흔 나침반")
                .lore(List.of(
                        "§7우클릭하면 나를 죽이고 도망간",
                        "§7상대의 대략적인 현재 위치 방향으로",
                        "§7바늘이 맞춰집니다.",
                        "§7(상대가 접속 중이어야 작동)"
                ))
                .amount(amount)
                .glow()
                .build();
        return tagBoolean(item, bloodCompassKey, modelData("blood-compass"));
    }

    public boolean isBloodCompass(ItemStack item) {
        return hasFlag(item, bloodCompassKey);
    }

    public double bloodCompassJitterRadius() {
        return plugin.getConfig().getDouble("blackmarket.blood-compass.jitter-radius", 20.0);
    }

    public void recordKill(UUID victim, UUID killer) {
        lastKiller.put(victim, killer);
    }

    public UUID getLastKiller(UUID victim) {
        return lastKiller.get(victim);
    }

    // ----- 소음 차단 포션 -----

    public ItemStack createSilencePotion(int amount) {
        ItemStack item = new ItemBuilder(matchOrDefault("blackmarket.silence-potion.material", Material.POTION))
                .name("§8소음 차단 포션")
                .lore(List.of(
                        "§7마시면 " + silenceDurationTicks() / 20 + "초 동안 블록을 부수거나",
                        "§7상자를 열 때 나는 소리/모습이",
                        "§7주변 다른 유저에게 들키지 않습니다."
                ))
                .amount(amount)
                .build();
        return tagBoolean(item, silencePotionKey, modelData("silence-potion"));
    }

    public boolean isSilencePotion(ItemStack item) {
        return hasFlag(item, silencePotionKey);
    }

    public int silenceDurationTicks() {
        return plugin.getConfig().getInt("blackmarket.silence-potion.duration-ticks", 600);
    }

    public void markSilenced(UUID uuid) {
        silenced.put(uuid, System.currentTimeMillis() + silenceDurationTicks() * 50L);
    }

    public boolean isSilenced(UUID uuid) {
        Long expiry = silenced.get(uuid);
        return expiry != null && expiry > System.currentTimeMillis();
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

    public NamespacedKey footprintTrackerKey() {
        return footprintTrackerKey;
    }
}
