package com.tntbbp.myminecraft.manager.weapon;

import com.tntbbp.myminecraft.MyMinecraftPlugin;
import com.tntbbp.myminecraft.manager.item.GradeManager;
import com.tntbbp.myminecraft.util.ItemBuilder;
import com.tntbbp.myminecraft.util.OpImmunity;
import com.tntbbp.myminecraft.util.SkillTargets;
import com.tntbbp.myminecraft.util.WeaponAttributes;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 전설 등급 세이버 '볼트 세이버'. 빠르게 휘두르며 뇌전을 쌓아 터뜨리는 기병용 검.
 *
 * <ul>
 *   <li>패시브 <b>일렉트릭 필드</b> — 움직인 거리가 쌓일수록 전류가 모이고,
 *       모인 상태에서 평타를 넣으면 추가 번개 피해가 들어간다</li>
 *   <li>[F] <b>소닉 랜스</b> — 전방으로 돌진하며 경로의 적을 베고 짧게 기절시킨다</li>
 *   <li>[웅크리기+F] <b>라이트닝 오라</b> — 검에 번개를 둘러 피해가 오른다.
 *       두른 상태에서 다시 누르면 전기를 방출해 주변에 번개를 연달아 내리친다</li>
 * </ul>
 */
public class VoltSaberManager {

    private static final int TICK_INTERVAL = 10;

    private final MyMinecraftPlugin plugin;
    private final NamespacedKey itemKey;

    /** 이동으로 모인 전하. 설정한 양을 채우면 다음 평타에 번개가 실린다. */
    private final Map<UUID, Double> charge = new ConcurrentHashMap<>();
    private final Map<UUID, Location> lastLocations = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lanceCooldowns = new ConcurrentHashMap<>();
    private final Map<UUID, Long> auraCooldowns = new ConcurrentHashMap<>();
    /** 라이트닝 오라가 끝나는 시각. */
    private final Map<UUID, Long> auraUntil = new ConcurrentHashMap<>();

    private BukkitTask tickTask;

    public VoltSaberManager(MyMinecraftPlugin plugin) {
        this.plugin = plugin;
        this.itemKey = new NamespacedKey(plugin, "volt_saber");
    }

    public void start() {
        tickTask = plugin.getServer().getScheduler().runTaskTimer(
                plugin, this::tick, TICK_INTERVAL, TICK_INTERVAL);
    }

    public void stop() {
        if (tickTask != null) {
            tickTask.cancel();
        }
        charge.clear();
        lastLocations.clear();
        auraUntil.clear();
    }

    public ItemStack createItem() {
        ItemStack item = new ItemBuilder(Material.NETHERITE_SWORD)
                .name("§b§l볼트 세이버 §7(Volt Saber)")
                .lore(List.of(
                        "§c§l전설 (Legendary) §8| §7세이버 §8| §7공격력 §c" + num(attackDamage())
                                + " §8| §7공격속도 §e" + num(attackSpeed()),
                        "§7치명타 §c" + num(critDamage()) + " §8| §7날이 푸른 전자기파로 번쩍인다",
                        "",
                        "§b[패시브] §f일렉트릭 필드",
                        "§7 §f" + num(chargePerHit()) + "블록 §7움직일 때마다 전하가 차고,",
                        "§7 가득 찬 상태로 평타를 넣으면 §b+" + num(boltDamage()) + " §7번개 피해",
                        "",
                        "§6[F] §f소닉 랜스 §7(재사용 " + lanceCooldownSeconds() + "초)",
                        "§7 전방 §f" + num(lanceDistance()) + "블록 §7돌진하며 경로의 적에게",
                        "§7 §c" + num(lanceDamage()) + " §7뇌전 베기 + §f" + lanceStunSeconds() + "초 §7기절",
                        "",
                        "§6[웅크리기+F] §f라이트닝 오라 §7(재사용 " + auraCooldownSeconds() + "초)",
                        "§7 " + auraSeconds() + "초간 검에 번개를 둘러 피해 §f+"
                                + num(auraDamagePercent()) + "%",
                        "§8 └ §7두른 상태에서 §f다시 웅크리기+F§7: 전기를 방출해",
                        "§7   주변에 번개 §f" + dischargeStrikes() + "번 §7(시전자는 맞지 않는다)",
                        "",
                        "§7\"번개는 두 번 치지 않는다 — 서른 번 친다\""
                ))
                .glow()
                .build();

        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(itemKey, PersistentDataType.BYTE, (byte) 1);
        meta.setUnbreakable(true);
        meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE, ItemFlag.HIDE_ATTRIBUTES);
        WeaponAttributes.applyBase(meta, Material.NETHERITE_SWORD, attackDamage(), attackSpeed(),
                new NamespacedKey(plugin, "volt_saber_attack_damage"));
        int modelData = plugin.getConfig().getInt("volt-saber.model-data", 0);
        if (modelData != 0) {
            meta.setCustomModelData(modelData);
        }
        item.setItemMeta(meta);

