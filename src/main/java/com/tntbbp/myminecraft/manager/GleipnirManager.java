package com.tntbbp.myminecraft.manager;

import com.tntbbp.myminecraft.MyMinecraftPlugin;
import com.tntbbp.myminecraft.util.ItemBuilder;
import com.tntbbp.myminecraft.util.OpImmunity;
import org.bukkit.BanList;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 신화 등급 구속 무기 '글레이프니르'. 공격력이 0인 대신 상대의 행동 자체를 묶는다.
 *
 * <ul>
 *   <li>[F] 봉인 — 전방 대상의 스킬과 특수능력을 일정 시간 사용할 수 없게 만든다.</li>
 *   <li>[Q] 속박 — 대상의 방어력에 비례해 길어지는 시간 동안 이동을 묶는다.</li>
 *   <li>[L] 절대봉인 — 아이템을 소모해 대상을 일정 시간 서버에서 차단한다.</li>
 * </ul>
 */
public class GleipnirManager {

    private final MyMinecraftPlugin plugin;
    private final NamespacedKey itemKey;
    private final NamespacedKey attackDamageKey;

    private final Map<UUID, Long> sealedUntilMillis = new HashMap<>();
    private final Map<UUID, Long> boundUntilMillis = new HashMap<>();
    private final Map<UUID, Long> sealCooldowns = new HashMap<>();
    private final Map<UUID, Long> bindCooldowns = new HashMap<>();

    public GleipnirManager(MyMinecraftPlugin plugin) {
        this.plugin = plugin;
        this.itemKey = new NamespacedKey(plugin, "gleipnir");
        this.attackDamageKey = new NamespacedKey(plugin, "gleipnir_attack_damage");
    }

    // ----- 아이템 -----

    public ItemStack createItem() {
        ItemStack item = new ItemBuilder(Material.LEAD)
                .name("§f§l글레이프니르 §7(Gleipnir)")
                .lore(List.of(
                        "§d§l신화 (Mythic) §8| §7구속 §8| §7공격력 §c0",
                        "§7이 사슬은 베지 않는다. 다만 묶을 뿐이다.",
                        "",
                        "§6[F] §f봉인 §7(재사용 " + sealCooldownSeconds() + "초)",
                        "§7 전방 대상의 스킬과 특수능력을 " + sealDurationSeconds() + "초간 봉인",
                        "",
                        "§6[Q] §f속박 §7(재사용 " + bindCooldownSeconds() + "초)",
                        "§7 대상의 이동을 묶는다. §f방어력이 높을수록 더 오래 묶인다",
                        "",
                        "§6[L] §4§l절대봉인 §7(아이템 소모)",
                        "§7 대상을 " + (absoluteBanSeconds() / 60) + "분간 서버에서 차단한다",
                        "§8 사용 시 글레이프니르가 사라진다",
                        "",
                        "§7\"신들이 늑대를 묶기 위해 만든, 끊어지지 않는 족쇄\""
                ))
                .glow()
                .build();

        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(itemKey, PersistentDataType.BYTE, (byte) 1);
        meta.setUnbreakable(true);
        meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE, ItemFlag.HIDE_ATTRIBUTES);

        // 공격력 0 — 맨손 기본 공격력(1.0)까지 상쇄한다.
        meta.addAttributeModifier(Attribute.GENERIC_ATTACK_DAMAGE, new AttributeModifier(
                attackDamageKey, -1.0, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.MAINHAND));

        int modelData = plugin.getConfig().getInt("gleipnir.model-data", 0);
        if (modelData != 0) {
            meta.setCustomModelData(modelData);
        }
        item.setItemMeta(meta);

