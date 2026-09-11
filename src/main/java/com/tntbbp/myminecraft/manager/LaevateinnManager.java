package com.tntbbp.myminecraft.manager;

import com.tntbbp.myminecraft.MyMinecraftPlugin;
import com.tntbbp.myminecraft.util.ItemBuilder;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlotGroup;
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

/** 신화 등급 무기 '레바테인'의 아이템 정의와 '라그나로크의 숨결' 액티브 능력. */
public class LaevateinnManager {

    private record BlockSnapshot(Location location, BlockData data) {
    }

    private final MyMinecraftPlugin plugin;
    private final NamespacedKey itemKey;
    private final Map<UUID, Long> abilityCooldowns = new HashMap<>();

    public LaevateinnManager(MyMinecraftPlugin plugin) {
        this.plugin = plugin;
        this.itemKey = new NamespacedKey(plugin, "laevateinn");
    }

    public ItemStack createItem() {
        double attackDamage = plugin.getConfig().getDouble("laevateinn.attack-damage", 12.0);

        ItemStack item = new ItemBuilder(Material.NETHERITE_SWORD)
                .name("§5§l레바테인 §7(Lævateinn)")
                .lore(List.of(
                        "§d§l신화 (Mythic)",
                        "§7공격력: §f" + formatNumber(attackDamage),
                        "",
                        "§7\"로키가 니플헤임의 깊은 곳에서 담금질하고,",
                        "§7수르트가 세계의 종말에 휘두를 것이라 전해지는",
                        "§7태고의 마검.\"",
                        "",
                        "§c영원한 불꽃",
                        "§7 공격 시 오직 다량의 물로만 꺼지는",
                        "§7 마법의 불꽃이 옮겨붙습니다.",
                        "§c지옥의 화상",
                        "§7 방어력을 무시하는 지속 피해를 주며,",
                        "§7 대상은 불타는 동안 치유 효과가 감소합니다.",
                        "§6라그나로크의 숨결 §7(F키)",
                        "§7 전방에 거대한 화염을 내뿜어",
                        "§7 광역 피해 + 띄우기 + 화상을 입히고,",
                        "§7 땅을 일시적으로 마그마로 바꿉니다.",
                        "§8재사용 대기시간: " + abilityCooldownSeconds() + "초"
                ))
                .glow()
                .build();

        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(itemKey, PersistentDataType.BYTE, (byte) 1);
        meta.setUnbreakable(true);
        meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE, ItemFlag.HIDE_ATTRIBUTES);
        meta.addEnchant(Enchantment.FIRE_ASPECT, 2, true);

        AttributeModifier attackDamageModifier = new AttributeModifier(
                new NamespacedKey(plugin, "laevateinn_attack_damage"),
                attackDamage - 1.0, // 맨손 기본 공격력(1.0)을 더하면 툴팁상 공격력이 attackDamage가 됨
                AttributeModifier.Operation.ADD_NUMBER,
                EquipmentSlotGroup.MAINHAND);
        meta.addAttributeModifier(Attribute.GENERIC_ATTACK_DAMAGE, attackDamageModifier);

        int modelData = plugin.getConfig().getInt("laevateinn.model-data", 0);
        if (modelData != 0) {
            meta.setCustomModelData(modelData);
        }
        item.setItemMeta(meta);

        // 레바테인은 신화 등급 무기이므로 처음부터 최고 등급(마스터)으로 지급되고, 강화 한계치도
        // 바로 30강까지 열려 있다 (초월의 제단에서 다시 초월할 필요가 없다).
        plugin.getGradeManager().applyGrade(item, GradeManager.Grade.MASTER);
        plugin.getEnhanceManager().markTranscended(item);
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

    // ----- 온히트: 지옥의 화상 -----

    public int onHitBurnDurationSeconds() {
        return plugin.getConfig().getInt("laevateinn.onhit.burn-duration-seconds", 6);
    }

    public double onHitBurnDamagePerSecond() {
        return plugin.getConfig().getDouble("laevateinn.onhit.burn-damage-per-second", 3.0);
    }

    // ----- 라그나로크의 숨결 -----

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

    public boolean useBreathOfRagnarok(Player caster) {
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