        plugin.getGradeManager().applyGrade(item, GradeManager.Grade.LEGEND);
        return item;
    }

    public boolean isVoltSaber(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        Byte flag = item.getItemMeta().getPersistentDataContainer().get(itemKey, PersistentDataType.BYTE);
        return flag != null && flag == (byte) 1;
    }

    // ----- 설정 -----

    public double attackDamage() {
        return plugin.getConfig().getDouble("volt-saber.attack-damage", 16.5);
    }

    public double attackSpeed() {
        return plugin.getConfig().getDouble("volt-saber.attack-speed", 1.6);
    }

    public double critDamage() {
        return plugin.getConfig().getDouble("volt-saber.crit-damage", 24.0);
    }

    /** 전하가 가득 차는 데 필요한 이동 거리(블록). */
    public double chargePerHit() {
        return plugin.getConfig().getDouble("volt-saber.field.charge-distance", 12.0);
    }

    public double boltDamage() {
        return plugin.getConfig().getDouble("volt-saber.field.bolt-damage", 2.0);
    }

    public int lanceCooldownSeconds() {
        return plugin.getConfig().getInt("volt-saber.lance.cooldown-seconds", 12);
    }

    public double lanceDistance() {
        return plugin.getConfig().getDouble("volt-saber.lance.distance", 8.0);
    }

    /** 돌진 경로의 판정 폭(블록). */
    public double lanceWidth() {
        return plugin.getConfig().getDouble("volt-saber.lance.width", 2.0);
    }

    public double lanceDamage() {
        return plugin.getConfig().getDouble("volt-saber.lance.damage", 14.0);
    }

    public double lanceStunSeconds() {
        return plugin.getConfig().getDouble("volt-saber.lance.stun-seconds", 0.5);
    }

    public int auraCooldownSeconds() {
        return plugin.getConfig().getInt("volt-saber.aura.cooldown-seconds", 30);
    }

    public int auraSeconds() {
        return plugin.getConfig().getInt("volt-saber.aura.duration-seconds", 10);
    }

    public double auraDamagePercent() {
        return plugin.getConfig().getDouble("volt-saber.aura.damage-percent", 40.0);
    }

    public int dischargeStrikes() {
        return plugin.getConfig().getInt("volt-saber.aura.discharge-strikes", 30);
    }

    public double dischargeRadius() {
        return plugin.getConfig().getDouble("volt-saber.aura.discharge-radius", 7.0);
    }

    public double dischargeDamage() {
        return plugin.getConfig().getDouble("volt-saber.aura.discharge-damage-per-strike", 3.0);
    }

    // ----- 패시브: 일렉트릭 필드 -----

    public boolean isCharged(UUID uuid) {
        return charge.getOrDefault(uuid, 0.0) >= chargePerHit();
    }

    /** 쌓인 전하를 써서 번개 피해를 얹는다. 전하가 모자라면 아무 일도 없다. */
    public boolean consumeCharge(Player attacker, LivingEntity target) {
        if (!isCharged(attacker.getUniqueId())) {
            return false;
        }
        charge.put(attacker.getUniqueId(), 0.0);
        target.getWorld().playSound(target.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 0.8f, 1.6f);
        target.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, target.getLocation().add(0, 1, 0),
                20, 0.4, 0.6, 0.4, 0.1);
        plugin.getCurseManager().dealTrueDamage(target, attacker, boltDamage());
        return true;
    }

    // ----- [F] 소닉 랜스 -----

    public long remainingLanceCooldown(UUID uuid) {
        return remaining(lanceCooldowns, uuid, lanceCooldownSeconds());
    }

    public boolean useSonicLance(Player caster) {
        if (remainingLanceCooldown(caster.getUniqueId()) > 0) {
            return false;
        }
        lanceCooldowns.put(caster.getUniqueId(), System.currentTimeMillis());

        World world = caster.getWorld();
        Location eye = caster.getEyeLocation();
        Vector direction = eye.getDirection().normalize();

        caster.setVelocity(direction.clone().multiply(lanceDistance() / 6.0).setY(0.2));
        world.playSound(eye, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.2f, 1.8f);
        spawnTrail(world, eye, direction, lanceDistance());

        int stunTicks = (int) Math.round(lanceStunSeconds() * 20);
        for (LivingEntity target : SkillTargets.inLine(world, eye, direction, lanceDistance(), lanceWidth(), caster)) {
            target.damage(lanceDamage(), caster);
            if (!OpImmunity.isImmune(target) && stunTicks > 0) {
                // 아주 짧은 기절이라 제압 상태를 쓰기보다 그 자리에서 묶는 편이 자연스럽다.
                plugin.getCurseManager().applyStun(target, Math.max(1, stunTicks / 20));
            }
        }
        return true;
    }

    // ----- [웅크리기+F] 라이트닝 오라 -----

    public long remainingAuraCooldown(UUID uuid) {
        return remaining(auraCooldowns, uuid, auraCooldownSeconds());
    }

    public boolean isAuraActive(UUID uuid) {
        Long until = auraUntil.get(uuid);
        return until != null && until > System.currentTimeMillis();
    }

    /** 오라가 걸려 있는 동안 평타에 곱해지는 배율. */
    public double auraMultiplier(UUID uuid) {
        return isAuraActive(uuid) ? 1.0 + auraDamagePercent() / 100.0 : 1.0;
    }

    /** 오라를 두른다. 이미 둘렀으면 방출로 넘어간다. */
    public boolean startAura(Player caster) {
        if (remainingAuraCooldown(caster.getUniqueId()) > 0) {
            return false;
        }
        auraCooldowns.put(caster.getUniqueId(), System.currentTimeMillis());
        auraUntil.put(caster.getUniqueId(), System.currentTimeMillis() + auraSeconds() * 1000L);

        World world = caster.getWorld();
        world.playSound(caster.getLocation(), Sound.ITEM_TRIDENT_THUNDER, 1.4f, 1.2f);
        caster.sendMessage("§b라이트닝 오라! §7— " + auraSeconds() + "초간 피해 +"
                + num(auraDamagePercent()) + "%. 다시 §f웅크리기+F§7로 방출합니다.");
        return true;
    }

    /** 두른 전기를 한 번에 쏟아낸다. 시전자는 맞지 않는다. */
    public void discharge(Player caster) {
        auraUntil.remove(caster.getUniqueId());
        World world = caster.getWorld();
        Location center = caster.getLocation();
        int strikes = dischargeStrikes();
        double radius = dischargeRadius();

        world.playSound(center, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 2.0f, 0.8f);
        caster.sendMessage("§b전기 방출! §7— 번개 " + strikes + "번이 주변을 휘몰아칩니다.");

        new BukkitRunnable() {
            int done = 0;

            @Override
            public void run() {
                if (done >= strikes || !caster.isOnline()) {
                    cancel();
                    return;
                }
                done++;
                double angle = Math.toRadians(done * 360.0 / Math.max(1, strikes) * 3);
                double r = radius * (0.35 + Math.random() * 0.65);
                Location spot = center.clone().add(Math.cos(angle) * r, 0, Math.sin(angle) * r);

                // 실제 번개 엔티티는 시전자도 태우므로, 소리와 입자만 내고 판정은 직접 준다.
                world.strikeLightningEffect(spot);
                for (LivingEntity target : SkillTargets.inCone(world, spot, spot.getDirection(),
                        2.5, 360.0, caster)) {
                    target.damage(dischargeDamage(), caster);
                }
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }

    // ----- 주기 처리 -----

    private void tick() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            UUID uuid = player.getUniqueId();
            if (!isVoltSaber(player.getInventory().getItemInMainHand())) {
                lastLocations.remove(uuid);
                continue;
            }

            Location last = lastLocations.put(uuid, player.getLocation().clone());
            if (last != null && last.getWorld().equals(player.getWorld())) {
                double moved = last.distance(player.getLocation());
                if (moved > 0.05) {
                    charge.merge(uuid, moved, (a, b) -> Math.min(chargePerHit(), a + b));
                }
            }

            if (isCharged(uuid)) {
                player.getWorld().spawnParticle(Particle.ELECTRIC_SPARK,
                        player.getLocation().add(0, 1.0, 0), 4, 0.3, 0.4, 0.3, 0.02);
            }
            if (isAuraActive(uuid)) {
                player.getWorld().spawnParticle(Particle.DUST, player.getLocation().add(0, 1.2, 0),
                        8, 0.4, 0.5, 0.4, 0,
                        new Particle.DustOptions(Color.fromRGB(120, 200, 255), 1.2f));
            }
            player.sendActionBar(LegacyComponentSerializer.legacySection().deserialize(
                    "§b전하 §f" + (int) Math.floor(charge.getOrDefault(uuid, 0.0)) + "§8/§7"
                            + (int) chargePerHit() + (isAuraActive(uuid) ? " §8| §b라이트닝 오라" : "")));
        }
    }

    private long remaining(Map<UUID, Long> cooldowns, UUID uuid, int seconds) {
        Long last = cooldowns.get(uuid);
        if (last == null) {
            return 0;
        }
        return Math.max(0, seconds - (System.currentTimeMillis() - last) / 1000);
    }

    private void spawnTrail(World world, Location eye, Vector direction, double distance) {
        for (double step = 0.5; step <= distance; step += 0.4) {
            Location point = eye.clone().add(direction.clone().multiply(step));
            world.spawnParticle(Particle.ELECTRIC_SPARK, point, 4, 0.15, 0.15, 0.15, 0.02);
            world.spawnParticle(Particle.DUST, point, 2, 0.1, 0.1, 0.1, 0,
                    new Particle.DustOptions(Color.fromRGB(150, 220, 255), 1.3f));
        }
    }

    private String num(double value) {
        return value == Math.floor(value) ? String.valueOf((long) value) : String.valueOf(value);
    }
}
