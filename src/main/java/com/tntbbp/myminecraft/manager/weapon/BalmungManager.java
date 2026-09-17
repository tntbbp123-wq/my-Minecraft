package com.tntbbp.myminecraft.manager.weapon;

import com.tntbbp.myminecraft.MyMinecraftPlugin;
import com.tntbbp.myminecraft.manager.combat.CurseManager;
import com.tntbbp.myminecraft.manager.item.GradeManager;
import com.tntbbp.myminecraft.util.ItemBuilder;
import com.tntbbp.myminecraft.util.SkillTargets;
import com.tntbbp.myminecraft.util.WeaponAttributes;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 신화 등급 무기 '발뭉(Balmung)'.
 *
 * <ul>
 *   <li>패시브 <b>용살의 긍지</b> — 상대 방어력이 높을수록 방어관통이 올라가고 갑주 내구도를 깎는다</li>
 *   <li>[F] <b>그람의 참격</b> — 전방 직선으로 검기를 날려 '파프니르의 저주'(둔화+치유 차단)를 건다</li>
 *   <li>[웅크리기+우클릭] <b>발뭉의 종말</b> — 넓은 부채꼴 검기 폭발, 고정 피해 + 제압</li>
 * </ul>
 */
public class BalmungManager {

    private final MyMinecraftPlugin plugin;
    private final NamespacedKey itemKey;
    private final Map<UUID, Long> gramCooldowns = new HashMap<>();
    private final Map<UUID, Long> doomCooldowns = new HashMap<>();

    public BalmungManager(MyMinecraftPlugin plugin) {
        this.plugin = plugin;
        this.itemKey = new NamespacedKey(plugin, "balmung");
    }

    public ItemStack createItem() {
        double attackDamage = attackDamage();

        ItemStack item = new ItemBuilder(Material.NETHERITE_SWORD)
                .name("§f§l발뭉 §7(Balmung)")
                .lore(List.of(
                        "§d§l신화 (Mythic) §8| §7무기 §8| §7공격력 §c" + formatNumber(attackDamage)
                                + " §8| §7공격속도 §e1.6",
                        "",
                        "§c[패시브] §f용살의 긍지 §7(Dragon Slayer's Pride)",
                        "§7 방어 관통 §f" + formatNumber(basePenetrationPercent()) + "% §7+ 상대 방어력 1당 §f"
                                + formatNumber(penetrationPerArmorPoint()) + "% §7(최대 "
                                + formatNumber(maxPenetrationPercent()) + "%)",
                        "§7 때릴 때마다 상대 갑주 내구도를 §f" + armorDurabilityDamage() + " §7씩 추가로 깎음",
                        "",
                        "§6[F] §f그람의 참격 §7(Gram Slash · 재사용 " + gramCooldownSeconds() + "초)",
                        "§7 전방 §f" + formatNumber(gramRange()) + "블록 §7직선을 관통하는 검기, §c"
                                + formatNumber(gramDamage()) + " §7피해",
                        "§7 적중 시 §5파프니르의 저주§7: " + gramCurseDurationSeconds() + "초간 이동속도 급감 + 치유 차단",
                        "",
                        "§6[웅크리기+우클릭] §f발뭉의 종말 §7(Balmung's Doom · 재사용 "
                                + doomCooldownSeconds() + "초)",
                        "§7 전방 넓은 부채꼴에 검기 폭발, §c" + formatNumber(doomDamage()) + " §7피해",
                        "§7 시전 중 모든 상태 이상 면역",
                        "§7 추가로 방어무시 §c" + formatNumber(doomFixedDamage()) + " §7피해 + §f제압 "
                                + doomStunSeconds() + "초",
                        "",
                        "§7\"파프니르를 두 동강 낸 영웅의 마검\""
                ))
                .glow()
                .build();

        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(itemKey, PersistentDataType.BYTE, (byte) 1);
        meta.setUnbreakable(true);
        meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE, ItemFlag.HIDE_ATTRIBUTES);

        WeaponAttributes.applyBase(meta, Material.NETHERITE_SWORD, attackDamage,
                new NamespacedKey(plugin, "balmung_attack_damage"));

        int modelData = plugin.getConfig().getInt("balmung.model-data", 0);
        if (modelData != 0) {
            meta.setCustomModelData(modelData);
        }
        item.setItemMeta(meta);

