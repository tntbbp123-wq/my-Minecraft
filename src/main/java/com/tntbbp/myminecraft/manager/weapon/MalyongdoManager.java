package com.tntbbp.myminecraft.manager.weapon;

import com.tntbbp.myminecraft.MyMinecraftPlugin;
import com.tntbbp.myminecraft.manager.item.GradeManager;
import com.tntbbp.myminecraft.util.ItemBuilder;
import com.tntbbp.myminecraft.util.Particles;
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
 * 전설 등급 무기 '말룡도(末龍刀)'. 종말룡 엔더드래곤을 잡으면 낮은 확률로 떨어진다.
 *
 * <p>다른 무기와 달리 <b>단계별 연계</b>로 묶여 있다. 앞 단계를 밟지 않으면 뒷 단계가 열리지 않고,
 * 건너뛰거나 역순으로 쓸 수 없다.
 *
 * <ul>
 *   <li>[F] <b>1초식 말(末)</b> — 선행 조건 없음. '말' 스택을 일부 써서 전방을 내리벤다</li>
 *   <li>[웅크리기+F] <b>2초식 말룡(末龍)</b> — 1초식이 <b>적중한</b> 뒤에만. 스택 전량을 써서 파열파</li>
 *   <li>[웅크리기+우클릭] <b>3초식 종말룡(終末龍)</b> — 2초식을 쓴 뒤에만. '종말의 화신' 상태로 들어갔다가
 *       끝날 때 그동안 쌓은 파괴력을 한 번에 쏟아낸다</li>
 * </ul>
 *
 * <p>연계가 열려 있는 시간은 단계마다 다르다. 2초식이 말을 전부 먹고 가기 때문에 3초식은 더 길게
 * 열어 두고, <b>2초식에 쏟아부은 말을 3초식이 그대로 이어받는다.</b> 이어받지 않으면 남은 시간 안에
 * 3초식의 최소 말을 다시 모으는 것이 사실상 불가능하다.
 *
 * <p><b>말 스택</b>은 들고 때리면 1, 들고 있기만 해도 초당 0.5씩 쌓인다. 스택과 지금 열린 연계 단계는
 * 무기를 들고 있는 동안 액션바에 계속 보여준다.
 */
public class MalyongdoManager {

    /** 액션바 표시와 초당 스택 적립 주기. 0.5초마다 돌며 매번 0.25씩 올려 "초당 0.5"를 맞춘다. */
    private static final int TICK_INTERVAL = 10;

    /** 지금 열려 있는 연계 단계. */
    public enum Stage {
        /** 아무것도 안 쓴 상태. 1초식만 쓸 수 있다. */
        NONE,
        /** 1초식이 적중했다. 2초식이 열렸다. */
        FIRST_HIT,
        /** 2초식을 썼다. 3초식이 열렸다. */
        SECOND_USED
    }

    /**
     * 연계 진행 상황.
     *
     * @param stage           지금 밟은 단계
     * @param updatedAtMillis 이 단계로 올라간 시각. 단계별 유지 시간이 지나면 처음으로 되돌린다
     * @param carriedStacks   2초식에 쏟아부은 말. 3초식은 이 말을 이어받는다
     */
    private record Combo(Stage stage, long updatedAtMillis, int carriedStacks) {
    }

    private final MyMinecraftPlugin plugin;
    private final NamespacedKey itemKey;

    private final Map<UUID, Double> stacks = new ConcurrentHashMap<>();
    private final Map<UUID, Combo> combos = new ConcurrentHashMap<>();
    private final Map<UUID, Long> firstCooldowns = new ConcurrentHashMap<>();
    private final Map<UUID, Long> secondCooldowns = new ConcurrentHashMap<>();
    private final Map<UUID, Long> thirdCooldowns = new ConcurrentHashMap<>();
    /** '종말의 화신' 상태로 축적 중인 파괴력. */
    private final Map<UUID, Double> avatarPower = new ConcurrentHashMap<>();

    private BukkitTask tickTask;

    public MalyongdoManager(MyMinecraftPlugin plugin) {
        this.plugin = plugin;
        this.itemKey = new NamespacedKey(plugin, "malyongdo");
    }

    public void start() {
        tickTask = plugin.getServer().getScheduler().runTaskTimer(
                plugin, this::tick, TICK_INTERVAL, TICK_INTERVAL);
    }

