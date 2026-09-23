package com.tntbbp.myminecraft.listener.raid;

import com.tntbbp.myminecraft.MyMinecraftPlugin;
import com.tntbbp.myminecraft.manager.mail.MailSpec;
import com.tntbbp.myminecraft.manager.raid.RaidBossManager;
import com.tntbbp.myminecraft.manager.weapon.MalyongdoManager;
import com.tntbbp.myminecraft.model.MailSenderType;
import com.tntbbp.myminecraft.raid.RaidBoss;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/** 레이드 보스 본체와 보조 엔티티(영혼의 파편 등)에 대한 피해/사망을 해당 보스에게 전달한다. */
public class RaidBossListener implements Listener {

    private final MyMinecraftPlugin plugin;

    public RaidBossListener(MyMinecraftPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * 보스가 받는 피해를 가로챈다. 무적 페이즈면 전부 무효화하고, 이번 피해로 체력이 0 이하가
     * 되면 보스에게 "진짜 죽을지"를 물어본다 (불사 기믹이 false를 반환하면 피해가 취소된다).
     */
    /** 청크가 올라와 엔티티가 로드될 때, 추적하지 않는 보스 잔재를 치운다. */
    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        plugin.getRaidBossManager().removeOrphans(event.getEntities());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBossDamage(EntityDamageEvent event) {
        RaidBossManager manager = plugin.getRaidBossManager();
        RaidBoss boss = manager.byEntity(event.getEntity());
        if (boss == null) {
            return;
        }

        if (boss.isInvulnerable()) {
            event.setCancelled(true);
            return;
        }

        boss.onDamage(event);
        if (event.isCancelled()) {
            return;
        }

        if (boss.entity().getHealth() - event.getFinalDamage() > 0) {
            return;
        }
        if (!boss.onLethalDamage(event)) {
            event.setCancelled(true);
        }
    }

    /** 영혼의 파편 같은 보조 엔티티는 플레이어가 때리면 주인 보스가 처리한다. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onAuxEntityDamage(EntityDamageByEntityEvent event) {
        RaidBoss boss = plugin.getRaidBossManager().byAuxEntity(event.getEntity());
        if (boss == null) {
            return;
        }
        if (!(event.getDamager() instanceof Player attacker)) {
            event.setCancelled(true);
            return;
        }

        event.setCancelled(true);
        boss.onAuxEntityHit(event.getEntity(), attacker);
    }

    /** 회복을 막는 보스가 하나라도 있으면 회복을 취소한다 ('종말룡'의 공간 분할). */
    @EventHandler(ignoreCancelled = true)
    public void onRegainHealth(EntityRegainHealthEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        for (RaidBoss boss : plugin.getRaidBossManager().activeBosses()) {
            if (boss.blocksHealing(player)) {
                event.setCancelled(true);
                return;
            }
        }
    }

    // ----- 사고 정지 중 행동 차단 ('기억할 수 없는 자'의 응시) -----

    /** 이동은 포션 효과로도 거의 막히지만, 순간이동성 이동까지 확실히 묶기 위해 좌표 이동만 되돌린다. */
    @EventHandler(ignoreCancelled = true)
    public void onMindBrokenMove(PlayerMoveEvent event) {
        if (!plugin.getRaidBossManager().isMindBroken(event.getPlayer().getUniqueId())) {
            return;
        }
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null || (from.getBlockX() == to.getBlockX() && from.getBlockZ() == to.getBlockZ()
                && from.getBlockY() == to.getBlockY())) {
            return;
        }
        // 시점 회전은 그대로 두고 위치만 고정한다.
        event.setTo(new Location(from.getWorld(), from.getX(), from.getY(), from.getZ(),
                to.getYaw(), to.getPitch()));
    }

    @EventHandler(ignoreCancelled = true)
    public void onMindBrokenCommand(PlayerCommandPreprocessEvent event) {
        if (plugin.getRaidBossManager().isMindBroken(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("§8사고가 정지되어 아무것도 할 수 없습니다.");
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onMindBrokenChat(AsyncPlayerChatEvent event) {
        if (plugin.getRaidBossManager().isMindBroken(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    /** 우클릭 스킬과 F·Q 키 발동(레바테인·드라켄피어스·글레이프니르 등)을 모두 막는다. */
    @EventHandler(ignoreCancelled = true)
    public void onMindBrokenInteract(PlayerInteractEvent event) {
        if (plugin.getRaidBossManager().isMindBroken(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onMindBrokenSwapHands(PlayerSwapHandItemsEvent event) {
        if (plugin.getRaidBossManager().isMindBroken(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onMindBrokenDrop(PlayerDropItemEvent event) {
        if (plugin.getRaidBossManager().isMindBroken(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onBossDeath(EntityDeathEvent event) {
        RaidBossManager manager = plugin.getRaidBossManager();
        RaidBoss boss = manager.byEntity(event.getEntity());
        if (boss == null) {
            return;
        }
        boss.onDeath();
        dropMalyongdo(boss, event);
        manager.cleanUp(boss);
    }

    /**
     * 종말룡 엔더드래곤을 잡으면 정해진 확률로 말룡도가 나온다.
     *
     * <p>바닥에 떨어뜨리지 않고 <b>우편으로 보낸다.</b> 전설 무기가 용암에 빠지거나 인벤토리가 가득 차
     * 사라지는 일을 막고, CLAUDE.md 6절대로 거래 기록도 우편 쪽에서 함께 남기기 위해서다.
     *
     * <p>받는 사람은 마지막 일격을 넣은 플레이어다. 보스가 환경 피해로 죽어 처치자가 없으면 근처
     * 플레이어 중 한 명에게 간다. 아무도 없으면 이번 판은 드랍 없이 넘어간다.
     */
    private void dropMalyongdo(RaidBoss boss, EntityDeathEvent event) {
        MalyongdoManager malyongdo = plugin.getMalyongdoManager();
        if (!"apocalypse-dragon".equals(boss.id())) {
            return;
        }
        if (ThreadLocalRandom.current().nextDouble() * 100.0 >= malyongdo.dropChancePercent()) {
            return;
        }

        Player recipient = event.getEntity().getKiller();
        if (recipient == null) {
            List<Player> nearby = new ArrayList<>();
            for (Entity nearbyEntity : event.getEntity().getWorld().getNearbyEntities(
                    event.getEntity().getLocation(), 30, 30, 30)) {
                if (nearbyEntity instanceof Player player && player.getGameMode() != GameMode.SPECTATOR) {
                    nearby.add(player);
                }
            }
            if (nearby.isEmpty()) {
                return;
            }
            recipient = nearby.get(ThreadLocalRandom.current().nextInt(nearby.size()));
        }

        ItemStack weapon = malyongdo.createItem();
        MailSpec spec = MailSpec.of(MailSenderType.QUEST, "레이드 보상", "종말룡 토벌 보상",
                "종말룡 엔더드래곤을 쓰러뜨린 증표입니다. 말룡도를 받으십시오.",
                List.of(MailSpec.Item.special("말룡도", weapon, 1, "말룡도")), 0);
        plugin.getMailManager().send(spec, recipient.getUniqueId());

        plugin.getServer().broadcastMessage(ChatColor.DARK_RED + "[레이드] " + ChatColor.GOLD
                + recipient.getName() + ChatColor.WHITE + "님이 종말룡에게서 "
                + ChatColor.RED + "말룡도" + ChatColor.WHITE + "를 얻었습니다! "
                + ChatColor.GRAY + "(우편함 확인)");
    }
}