        plugin.getGradeManager().applyGrade(item, GradeManager.Grade.MASTER);
        return item;
    }

    public boolean isBalmung(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        Byte flag = item.getItemMeta().getPersistentDataContainer().get(itemKey, PersistentDataType.BYTE);
        return flag != null && flag == (byte) 1;
    }

    // ----- 설정 -----

    public double attackDamage() {
        return plugin.getConfig().getDouble("balmung.attack-damage", 14.0);
    }

    public double basePenetrationPercent() {
        return plugin.getConfig().getDouble("balmung.pride.base-penetration-percent", 10.0);
    }

    public double penetrationPerArmorPoint() {
        return plugin.getConfig().getDouble("balmung.pride.penetration-per-armor-point", 3.0);
    }

    public double maxPenetrationPercent() {
        return plugin.getConfig().getDouble("balmung.pride.max-penetration-percent", 70.0);
    }

    public int armorDurabilityDamage() {
        return plugin.getConfig().getInt("balmung.pride.armor-durability-damage", 3);
    }

    public int gramCooldownSeconds() {
        return plugin.getConfig().getInt("balmung.gram.cooldown-seconds", 20);
    }

    public double gramRange() {
        return plugin.getConfig().getDouble("balmung.gram.range", 14.0);
    }

    public double gramDamage() {
        return plugin.getConfig().getDouble("balmung.gram.damage", 15.0);
    }

    public int gramCurseDurationSeconds() {
        return plugin.getConfig().getInt("balmung.gram.curse-duration-seconds", 6);
    }

    public int doomCooldownSeconds() {
        return plugin.getConfig().getInt("balmung.doom.cooldown-seconds", 100);
    }

    public double doomDamage() {
        return plugin.getConfig().getDouble("balmung.doom.damage", 12.0);
    }

    public double doomFixedDamage() {
        return plugin.getConfig().getDouble("balmung.doom.fixed-damage", 10.0);
    }

    public int doomStunSeconds() {
        return plugin.getConfig().getInt("balmung.doom.stun-seconds", 2);
    }

    // ----- 패시브: 용살의 긍지 -----

    /** 상대의 방어력 수치에 비례해 올라가는 방어 관통(%). */
    public double penetrationPercentAgainst(LivingEntity target) {
        AttributeInstance armor = target.getAttribute(Attribute.GENERIC_ARMOR);
        double armorPoints = armor == null ? 0.0 : armor.getValue();
        double penetration = basePenetrationPercent() + armorPoints * penetrationPerArmorPoint();
        return Math.min(penetration, maxPenetrationPercent());
    }

    /** 맞은 쪽이 입고 있는 갑주의 내구도를 추가로 깎는다. 부서질 만큼 깎이면 실제로 부순다. */
    public void wearDownArmor(LivingEntity target) {
        int durabilityDamage = armorDurabilityDamage();
        if (durabilityDamage <= 0 || target.getEquipment() == null) {
            return;
        }
        ItemStack[] armorContents = target.getEquipment().getArmorContents();
        boolean changed = false;
        for (int slot = 0; slot < armorContents.length; slot++) {
            ItemStack piece = armorContents[slot];
            if (piece == null || piece.getType().getMaxDurability() <= 0 || !piece.hasItemMeta()) {
                continue;
            }
            if (!(piece.getItemMeta() instanceof Damageable damageable) || damageable.isUnbreakable()) {
                continue;
            }
            int newDamage = damageable.getDamage() + durabilityDamage;
            if (newDamage >= piece.getType().getMaxDurability()) {
                armorContents[slot] = null;
                target.getWorld().playSound(target.getLocation(), Sound.ENTITY_ITEM_BREAK, 1.0f, 1.0f);
            } else {
                damageable.setDamage(newDamage);
                piece.setItemMeta(damageable);
                armorContents[slot] = piece;
            }
            changed = true;
        }
        if (changed) {
            target.getEquipment().setArmorContents(armorContents);
        }
    }

    // ----- [F] 그람의 참격 -----

    public long remainingGramCooldown(UUID uuid) {
        return remainingCooldown(gramCooldowns, uuid, gramCooldownSeconds());
    }

    public boolean useGramSlash(Player caster) {
        if (remainingGramCooldown(caster.getUniqueId()) > 0) {
            return false;
        }
        gramCooldowns.put(caster.getUniqueId(), System.currentTimeMillis());

        double range = gramRange();
        double halfWidth = plugin.getConfig().getDouble("balmung.gram.half-width", 1.2);
        int slowAmplifier = plugin.getConfig().getInt("balmung.gram.curse-slow-amplifier", 2);

        World world = caster.getWorld();
        Location eye = caster.getEyeLocation();
        Vector direction = eye.getDirection().normalize();

        world.playSound(eye, Sound.ITEM_TRIDENT_THROW, 1.6f, 0.6f);
        world.playSound(eye, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.6f, 0.5f);
        spawnBeam(world, eye, direction, range);

        CurseManager curseManager = plugin.getCurseManager();
        for (LivingEntity target : SkillTargets.inLine(world, eye, direction, range, halfWidth, caster)) {
            target.damage(gramDamage(), caster);
            curseManager.applyCurse(target, "파프니르의 저주", 0.0, 100.0,
                    slowAmplifier, gramCurseDurationSeconds());
        }
        return true;
    }

    // ----- [웅크리기+우클릭] 발뭉의 종말 -----

    public long remainingDoomCooldown(UUID uuid) {
        return remainingCooldown(doomCooldowns, uuid, doomCooldownSeconds());
    }

    public boolean useDoom(Player caster) {
        if (remainingDoomCooldown(caster.getUniqueId()) > 0) {
            return false;
        }
        doomCooldowns.put(caster.getUniqueId(), System.currentTimeMillis());

        double range = plugin.getConfig().getDouble("balmung.doom.range", 9.0);
        double coneAngle = plugin.getConfig().getDouble("balmung.doom.cone-angle-degrees", 140.0);
        int castTicks = plugin.getConfig().getInt("balmung.doom.cast-ticks", 20);

        CurseManager curseManager = plugin.getCurseManager();
        curseManager.grantImmunity(caster, castTicks + 20);

        World world = caster.getWorld();
        world.playSound(caster.getEyeLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1.4f, 0.7f);

        // 기운을 모으는 연출을 보여준 뒤 터뜨린다. 시전 중에는 상태 이상 면역이다.
        new BukkitRunnable() {
            int elapsed = 0;

            @Override
            public void run() {
                if (!caster.isOnline()) {
                    cancel();
                    return;
                }
                if (elapsed < castTicks) {
                    world.spawnParticle(Particle.DUST, caster.getEyeLocation(), 12, 0.6, 0.6, 0.6, 0,
                            new Particle.DustOptions(Color.fromRGB(230, 230, 255), 1.4f));
                    elapsed += 4;
                    return;
                }
                cancel();
                detonate(caster, range, coneAngle);
            }
        }.runTaskTimer(plugin, 0L, 4L);

        return true;
    }

    private void detonate(Player caster, double range, double coneAngle) {
        World world = caster.getWorld();
        Location eye = caster.getEyeLocation();
        Vector direction = eye.getDirection().normalize();

        world.playSound(eye, Sound.ENTITY_GENERIC_EXPLODE, 1.8f, 0.6f);
        world.spawnParticle(Particle.EXPLOSION_EMITTER, eye.clone().add(direction.clone().multiply(3)), 2);
        spawnFan(world, eye, direction, range, coneAngle);

        CurseManager curseManager = plugin.getCurseManager();
        for (LivingEntity target : SkillTargets.inCone(world, eye, direction, range, coneAngle, caster)) {
            target.damage(doomDamage(), caster);
            curseManager.dealTrueDamage(target, caster, doomFixedDamage());
            curseManager.applyStun(target, doomStunSeconds());
        }
    }

    // ----- 내부 -----

    private long remainingCooldown(Map<UUID, Long> cooldowns, UUID uuid, int cooldownSeconds) {
        Long last = cooldowns.get(uuid);
        if (last == null) {
            return 0;
        }
        return Math.max(0, cooldownSeconds - (System.currentTimeMillis() - last) / 1000);
    }

    private void spawnBeam(World world, Location eye, Vector direction, double range) {
        Vector unit = direction.clone().normalize();
        for (double distance = 0.5; distance <= range; distance += 0.4) {
            Location point = eye.clone().add(unit.clone().multiply(distance));
            world.spawnParticle(Particle.SWEEP_ATTACK, point, 1, 0.1, 0.1, 0.1, 0);
            world.spawnParticle(Particle.DUST, point, 3, 0.15, 0.15, 0.15, 0,
                    new Particle.DustOptions(Color.fromRGB(220, 220, 255), 1.3f));
        }
    }

    private void spawnFan(World world, Location eye, Vector direction, double range, double coneAngleDegrees) {
        Vector forward = direction.clone().setY(0);
        if (forward.lengthSquared() < 1.0E-4) {
            return;
        }
        forward.normalize();
        Vector right = new Vector(-forward.getZ(), 0, forward.getX());
        double half = coneAngleDegrees / 2.0;
        for (double angle = -half; angle <= half; angle += 6) {
            double radians = Math.toRadians(angle);
            for (double distance = 1.0; distance <= range; distance += 1.0) {
                Vector offset = forward.clone().multiply(Math.cos(radians) * distance)
                        .add(right.clone().multiply(Math.sin(radians) * distance));
                world.spawnParticle(Particle.SWEEP_ATTACK, eye.clone().add(offset), 1, 0, 0, 0, 0);
            }
        }
    }

    private String formatNumber(double value) {
        return value == Math.floor(value) ? String.valueOf((long) value) : String.valueOf(value);
    }
}
