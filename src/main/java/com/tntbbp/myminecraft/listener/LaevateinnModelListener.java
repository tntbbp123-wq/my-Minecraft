package com.tntbbp.myminecraft.listener;

import com.tntbbp.myminecraft.MyMinecraftPlugin;
import kr.toxicity.model.api.BetterModel;
import kr.toxicity.model.api.bukkit.platform.BukkitAdapter;
import kr.toxicity.model.api.tracker.EntityTrackerRegistry;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * BetterModel이 설치되어 있을 때, 레바테인을 메인핸드에 들고 있는 동안
 * 커스텀 3D 검 모델을 플레이어에게 추가로 덧씌워 보여준다.
 * 플레이어 본래 외형은 그대로 유지되고 별도의 검 모델만 추가로 표시된다(/bettermodel disguise로 확인된 동작).
 * 이 클래스는 BetterModel이 활성화된 경우에만 생성/등록된다.
 */
public class LaevateinnModelListener implements Listener {

    private final MyMinecraftPlugin plugin;
    private final String modelName;
    private final Set<UUID> shown = new HashSet<>();

    public LaevateinnModelListener(MyMinecraftPlugin plugin) {
        this.plugin = plugin;
        this.modelName = plugin.getConfig().getString("laevateinn.bettermodel-name", "shadowflame");

        plugin.getServer().getScheduler().runTaskTimer(plugin, this::syncAll, 20L, 10L);
    }

    private void syncAll() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            sync(player);
        }
    }

    private void sync(Player player) {
        ItemStack mainHand = player.getInventory().getItemInMainHand();
        boolean holding = plugin.getLaevateinnManager().isLaevateinn(mainHand);
        boolean currentlyShown = shown.contains(player.getUniqueId());

        if (holding && !currentlyShown) {
            BetterModel.model(modelName).ifPresent(renderer -> {
                renderer.getOrCreate(BukkitAdapter.adapt(player));
                shown.add(player.getUniqueId());
            });
        } else if (!holding && currentlyShown) {
            hide(player.getUniqueId());
        }
    }

    private void hide(UUID uuid) {
        BetterModel.registry(uuid).ifPresent(registry -> registry.remove(modelName));
        shown.remove(uuid);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        if (shown.contains(uuid)) {
            hide(uuid);
        }
    }
}
