package com.tntbbp.myminecraft.manager.weapon;

import com.tntbbp.myminecraft.MyMinecraftPlugin;
import com.tntbbp.myminecraft.manager.combat.CurseManager;
import com.tntbbp.myminecraft.manager.combat.InfernalBurnManager;
import com.tntbbp.myminecraft.manager.item.GradeManager;
import com.tntbbp.myminecraft.util.ItemBuilder;
import com.tntbbp.myminecraft.util.SkillTargets;
import com.tntbbp.myminecraft.util.WeaponAttributes;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 신화 등급 무기 '레바테인(Lævateinn)'.
 *
 * <ul>
 *   <li>패시브 <b>붉은 나뭇가지의 지옥화</b> — 때릴 때마다 '업화'가 중첩되어 초당 피해가 커진다</li>
 *   <li>[F] <b>수르트의 불길</b> — 전방 화염 폭풍, 화상 + 치유 차단</li>
 *   <li>[웅크리기+우클릭] <b>라그나로크</b> — 시전 중 상태이상 면역, 물로도 꺼지지 않는 화염 저주</li>
 * </ul>
 */
public class LaevateinnManager {

    private record BlockSnapshot(Location location, BlockData data) {
    }

    private final MyMinecraftPlugin plugin;
    private final NamespacedKey itemKey;
    private final Map<UUID, Long> abilityCooldowns = new HashMap<>();
    private final Map<UUID, Long> ragnarokCooldowns = new HashMap<>();

    public LaevateinnManager(MyMinecraftPlugin plugin) {
        this.plugin = plugin;
        this.itemKey = new NamespacedKey(plugin, "laevateinn");
    }

    public ItemStack createItem() {
        double attackDamage = plugin.getConfig().getDouble("laevateinn.attack-damage", 12.0);

        ItemStack item = new ItemBuilder(Material.NETHERITE_SWORD)
                .name("§5§l레바테인 §7(Lævateinn)")
                .lore(List.of(
                        "§d§l신화 (Mythic) §8| §7무기 §8| §7공격력 §c" + formatNumber(attackDamage)
                                + " §8| §7공격속도 §e1.6",
                        "",
                        "§c[패시브] §f붉은 나뭇가지의 지옥화 §7(Laev's Scourge)",
                        "§7 때릴 때마다 §6업화§7가 최대 §f" + karmaMaxStacks() + "중첩§7까지 쌓여",
                        "§7 중첩당 초당 §c" + formatNumber(karmaDamagePerSecondPerStack()) + " §7방어무시 피해",
                        "",
                        "§6[F] §f수르트의 불길 §7(Surtr's Flame · 재사용 " + abilityCooldownSeconds() + "초)",
                        "§7 전방에 화염 폭풍을 뿜어 광역 피해+띄우기+화상, 땅을 잠시 마그마로 바꿈",
                        "§7 적중 시 " + flameHealBlockSeconds() + "초간 §f치유·회복 효과 차단§7(중화)",
                        "",
                        "§6[웅크리기+우클릭] §f라그나로크 §7(Ragnarok · 재사용 "
                                + ragnarokCooldownSeconds() + "초)",
                        "§7 전방을 초토화하고 시전 중 모든 상태 이상 면역",
                        "§7 §c영원불멸의 화염§7: " + ragnarokBurnSeconds() + "초간 초당 §c"
                                + formatNumber(ragnarokBurnDamagePerSecond()) + " §7방어무시 피해",
                        "§7 (물로도 꺼지지 않는다)",
                        "",
                        "§7\"로키가 담금질하고 수르트가 종말에 휘두를 태고의 마검\""
                ))
                .glow()
                .build();

        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(itemKey, PersistentDataType.BYTE, (byte) 1);
        meta.setUnbreakable(true);
        meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE, ItemFlag.HIDE_ATTRIBUTES);
        meta.addEnchant(Enchantment.FIRE_ASPECT, 2, true);

        WeaponAttributes.applyBase(meta, Material.NETHERITE_SWORD, attackDamage,
                new NamespacedKey(plugin, "laevateinn_attack_damage"));

        int modelData = plugin.getConfig().getInt("laevateinn.model-data", 0);
        if (modelData != 0) {
            meta.setCustomModelData(modelData);
        }
        item.setItemMeta(meta);

