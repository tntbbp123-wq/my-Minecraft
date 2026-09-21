package com.tntbbp.myminecraft.listener.item;

import com.tntbbp.myminecraft.MyMinecraftPlugin;
import com.tntbbp.myminecraft.manager.item.ArtemisSetManager;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 아르테미스 세트의 제작 판정, 세트 효과, 월광 공명.
 *
 * <p>제작법은 방어구 <b>재질</b>까지만 바닐라 제작법으로 거르고, 인챈트 조건은 여기서 확인한다.
 * 바닐라 제작법은 인챈트를 조건으로 걸 수 없기 때문이다. 조건이 맞지 않으면 결과 칸을 비운다.
 */
public class ArtemisSetListener implements Listener {

    private static final int TICK_INTERVAL = 20;

    private final MyMinecraftPlugin plugin;
    private BukkitTask moonTask;

    public ArtemisSetListener(MyMinecraftPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        moonTask = plugin.getServer().getScheduler().runTaskTimer(
                plugin, this::tickMoonResonance, TICK_INTERVAL, TICK_INTERVAL);
    }

    public void stop() {
        if (moonTask != null) {
            moonTask.cancel();
        }
    }

    // ----- 제작 판정 -----

    /**
     * 네 방향 재료의 인챈트를 확인한다. 하나라도 조건에 못 미치면 결과를 지운다.
     *
     * <p>어느 부위를 만들지는 <b>북(위 가운데)에 넣은 방어구의 부위</b>로 정해진다.
     * 투구를 넣으면 아르테미스 투구가, 흉갑을 넣으면 흉갑이 나온다.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        ArtemisSetManager manager = plugin.getArtemisSetManager();
        if (event.getRecipe() == null || !(event.getRecipe() instanceof ShapedRecipe recipe)) {
            return;
        }
        NamespacedKey key = recipe.getKey();
        if (!key.getNamespace().equals(plugin.getName().toLowerCase())
                || !key.getKey().equals("artemis_set")) {
            return;
        }
        if (!manager.enabled()) {
            event.getInventory().setResult(null);
            return;
        }

        ItemStack[] matrix = event.getInventory().getMatrix();
        // 3x3 기준 위치: 1=북, 3=서, 5=동, 7=남 (0부터 세는 인덱스)
        ItemStack north = matrix.length > 1 ? matrix[1] : null;
        ItemStack west = matrix.length > 3 ? matrix[3] : null;
        ItemStack east = matrix.length > 5 ? matrix[5] : null;
        ItemStack south = matrix.length > 7 ? matrix[7] : null;

        int level = manager.recipeEnchantLevel();
        boolean ok = hasEnchant(north, Enchantment.PROTECTION, level)
                && hasEnchant(east, Enchantment.BLAST_PROTECTION, level)
                && hasEnchant(west, Enchantment.PROJECTILE_PROTECTION, level)
                && hasEnchant(south, Enchantment.FIRE_PROTECTION, level);

        ArtemisSetManager.Piece piece = north == null ? null
                : ArtemisSetManager.Piece.ofAnyArmor(north.getType());
        if (!ok || piece == null) {
            event.getInventory().setResult(null);
            return;
        }
        event.getInventory().setResult(manager.createPiece(piece));
    }

    private boolean hasEnchant(ItemStack item, Enchantment enchantment, int minLevel) {
        return item != null && !item.getType().isAir()
                && ArtemisSetManager.Piece.ofAnyArmor(item.getType()) != null
                && item.getEnchantmentLevel(enchantment) >= minLevel;
    }

    // ----- 세트 효과: 여신의 회피 -----

    /** 피격 시 확률로 피해를 통째로 흘린다. 흘리면 짧게 신속이 붙는다. */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        double chance = plugin.getArtemisSetManager().evadePercent(player);
        if (chance <= 0 || ThreadLocalRandom.current().nextDouble() * 100.0 >= chance) {
            return;
        }
        event.setCancelled(true);
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED,
                plugin.getArtemisSetManager().evadeSwiftnessSeconds() * 20, 2, true, false, true));
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ILLUSIONER_MIRROR_MOVE, 0.9f, 1.6f);
        player.getWorld().spawnParticle(Particle.END_ROD, player.getLocation().add(0, 1, 0),
                14, 0.4, 0.6, 0.4, 0.03);
    }

    // ----- 월광 공명: 활·쇠뇌 피해 -----

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onProjectileDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Projectile projectile)
                || !(projectile.getShooter() instanceof Player shooter)) {
            return;
        }
        ArtemisSetManager manager = plugin.getArtemisSetManager();
        if (!manager.enabled() || !manager.hasFullSet(shooter)) {
            return;
        }
        double power = manager.moonPower(shooter.getWorld());
        if (power <= 0) {
            return;
        }
        // 보름달에서 설정 배율이 되도록 위상에 비례해 올린다.
        double multiplier = 1.0 + (manager.moonBowDamageMultiplier() - 1.0) * power;
        event.setDamage(event.getDamage() * multiplier);
    }

    // ----- 월광 공명: 이동·공격속도 -----

    private void tickMoonResonance() {
        ArtemisSetManager manager = plugin.getArtemisSetManager();
        if (!manager.enabled()) {
            return;
        }
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (!manager.hasFullSet(player)) {
                continue;
            }
            double power = manager.moonPower(player.getWorld());
            if (power <= 0) {
                continue;
            }
            // 신속/성급함은 단계당 20%·10%라 설정 비율에 가장 가까운 단계를 고른다.
            int speedAmplifier = Math.max(0, (int) Math.round(manager.moonSpeedPercent() * power / 20.0) - 1);
            int hasteAmplifier = Math.max(0, (int) Math.round(manager.moonSpeedPercent() * power / 10.0) - 1);
            int duration = TICK_INTERVAL * 3;
            player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, duration, speedAmplifier,
                    true, false, false));
            player.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, duration, hasteAmplifier,
                    true, false, false));

            if (manager.isFullMoonish(player.getWorld())) {
                player.getWorld().spawnParticle(Particle.END_ROD, player.getLocation().add(0, 1.2, 0),
                        2, 0.4, 0.6, 0.4, 0.01);
            }
        }
    }

    /** 제작법. 사방에 인챈트 방어구, 중앙에 네더의 별, 네 모서리에 네더라이트 블록. */
    public static ShapedRecipe recipe(MyMinecraftPlugin plugin) {
        // 결과는 제작 판정에서 부위에 맞게 갈아끼운다. 여기서는 자리만 잡아둔다.
        ShapedRecipe recipe = new ShapedRecipe(new NamespacedKey(plugin, "artemis_set"),
                plugin.getArtemisSetManager().createPiece(ArtemisSetManager.Piece.HELMET));
        recipe.shape("BAB", "CSD", "BEB");
        recipe.setIngredient('B', Material.NETHERITE_BLOCK);
        recipe.setIngredient('S', Material.NETHER_STAR);
        // 방어구 자리는 재질만 거르고, 인챈트는 ArtemisSetListener가 확인한다.
        recipe.setIngredient('A', armorChoice());
        recipe.setIngredient('C', armorChoice());
        recipe.setIngredient('D', armorChoice());
        recipe.setIngredient('E', armorChoice());
        return recipe;
    }

    private static org.bukkit.inventory.RecipeChoice armorChoice() {
        java.util.List<Material> materials = new java.util.ArrayList<>();
        for (Material material : Material.values()) {
            if (!material.isLegacy() && ArtemisSetManager.Piece.ofAnyArmor(material) != null) {
                materials.add(material);
            }
        }
        return new org.bukkit.inventory.RecipeChoice.MaterialChoice(materials);
    }

    /** 제작법 안내(관리자 확인용). */
    public static Map<String, Enchantment> recipeEnchants() {
        return ArtemisSetManager.RECIPE_ENCHANTS;
    }
}