    public void stop() {
        if (tickTask != null) {
            tickTask.cancel();
        }
        stacks.clear();
        combos.clear();
        avatarPower.clear();
    }

    public ItemStack createItem() {
        double attackDamage = attackDamage();

        ItemStack item = new ItemBuilder(Material.NETHERITE_SWORD)
                .name("§e§l말룡도 §7(末龍刀)")
                .lore(List.of(
                        "§e§l전설 (Legendary) §8| §7무기 §8| §7공격력 §c" + formatNumber(attackDamage)
                                + " §8| §7공격속도 §e1.6",
                        "§7종말룡의 턱뼈를 갈아 벼려낸 대도",
                        "",
                        "§b[자원] §f말(末) §7— 최대 §f" + maxStacks() + "말",
                        "§7 때리면 §f1말§7, 들고 있으면 §f초당 0.5말",
                        "",
                        "§e[연계] §7앞 단계를 밟아야 다음 초식이 열린다",
                        "§7 1초식 적중 §8→ §72초식 §8(§f" + comboWindowSeconds() + "초§8)"
                                + " §8→ §73초식 §8(§f" + secondComboWindowSeconds() + "초§8)",
                        "",
                        "§6[F] §f1초식 말 §7(末 · 재사용 " + firstCooldownSeconds() + "초)",
                        "§7 말 §f" + firstCost() + "말 §7소모, 전방에 §c" + formatNumber(firstDamage()) + " §7참격",
                        "",
                        "§6[웅크리기+F] §f2초식 말룡 §7(末龍 · 재사용 " + secondCooldownSeconds() + "초)",
                        "§8 └ §71초식이 §f적중§7한 뒤에만",
                        "§7 말 §f전량 §7소모, 1말당 §c" + formatNumber(secondDamagePerStack()) + " §7피해",
                        "§7 소모한 말에 비례해 방어관통 (최대 §f"
                                + formatNumber(secondMaxPenetrationPercent()) + "%§7)",
                        "",
                        "§6[웅크리기+우클릭] §f3초식 종말룡 §7(終末龍 · 재사용 "
                                + thirdCooldownSeconds() + "초)",
                        "§8 └ §72초식을 쓴 뒤에만 · §f2초식에 쏟은 말 §7+ §f지금 가진 말 §7"
                                + thirdMinStacks() + "말 이상",
                        "§7 " + avatarSeconds() + "초간 §5종말의 화신§7: 상태이상 면역,",
                        "§7 그동안 입힌 피해가 파괴력으로 쌓인다",
                        "§7 끝나는 순간 쌓인 파괴력을 암흑 용으로 쏟아낸다",
                        "",
                        "§7\"끝을 삼킨 용의 이빨은 끝에서야 열린다\""
                ))
                .glow()
                .build();

        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(itemKey, PersistentDataType.BYTE, (byte) 1);
        meta.setUnbreakable(true);
        meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE, ItemFlag.HIDE_ATTRIBUTES);

        WeaponAttributes.applyBase(meta, Material.NETHERITE_SWORD, attackDamage,
                new NamespacedKey(plugin, "malyongdo_attack_damage"));

        int modelData = plugin.getConfig().getInt("malyongdo.model-data", 0);
        if (modelData != 0) {
            meta.setCustomModelData(modelData);
        }
        item.setItemMeta(meta);