        // 레바테인은 신화 등급 무기이므로 처음부터 등급은 신화로 표시되지만, 강화 한계치를
        // 30강까지 풀려면 다른 무기와 마찬가지로 초월의 제단을 거쳐야 한다 (자동 초월 없음).
        plugin.getGradeManager().applyGrade(item, GradeManager.Grade.MASTER);
        return item;
    }

    private String formatNumber(double value) {
        return value == Math.floor(value) ? String.valueOf((long) value) : String.valueOf(value);
    }

    public boolean isLaevateinn(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        Byte flag = item.getItemMeta().getPersistentDataContainer().get(itemKey, PersistentDataType.BYTE);
        return flag != null && flag == (byte) 1;
    }

    // ----- 패시브: 업화 (Karma) -----

    public int karmaDurationSeconds() {
        return plugin.getConfig().getInt("laevateinn.karma.duration-seconds", 6);
    }

    public double karmaDamagePerSecondPerStack() {
        return plugin.getConfig().getDouble("laevateinn.karma.damage-per-second-per-stack", 1.5);
    }

    public int karmaMaxStacks() {
        return plugin.getConfig().getInt("laevateinn.karma.max-stacks", 5);
    }

    // ----- [F] 수르트의 불길 -----

    public int flameHealBlockSeconds() {
        return plugin.getConfig().getInt("laevateinn.ability.heal-block-seconds", 5);
    }

    public int abilityCooldownSeconds() {
        return plugin.getConfig().getInt("laevateinn.ability.cooldown-seconds", 120);
    }

    public long remainingCooldownSeconds(UUID uuid) {
        Long last = abilityCooldowns.get(uuid);
        if (last == null) {
            return 0;
        }
        long remaining = abilityCooldownSeconds() - (System.currentTimeMillis() - last) / 1000;
        return Math.max(0, remaining);
    }

    public boolean useSurtrFlame(Player caster) {
        if (remainingCooldownSeconds(caster.getUniqueId()) > 0) {
            return false;
        }
        abilityCooldowns.put(caster.getUniqueId(), System.currentTimeMillis());

        double range = plugin.getConfig().getDouble("laevateinn.ability.range", 8.0);
        double coneAngle = plugin.getConfig().getDouble("laevateinn.ability.cone-angle-degrees", 80.0);
        double damage = plugin.getConfig().getDouble("laevateinn.ability.damage", 20.0);
        double launchPower = plugin.getConfig().getDouble("laevateinn.ability.launch-power", 1.0);
        int burnDuration = plugin.getConfig().getInt("laevateinn.ability.burn-duration-seconds", 10);
        double burnDps = plugin.getConfig().getDouble("laevateinn.ability.burn-damage-per-second", 4.0);
        int magmaDuration = plugin.getConfig().getInt("laevateinn.ability.magma-duration-seconds", 8);

        World world = caster.getWorld();
        Location eye = caster.getEyeLocation();
        Vector direction = eye.getDirection().normalize();
        double halfAngleCos = Math.cos(Math.toRadians(coneAngle / 2.0));

        world.playSound(eye, Sound.ENTITY_BLAZE_SHOOT, 2.0f, 0.6f);
        world.playSound(eye, Sound.ENTITY_GENERIC_EXPLODE, 1.2f, 1.4f);

        InfernalBurnManager burnManager = plugin.getInfernalBurnManager();
        for (Entity entity : world.getNearbyEntities(eye, range, range, range)) {
            if (!(entity instanceof LivingEntity target) || entity.equals(caster)) {
                continue;
            }
            Vector toTarget = target.getLocation().toVector().subtract(eye.toVector());
            double distance = toTarget.length();
            if (distance < 0.001 || distance > range) {
                continue;
            }
            double dot = direction.dot(toTarget.normalize());
            if (dot < halfAngleCos) {
                continue;
            }

            target.damage(damage, caster);
            Vector launch = direction.clone().multiply(0.6);
            launch.setY(Math.max(launch.getY(), 0) + launchPower);
            target.setVelocity(launch);
            burnManager.applyBurn(target, caster.getUniqueId(), burnDuration, burnDps);
            plugin.getCurseManager().applyCurse(target, "수르트의 불길", 0.0, 100.0, -1, flameHealBlockSeconds());
        }

        List<BlockSnapshot> changed = scorchGround(world, eye, direction, range, coneAngle);
        spawnBreathParticles(world, eye, direction, range);

        if (!changed.isEmpty()) {
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                for (BlockSnapshot snapshot : changed) {
                    Block block = snapshot.location().getBlock();
                    if (block.getType() == Material.MAGMA_BLOCK) {
                        block.setBlockData(snapshot.data());
                    }
                }
            }, magmaDuration * 20L);
        }

        return true;
    }

    // ----- [웅크리기+우클릭] 라그나로크 -----

    public int ragnarokCooldownSeconds() {
        return plugin.getConfig().getInt("laevateinn.ragnarok.cooldown-seconds", 120);
    }

    public int ragnarokBurnSeconds() {
        return plugin.getConfig().getInt("laevateinn.ragnarok.burn-duration-seconds", 12);
    }

    public double ragnarokBurnDamagePerSecond() {
        return plugin.getConfig().getDouble("laevateinn.ragnarok.burn-damage-per-second", 5.0);
    }

    public long remainingRagnarokCooldownSeconds(UUID uuid) {
        Long last = ragnarokCooldowns.get(uuid);
        if (last == null) {
            return 0;
        }
        return Math.max(0, ragnarokCooldownSeconds() - (System.currentTimeMillis() - last) / 1000);
    }

    public boolean useRagnarok(Player caster) {
        if (remainingRagnarokCooldownSeconds(caster.getUniqueId()) > 0) {
            return false;
        }
        ragnarokCooldowns.put(caster.getUniqueId(), System.currentTimeMillis());

        double range = plugin.getConfig().getDouble("laevateinn.ragnarok.range", 10.0);
        double coneAngle = plugin.getConfig().getDouble("laevateinn.ragnarok.cone-angle-degrees", 150.0);
        double damage = plugin.getConfig().getDouble("laevateinn.ragnarok.damage", 18.0);
        int castTicks = plugin.getConfig().getInt("laevateinn.ragnarok.cast-ticks", 20);

        CurseManager curseManager = plugin.getCurseManager();
        curseManager.grantImmunity(caster, castTicks + 20);

        World world = caster.getWorld();
        Location eye = caster.getEyeLocation();
        world.playSound(eye, Sound.ENTITY_WITHER_SPAWN, 1.4f, 0.7f);

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!caster.isOnline()) {
                return;
            }
            World castWorld = caster.getWorld();
            Location castEye = caster.getEyeLocation();
            Vector direction = castEye.getDirection().normalize();

            castWorld.playSound(castEye, Sound.ENTITY_GENERIC_EXPLODE, 2.0f, 0.5f);
            castWorld.spawnParticle(Particle.EXPLOSION_EMITTER, castEye.clone().add(direction.clone().multiply(3)), 3);
            spawnBreathParticles(castWorld, castEye, direction, range);

            InfernalBurnManager burnManager = plugin.getInfernalBurnManager();
            for (LivingEntity target : SkillTargets.inCone(castWorld, castEye, direction, range, coneAngle, caster)) {
                curseManager.dealTrueDamage(target, caster, damage);
                burnManager.applyEternalBurn(target, caster.getUniqueId(),
                        ragnarokBurnSeconds(), ragnarokBurnDamagePerSecond());
            }
        }, castTicks);

        return true;
    }

    private List<BlockSnapshot> scorchGround(World world, Location eye, Vector direction, double range, double coneAngleDegrees) {
        List<BlockSnapshot> changed = new ArrayList<>();
        Vector forward = direction.clone().setY(0);
        if (forward.lengthSquared() < 1.0E-4) {
            return changed;
        }
        forward.normalize();
        Vector right = new Vector(-forward.getZ(), 0, forward.getX());
        double halfWidthAngle = Math.toRadians(coneAngleDegrees / 2.0);

        int maxDistance = (int) Math.ceil(range);
        for (int d = 1; d <= maxDistance; d++) {
            double width = d * Math.tan(halfWidthAngle);
            for (double lateral = -width; lateral <= width; lateral += 1.0) {
                Location point = eye.clone().add(forward.clone().multiply(d)).add(right.clone().multiply(lateral));
                int blockX = point.getBlockX();
                int blockZ = point.getBlockZ();
                int groundY = world.getHighestBlockYAt(blockX, blockZ) - 1;
                Block block = world.getBlockAt(blockX, groundY, blockZ);
                if (block.getType().isSolid() && block.getType() != Material.MAGMA_BLOCK) {
                    changed.add(new BlockSnapshot(block.getLocation(), block.getBlockData()));
                    block.setType(Material.MAGMA_BLOCK);
                }
            }
        }
        return changed;
    }

    private void spawnBreathParticles(World world, Location eye, Vector direction, double range) {
        Vector forward = direction.clone().normalize();
        new BukkitRunnable() {
            int elapsed = 0;

            @Override
            public void run() {
                if (elapsed >= 100) {
                    cancel();
                    return;
                }
                for (int d = 1; d <= (int) range; d++) {
                    Location point = eye.clone().add(forward.clone().multiply(d));
                    world.spawnParticle(Particle.SOUL_FIRE_FLAME, point, 4, 0.3, 0.2, 0.3, 0.01);
                    world.spawnParticle(Particle.LAVA, point, 1, 0.2, 0.1, 0.2, 0);
                }
                elapsed += 5;
            }
        }.runTaskTimer(plugin, 0L, 5L);
    }
}