        plugin.getGradeManager().applyGrade(item, GradeManager.Grade.MASTER);
        return item;
    }

    public boolean isGleipnir(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        Byte flag = item.getItemMeta().getPersistentDataContainer().get(itemKey, PersistentDataType.BYTE);
        return flag != null && flag == (byte) 1;
    }

    // ----- 상태 조회 -----

    /** 스킬/특수능력이 봉인된 상태인가. 각 무기 리스너가 발동 전에 확인한다. */
    public boolean isSealed(UUID uuid) {
        return isActive(sealedUntilMillis, uuid);
    }

    /** 이동이 묶인 상태인가. */
    public boolean isBound(UUID uuid) {
        return isActive(boundUntilMillis, uuid);
    }

    private boolean isActive(Map<UUID, Long> map, UUID uuid) {
        Long until = map.get(uuid);
        if (until == null) {
            return false;
        }
        if (until <= System.currentTimeMillis()) {
            map.remove(uuid);
            return false;
        }
        return true;
    }

    public long remainingSealSeconds(UUID uuid) {
        return remainingSeconds(sealedUntilMillis, uuid);
    }

    private long remainingSeconds(Map<UUID, Long> map, UUID uuid) {
        Long until = map.get(uuid);
        return until == null ? 0 : Math.max(0, (until - System.currentTimeMillis() + 999) / 1000);
    }

    // ----- [F] 봉인 -----

    public int sealCooldownSeconds() {
        return plugin.getConfig().getInt("gleipnir.seal.cooldown-seconds", 120);
    }

    public int sealDurationSeconds() {
        return plugin.getConfig().getInt("gleipnir.seal.duration-seconds", 20);
    }

    public long remainingSealCooldown(UUID uuid) {
        return remainingCooldown(sealCooldowns, uuid, sealCooldownSeconds());
    }

    /** @return 봉인에 성공한 대상. 대상이 없으면 null */
    public Player useSeal(Player caster) {
        sealCooldowns.put(caster.getUniqueId(), System.currentTimeMillis());

        Player target = findTarget(caster);
        if (target == null) {
            return null;
        }

        sealedUntilMillis.put(target.getUniqueId(),
                System.currentTimeMillis() + sealDurationSeconds() * 1000L);

        target.getWorld().playSound(target.getLocation(), Sound.BLOCK_CHAIN_PLACE, 1.6f, 0.6f);
        target.getWorld().spawnParticle(Particle.ENCHANT, target.getLocation().add(0, 1, 0),
                60, 0.6, 1.0, 0.6, 0.6);
        target.sendTitle("§f§l봉인", "§7스킬을 사용할 수 없습니다", 5, 40, 10);
        target.sendMessage("§f[글레이프니르] §7스킬과 특수능력이 " + sealDurationSeconds() + "초간 봉인되었습니다.");
        return target;
    }

    // ----- [Q] 속박 -----

    public int bindCooldownSeconds() {
        return plugin.getConfig().getInt("gleipnir.bind.cooldown-seconds", 180);
    }

    public long remainingBindCooldown(UUID uuid) {
        return remainingCooldown(bindCooldowns, uuid, bindCooldownSeconds());
    }

    /**
     * 대상의 방어력에 비례해 길어지는 시간 동안 이동을 묶는다.
     * 무겁게 무장할수록 사슬에 더 오래 잡힌다는 콘셉트다.
     *
     * @return 묶은 대상. 대상이 없으면 null
     */
    public Player useBind(Player caster) {
        bindCooldowns.put(caster.getUniqueId(), System.currentTimeMillis());

        Player target = findTarget(caster);
        if (target == null) {
            return null;
        }

        int seconds = bindSecondsFor(target);
        boundUntilMillis.put(target.getUniqueId(), System.currentTimeMillis() + seconds * 1000L);

        int ticks = seconds * 20;
        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, ticks, 255, false, false, true));
        target.setVelocity(new Vector(0, 0, 0));

        target.getWorld().playSound(target.getLocation(), Sound.BLOCK_CHAIN_BREAK, 1.8f, 0.5f);
        target.getWorld().spawnParticle(Particle.CRIT, target.getLocation().add(0, 1, 0),
                50, 0.5, 1.0, 0.5, 0.1);
        target.sendTitle("§f§l속박", "§7" + seconds + "초간 움직일 수 없습니다", 5, ticks, 10);
        target.sendMessage("§f[글레이프니르] §7사슬에 묶였습니다. §8(방어력이 높을수록 오래 묶입니다)");
        return target;
    }

    /** 방어력 수치에 비례한 속박 시간(초). */
    public int bindSecondsFor(Player target) {
        double base = plugin.getConfig().getDouble("gleipnir.bind.base-seconds", 3.0);
        double perArmor = plugin.getConfig().getDouble("gleipnir.bind.seconds-per-armor", 0.4);
        double max = plugin.getConfig().getDouble("gleipnir.bind.max-seconds", 15.0);

        AttributeInstance armorAttribute = target.getAttribute(Attribute.GENERIC_ARMOR);
        double armor = armorAttribute == null ? 0.0 : armorAttribute.getValue();

        return (int) Math.round(Math.min(max, base + armor * perArmor));
    }

    // ----- [L] 절대봉인 -----

    public boolean absoluteSealEnabled() {
        return plugin.getConfig().getBoolean("gleipnir.absolute-seal.enabled", true);
    }

    public int absoluteBanSeconds() {
        return plugin.getConfig().getInt("gleipnir.absolute-seal.ban-seconds", 600);
    }

    /**
     * 대상을 일정 시간 서버에서 차단한다. 되돌리기 어려운 효과이므로 OP는 면역이고,
     * 누가 누구에게 썼는지 콘솔과 전체 공지에 남긴다.
     *
     * @return 차단한 대상. 대상이 없거나 사용할 수 없으면 null
     */
    public Player useAbsoluteSeal(Player caster) {
        Player target = findTarget(caster);
        if (target == null) {
            return null;
        }
        if (OpImmunity.isImmune(target)) {
            caster.sendMessage("§c이 대상에게는 절대봉인이 통하지 않습니다.");
            return null;
        }

        int seconds = absoluteBanSeconds();
        Date expires = new Date(System.currentTimeMillis() + seconds * 1000L);
        String reason = "글레이프니르 - 절대봉인 (" + (seconds / 60) + "분)";

        Bukkit.getBanList(BanList.Type.NAME)
                .addBan(target.getName(), reason, expires, caster.getName());

        plugin.getLogger().warning("절대봉인: " + caster.getName() + " -> " + target.getName()
                + " (" + seconds + "초 차단)");
        Bukkit.broadcastMessage("§4§l[절대봉인] §f" + caster.getName() + "§7님이 §f" + target.getName()
                + "§7님을 " + (seconds / 60) + "분간 봉인했습니다.");

        target.getWorld().playSound(target.getLocation(), Sound.ENTITY_WITHER_SPAWN, 2.0f, 0.5f);
        target.kickPlayer("§4§l절대봉인\n\n§7" + (seconds / 60) + "분 후에 다시 접속할 수 있습니다.");
        return target;
    }

    // ----- 공용 -----

    /** 시전자가 바라보는 방향의 가장 가까운 플레이어를 찾는다. */
    private Player findTarget(Player caster) {
        double range = plugin.getConfig().getDouble("gleipnir.range", 12.0);
        double coneAngle = plugin.getConfig().getDouble("gleipnir.cone-angle-degrees", 40.0);

        Location eye = caster.getEyeLocation();
        Vector direction = eye.getDirection().normalize();
        double halfAngleCos = Math.cos(Math.toRadians(coneAngle / 2.0));

        Player best = null;
        double bestDistance = Double.MAX_VALUE;

        for (org.bukkit.entity.Entity entity : caster.getWorld().getNearbyEntities(eye, range, range, range)) {
            if (!(entity instanceof Player target) || target.equals(caster)) {
                continue;
            }
            Vector toTarget = target.getLocation().toVector().subtract(eye.toVector());
            double distance = toTarget.length();
            if (distance < 0.001 || distance > range) {
                continue;
            }
            if (direction.dot(toTarget.normalize()) < halfAngleCos) {
                continue;
            }
            if (distance < bestDistance) {
                bestDistance = distance;
                best = target;
            }
        }
        return best;
    }

    private long remainingCooldown(Map<UUID, Long> cooldowns, UUID uuid, int cooldownSeconds) {
        Long last = cooldowns.get(uuid);
        if (last == null) {
            return 0;
        }
        return Math.max(0, cooldownSeconds - (System.currentTimeMillis() - last) / 1000);
    }

    /** 사슬 연출 — 시전자와 대상을 잇는 파티클. */
    public void drawChain(Player caster, LivingEntity target) {
        Vector from = caster.getEyeLocation().toVector();
        Vector to = target.getLocation().toVector().add(new Vector(0, target.getHeight() / 2.0, 0));
        Vector delta = to.clone().subtract(from);

        int steps = Math.max(1, (int) (delta.length() * 3));
        Vector step = delta.multiply(1.0 / steps);
        Vector point = from.clone();

        for (int i = 0; i <= steps; i++) {
            caster.getWorld().spawnParticle(Particle.END_ROD, point.getX(), point.getY(), point.getZ(),
                    1, 0.02, 0.02, 0.02, 0.0);
            point.add(step);
        }
    }
}
