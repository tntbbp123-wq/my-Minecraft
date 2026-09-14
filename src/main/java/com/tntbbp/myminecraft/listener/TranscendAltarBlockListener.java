package com.tntbbp.myminecraft.listener;

import com.tntbbp.myminecraft.MyMinecraftPlugin;
import com.tntbbp.myminecraft.gui.TranscendAltarGUI;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;

/** <초월의 제단> 커스텀 블록(ItemDisplay)을 오른쪽 클릭하면 제단 GUI를 연다. */
public class TranscendAltarBlockListener implements Listener {

    private final MyMinecraftPlugin plugin;

    public TranscendAltarBlockListener(MyMinecraftPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInteract(PlayerInteractAtEntityEvent event) {
        if (!plugin.getTranscendAltarBlockManager().isAltarMarker(event.getRightClicked())) {
            return;
        }
        event.setCancelled(true);

        Player player = event.getPlayer();
        new TranscendAltarGUI(plugin, player).open();
    }
}