        plugin.getGradeManager().applyGrade(item, GradeManager.Grade.LEGEND);
        return item;
    }

    public boolean isMalyongdo(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        Byte flag = item.getItemMeta().getPersistentDataContainer().get(itemKey, PersistentDataType.BYTE);
        return flag != null && flag == (byte) 1;
    }

    // ----- 설정 -----

    public double attackDamage() {
        return plugin.getConfig().getDouble("malyongdo.attack-damage", 12.0);
    }

    /** 종말룡 엔더드래곤을 잡았을 때 떨어질 확률(%). */
    public double dropChancePercent() {
        return plugin.getConfig().getDouble("malyongdo.drop-chance-percent", 10.0);
    }

    public int maxStacks() {
        return Math.max(1, plugin.getConfig().getInt("malyongdo.stack.max", 100));
    }

    private double stacksPerHit() {
        return plugin.getConfig().getDouble("malyongdo.stack.per-hit", 1.0);
    }

    private double stacksPerSecond() {
        return plugin.getConfig().getDouble("malyongdo.stack.per-second", 0.5);
    }

    /** 1초식이 적중한 뒤 2초식이 열려 있는 시간. 지나면 1초식부터 다시 시작한다. */
    public int comboWindowSeconds() {
        return plugin.getConfig().getInt("malyongdo.combo-window-seconds", 25);
    }

    /** 2초식을 쓴 뒤 3초식이 열려 있는 시간. 3초식은 말을 다시 모아야 해서 더 길게 잡는다. */
    public int secondComboWindowSeconds() {
        return plugin.getConfig().getInt("malyongdo.second-combo-window-seconds", 40);
    }

    public int firstCooldownSeconds() {
        return plugin.getConfig().getInt("malyongdo.first.cooldown-seconds", 6);
    }

    public int firstCost() {
        return plugin.getConfig().getInt("malyongdo.first.stack-cost", 20);
    }

    public double firstDamage() {
        return plugin.getConfig().getDouble("malyongdo.first.damage", 12.0);
    }

    public int secondCooldownSeconds() {
        return plugin.getConfig().getInt("malyongdo.second.cooldown-seconds", 20);
    }

    public double secondDamagePerStack() {
        return plugin.getConfig().getDouble("malyongdo.second.damage-per-stack", 0.35);
    }

    public double secondMaxPenetrationPercent() {
        return plugin.getConfig().getDouble("malyongdo.second.max-penetration-percent", 30.0);
    }

    public int thirdCooldownSeconds() {
        return plugin.getConfig().getInt("malyongdo.third.cooldown-seconds", 90);
    }

    public int thirdMinStacks() {
        return plugin.getConfig().getInt("malyongdo.third.min-stacks", 50);
    }

    public int avatarSeconds() {
        return plugin.getConfig().getInt("malyongdo.third.avatar-seconds", 5);
    }

    /** '종말의 화신' 중 입힌 피해가 파괴력으로 쌓이는 비율. */
    private double avatarPowerRatio() {
        return plugin.getConfig().getDouble("malyongdo.third.power-ratio", 1.0);
    }

    // ----- 말 스택 -----

    public int stacks(UUID uuid) {
        return (int) Math.floor(stacks.getOrDefault(uuid, 0.0));
    }

    /** 때렸을 때 스택을 1 올린다. */
    public void addHitStack(UUID uuid) {
        addStacks(uuid, stacksPerHit());
    }

    private void addStacks(UUID uuid, double amount) {
        stacks.merge(uuid, amount, (a, b) -> Math.min(maxStacks(), a + b));
    }

    private void consumeStacks(UUID uuid, double amount) {
        // merge를 쓰면 키가 없을 때 음수가 그대로 들어간다. 지금은 스택을 확인한 뒤에만 부르지만,
        // 나중에 호출부가 늘어도 음수로 내려가지 않도록 compute로 막아둔다.
        stacks.compute(uuid, (key, value) -> Math.max(0.0, (value == null ? 0.0 : value) - amount));
    }

    // ----- 연계 단계 -----

    /** 그 단계가 열려 있는 시간(초). */
    private int windowSecondsFor(Stage stage) {
        return switch (stage) {
            case NONE -> 0;
            case FIRST_HIT -> comboWindowSeconds();
            case SECOND_USED -> secondComboWindowSeconds();
        };
    }

    /** 아직 살아 있는 연계. 유지 시간이 지났으면 지우고 null을 준다. */
    private Combo activeCombo(UUID uuid) {
        Combo combo = combos.get(uuid);
        if (combo == null) {
            return null;
        }
        long elapsed = System.currentTimeMillis() - combo.updatedAtMillis();
        if (elapsed > windowSecondsFor(combo.stage()) * 1000L) {
            combos.remove(uuid);
            return null;
        }
        return combo;
    }

    /** 지금 열려 있는 단계. 연계 유지 시간이 지났으면 처음으로 되돌린다. */
    public Stage stage(UUID uuid) {
        Combo combo = activeCombo(uuid);
        return combo == null ? Stage.NONE : combo.stage();
    }

    /** 지금 열린 연계가 닫히기까지 남은 시간(초). 열린 연계가 없으면 0. */
    public long remainingComboSeconds(UUID uuid) {
        Combo combo = activeCombo(uuid);
        if (combo == null) {
            return 0;
        }
        long elapsed = System.currentTimeMillis() - combo.updatedAtMillis();
        return Math.max(0, windowSecondsFor(combo.stage()) - elapsed / 1000);
    }

    /** 2초식에 쏟아부어 3초식으로 이어지는 말. 연계가 닫혔으면 0. */
    private int carriedStacks(UUID uuid) {
        Combo combo = activeCombo(uuid);
        return combo == null ? 0 : combo.carriedStacks();
    }

    private void setStage(UUID uuid, Stage stage) {
        setStage(uuid, stage, 0);
    }

    private void setStage(UUID uuid, Stage stage, int carriedStacks) {
        if (stage == Stage.NONE) {
            combos.remove(uuid);
            return;
        }
        combos.put(uuid, new Combo(stage, System.currentTimeMillis(), carriedStacks));
    }

    // ----- [F] 1초식 말 -----

    public long remainingFirstCooldown(UUID uuid) {
        return remainingCooldown(firstCooldowns, uuid, firstCooldownSeconds());
    }

    /**
     * 1초식. 선행 조건은 없고 말 스택만 있으면 된다. 적중해야 2초식이 열린다.
     *
     * @return 실패 사유. 성공이면 null
     */
    public String useFirst(Player caster) {
        if (remainingFirstCooldown(caster.getUniqueId()) > 0) {
            return "1초식 말 재사용 대기 중입니다. (" + remainingFirstCooldown(caster.getUniqueId()) + "초)";
        }
        if (stacks(caster.getUniqueId()) < firstCost()) {
            return "말이 부족합니다. (" + stacks(caster.getUniqueId()) + " / " + firstCost() + "말)";
        }
        firstCooldowns.put(caster.getUniqueId(), System.currentTimeMillis());
        consumeStacks(caster.getUniqueId(), firstCost());

        World world = caster.getWorld();
        Location eye = caster.getEyeLocation();
        Vector direction = eye.getDirection().normalize();
        double range = plugin.getConfig().getDouble("malyongdo.first.range", 5.0);
        double coneAngle = plugin.getConfig().getDouble("malyongdo.first.cone-angle-degrees", 80.0);

        world.playSound(eye, Sound.ENTITY_PLAYER_ATTACK_STRONG, 1.5f, 0.6f);
        spawnHeavySlash(world, eye, direction, range);

        boolean hit = false;
        for (LivingEntity target : SkillTargets.inCone(world, eye, direction, range, coneAngle, caster)) {
            target.damage(firstDamage(), caster);
            hit = true;
        }

        Stage before = stage(caster.getUniqueId());
        if (hit) {
            if (before == Stage.SECOND_USED) {
                // 3초식이 열려 있는 동안 평소처럼 F를 눌렀다고 해서 연계를 뒤로 되돌리지는 않는다.
                // 되돌리면 2초식에 쏟은 말이 통째로 날아가 버린다.
                caster.sendMessage("§c1초식 말! §7— 3초식 종말룡이 그대로 열려 있습니다. ("
                        + remainingComboSeconds(caster.getUniqueId()) + "초)");
            } else {
                setStage(caster.getUniqueId(), Stage.FIRST_HIT);
                caster.sendMessage("§c1초식 말! §7— 2초식 말룡이 열렸습니다. ("
                        + comboWindowSeconds() + "초)");
            }
        } else if (before == Stage.SECOND_USED) {
            caster.sendMessage("§71초식 말이 빗나갔습니다. §73초식 종말룡은 그대로 열려 있습니다.");
        } else {
            // 빗나가면 연계가 열리지 않는다. 스펙상 "적중했거나 활성화된 상태"여야 다음 단계로 간다.
            setStage(caster.getUniqueId(), Stage.NONE);
            caster.sendMessage("§71초식 말이 빗나갔습니다. 연계가 열리지 않습니다.");
        }
        return null;
    }

    // ----- [웅크리기+F] 2초식 말룡 -----

    public long remainingSecondCooldown(UUID uuid) {
        return remainingCooldown(secondCooldowns, uuid, secondCooldownSeconds());
    }

    /** 2초식. 1초식이 적중한 뒤에만 열린다. 남은 말을 전부 쏟는다. */
    public String useSecond(Player caster) {
        UUID uuid = caster.getUniqueId();
        if (stage(uuid) != Stage.FIRST_HIT) {
            return "1초식 말을 먼저 적중시켜야 합니다.";
        }
        if (remainingSecondCooldown(uuid) > 0) {
            return "2초식 말룡 재사용 대기 중입니다. (" + remainingSecondCooldown(uuid) + "초)";
        }
        int spent = stacks(uuid);
        if (spent <= 0) {
            // 1초식이 말을 먹고 가므로 여기서 0이 되기 쉽다. 연계가 얼마나 남았는지까지 알려준다.
            return "쏟아낼 말이 없습니다. 말을 더 모으세요. (2초식 "
                    + remainingComboSeconds(uuid) + "초 남음)";
        }
        secondCooldowns.put(uuid, System.currentTimeMillis());
        consumeStacks(uuid, spent);

        World world = caster.getWorld();
        Location eye = caster.getEyeLocation();
        Vector direction = eye.getDirection().normalize();
        double range = plugin.getConfig().getDouble("malyongdo.second.range", 9.0);
        double coneAngle = plugin.getConfig().getDouble("malyongdo.second.cone-angle-degrees", 60.0);

        double damage = spent * secondDamagePerStack();
        // 스택을 가득 채웠을 때 설정된 최대치가 되도록 비례시킨다.
        double penetration = secondMaxPenetrationPercent() * Math.min(1.0, (double) spent / maxStacks());

        world.playSound(eye, Sound.ENTITY_ENDER_DRAGON_SHOOT, 1.6f, 0.7f);
        world.playSound(eye, Sound.ENTITY_BLAZE_SHOOT, 1.4f, 0.5f);
        spawnClawMarks(world, eye, direction, range);

        for (LivingEntity target : SkillTargets.inCone(world, eye, direction, range, coneAngle, caster)) {
            // 방어관통분은 방어력을 무시하는 고정 피해로 따로 넣는다. 나머지는 평범한 피해라
            // 방어구가 그대로 줄여준다.
            double trueDamage = damage * (penetration / 100.0);
            target.damage(damage - trueDamage, caster);
            if (trueDamage > 0) {
                plugin.getCurseManager().dealTrueDamage(target, caster, trueDamage);
            }
            target.setFireTicks(Math.max(target.getFireTicks(),
                    plugin.getConfig().getInt("malyongdo.second.fire-ticks", 60)));
        }

        // 쏟아부은 말은 3초식으로 이어진다. 2초식이 말을 전부 먹기 때문에, 이어받지 않으면
        // 남은 유지 시간 안에 3초식 최소 말을 다시 모으는 것이 사실상 불가능하다.
        setStage(uuid, Stage.SECOND_USED, spent);
        caster.sendMessage("§42초식 말룡! §7— " + spent + "말 소모, 방어관통 "
                + formatNumber(Math.round(penetration * 10) / 10.0) + "% §8· §73초식이 열렸습니다. ("
                + secondComboWindowSeconds() + "초, 쏟은 " + spent + "말이 이어집니다)");
        return null;
    }

    // ----- [웅크리기+우클릭] 3초식 종말룡 -----

    public long remainingThirdCooldown(UUID uuid) {
        return remainingCooldown(thirdCooldowns, uuid, thirdCooldownSeconds());
    }

    /** '종말의 화신' 상태인지. 이 동안 입힌 피해가 파괴력으로 쌓인다. */
    public boolean isAvatar(UUID uuid) {
        return avatarPower.containsKey(uuid);
    }

    /** 화신 상태에서 입힌 피해를 파괴력으로 쌓는다. */
    public void accumulatePower(UUID uuid, double damage) {
        if (damage <= 0) {
            return;
        }
        avatarPower.computeIfPresent(uuid, (key, value) -> value + damage * avatarPowerRatio());
    }

    /** 3초식. 2초식을 쓴 뒤에만 열린다. */
    public String useThird(Player caster) {
        UUID uuid = caster.getUniqueId();
        if (stage(uuid) != Stage.SECOND_USED) {
            return "2초식 말룡을 먼저 써야 합니다.";
        }
        if (remainingThirdCooldown(uuid) > 0) {
            return "3초식 종말룡 재사용 대기 중입니다. (" + remainingThirdCooldown(uuid) + "초)";
        }
        // 2초식에 쏟은 말이 그대로 이어지고, 그 뒤에 다시 모은 말을 더해서 따진다.
        int carried = carriedStacks(uuid);
        int available = carried + stacks(uuid);
        if (available < thirdMinStacks()) {
            return "말이 부족합니다. (" + available + " / " + thirdMinStacks() + "말"
                    + (carried > 0 ? ", 2초식에서 이어받은 " + carried + "말 포함" : "") + ")";
        }
        if (isAvatar(uuid)) {
            return "이미 종말의 화신 상태입니다.";
        }
        thirdCooldowns.put(uuid, System.currentTimeMillis());

        int spent = available;
        consumeStacks(uuid, stacks(uuid));
        // 이어받은 말은 여기서 다 쓴다. 화신 처리가 도중에 끊겨도 두 번 세지 않도록 바로 비운다.
        setStage(uuid, Stage.SECOND_USED, 0);
        int seconds = avatarSeconds();

        // 시작 파괴력은 쏟아부은 말에서 나온다. 여기에 화신 동안 입힌 피해가 더 쌓인다.
        avatarPower.put(uuid, spent * plugin.getConfig().getDouble("malyongdo.third.power-per-stack", 0.4));
        plugin.getCurseManager().grantImmunity(caster, seconds * 20 + 10);

        World world = caster.getWorld();
        world.playSound(caster.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 2.0f, 0.5f);
        caster.sendMessage("§53초식 종말룡! §7— " + seconds + "초간 §5종말의 화신§7. 끝나는 순간 쏟아집니다.");

        new BukkitRunnable() {
            int elapsedTicks = 0;

            @Override
            public void run() {
                elapsedTicks += 5;
                if (!caster.isOnline()) {
                    avatarPower.remove(uuid);
                    cancel();
                    return;
                }
                Location center = caster.getLocation().add(0, 1.0, 0);
                caster.getWorld().spawnParticle(Particle.DUST, center, 12, 0.6, 0.8, 0.6, 0,
                        new Particle.DustOptions(Color.fromRGB(60, 0, 80), 1.6f));

                if (elapsedTicks >= seconds * 20) {
                    releaseAvatar(caster);
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 5L, 5L);

        return null;
    }

    /** 화신이 끝나는 순간 쌓인 파괴력을 전방으로 쏟아낸다. */
    private void releaseAvatar(Player caster) {
        Double power = avatarPower.remove(caster.getUniqueId());
        if (power == null) {
            return;
        }
        World world = caster.getWorld();
        Location eye = caster.getEyeLocation();
        Vector direction = eye.getDirection().normalize();
        double range = plugin.getConfig().getDouble("malyongdo.third.range", 12.0);
        double coneAngle = plugin.getConfig().getDouble("malyongdo.third.cone-angle-degrees", 70.0);

        world.playSound(eye, Sound.ENTITY_ENDER_DRAGON_DEATH, 1.8f, 0.8f);
        world.playSound(eye, Sound.ENTITY_GENERIC_EXPLODE, 1.6f, 0.6f);
        spawnDragonMaw(world, eye, direction, range);

        for (LivingEntity target : SkillTargets.inCone(world, eye, direction, range, coneAngle, caster)) {
            // 초토화는 방어를 무시한다. 고정 피해로 넣어야 넉백·전투 태그·사망 기여자가 남는다.
            plugin.getCurseManager().dealTrueDamage(target, caster, power);
            target.setFireTicks(Math.max(target.getFireTicks(),
                    plugin.getConfig().getInt("malyongdo.third.fire-ticks", 100)));
        }
        caster.sendMessage("§5종말룡의 아가리! §7— 파괴력 §c" + formatNumber(Math.round(power * 10) / 10.0)
                + " §7를 쏟아냈습니다.");
        // 연계는 여기서 끝난다. 다시 1초식부터.
        setStage(caster.getUniqueId(), Stage.NONE);
    }

    // ----- 주기 처리 -----

    private void tick() {
        double perTick = stacksPerSecond() * TICK_INTERVAL / 20.0;
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (!isMalyongdo(player.getInventory().getItemInMainHand())) {
                continue;
            }
            addStacks(player.getUniqueId(), perTick);
            showActionBar(player);
        }
    }

    /**
     * 스택과 지금 열린 연계 단계를 액션바로 보여준다. 로어는 실시간으로 못 바꾼다.
     *
     * <p>초식 이름은 항상 <b>번호까지 통째로</b> 적는다. 예전에는 "1초식"을 §8(짙은 회색)로 칠하고
     * 기술명만 밝게 칠해서, 어두운 배경에서는 "말"·"말룡"만 보여 무엇이 열렸는지 읽을 수 없었다.
     * 이제 밟은 단계는 초록, 지금 쓸 수 있는 단계는 밝은 색, 아직 잠긴 단계만 회색으로 구분한다.
     */
    private void showActionBar(Player player) {
        UUID uuid = player.getUniqueId();
        int current = stacks(uuid);
        Stage stage = stage(uuid);
        long remaining = remainingComboSeconds(uuid);

        String combo = switch (stage) {
            case NONE -> "§e▶ 1초식 말 §8› §72초식 말룡 §8› §73초식 종말룡";
            case FIRST_HIT -> "§a✔ 1초식 말 §8› §c▶ 2초식 말룡 §8› §73초식 종말룡 §8[§f"
                    + remaining + "초§8]";
            case SECOND_USED -> "§a✔ 1초식 말 §8› §a✔ 2초식 말룡 §8› §d▶ 3초식 종말룡 §8[§f"
                    + remaining + "초§8]";
        };
        // 3초식은 2초식에 쏟은 말을 이어받으므로, 그 몫까지 더해서 보여줘야 숫자가 맞는다.
        int usable = current + (stage == Stage.SECOND_USED ? carriedStacks(uuid) : 0);
        String stackText = usable == current
                ? "§f" + current
                : "§f" + current + "§7(+" + (usable - current) + ")";
        String avatar = isAvatar(uuid) ? " §8| §5종말의 화신" : "";

        player.sendActionBar(LegacyComponentSerializer.legacySection().deserialize(
                "§6말(末) " + stackText + "§8/§7" + maxStacks() + " §8| " + combo + avatar));
    }

    // ----- 내부 -----

    private long remainingCooldown(Map<UUID, Long> cooldowns, UUID uuid, int cooldownSeconds) {
        Long last = cooldowns.get(uuid);
        if (last == null) {
            return 0;
        }
        return Math.max(0, cooldownSeconds - (System.currentTimeMillis() - last) / 1000);
    }

    private void spawnHeavySlash(World world, Location eye, Vector direction, double range) {
        Vector forward = direction.clone().setY(0);
        if (forward.lengthSquared() < 1.0E-4) {
            return;
        }
        forward.normalize();
        Vector right = new Vector(-forward.getZ(), 0, forward.getX());
        for (double angle = -50; angle <= 50; angle += 5) {
            double radians = Math.toRadians(angle);
            Vector offset = forward.clone().multiply(Math.cos(radians) * range * 0.7)
                    .add(right.clone().multiply(Math.sin(radians) * range * 0.7));
            world.spawnParticle(Particle.DUST, eye.clone().add(offset), 2, 0.05, 0.05, 0.05, 0,
                    new Particle.DustOptions(Color.fromRGB(180, 40, 40), 1.5f));
        }
    }

    /** 용의 발톱 자국 세 줄. */
    private void spawnClawMarks(World world, Location eye, Vector direction, double range) {
        Vector forward = direction.clone().setY(0);
        if (forward.lengthSquared() < 1.0E-4) {
            return;
        }
        forward.normalize();
        Vector right = new Vector(-forward.getZ(), 0, forward.getX());
        for (int claw = -1; claw <= 1; claw++) {
            for (double step = 0.5; step <= range; step += 0.5) {
                Location point = eye.clone()
                        .add(forward.clone().multiply(step))
                        .add(right.clone().multiply(claw * step * 0.18));
                world.spawnParticle(Particle.FLAME, point, 2, 0.05, 0.05, 0.05, 0.0);
                world.spawnParticle(Particle.DUST, point, 1, 0.05, 0.05, 0.05, 0,
                        new Particle.DustOptions(Color.fromRGB(30, 0, 45), 1.4f));
            }
        }
    }

    /** 전방에 벌어지는 암흑 용의 아가리. */
    private void spawnDragonMaw(World world, Location eye, Vector direction, double range) {
        Location impact = eye.clone().add(direction.clone().multiply(range * 0.5));
        for (double radius = 0.5; radius <= 4.0; radius += 0.5) {
            for (double angle = 0; angle < 360; angle += 15) {
                double radians = Math.toRadians(angle);
                Location point = impact.clone().add(
                        Math.cos(radians) * radius, Math.sin(radians) * radius * 0.6, 0);
                world.spawnParticle(Particle.DUST, point, 1, 0, 0, 0, 0,
                        new Particle.DustOptions(Color.fromRGB(80, 0, 110), 1.8f));
            }
        }
        Particles.flash(world, impact, 2);
        world.spawnParticle(Particle.SONIC_BOOM, impact, 1);
    }

    private String formatNumber(double value) {
        return value == Math.floor(value) ? String.valueOf((long) value) : String.valueOf(value);
    }
}
