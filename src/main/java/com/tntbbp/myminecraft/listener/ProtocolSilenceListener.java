package com.tntbbp.myminecraft.listener;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.BlockPosition;
import com.tntbbp.myminecraft.MyMinecraftPlugin;
import com.tntbbp.myminecraft.manager.BlackMarketManager;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.Optional;

/**
 * ProtocolLib으로 소음 차단 포션 효과 중인 플레이어 근처의 블록 파괴/상자 여는
 * 소리·애니메이션 패킷을 다른 플레이어에게는 전송하지 않도록 가로챈다.
 * ProtocolLib이 설치되어 있을 때만 등록되며(softdepend), 이 클래스 자체는 ProtocolLib
 * 클래스에 직접 의존하므로 ProtocolLib이 없을 때는 절대 인스턴스화하면 안 된다.
 */
public class ProtocolSilenceListener extends PacketAdapter {

    private static final double SUPPRESS_RADIUS_SQUARED = 12.0 * 12.0;

    private final MyMinecraftPlugin plugin;

    public ProtocolSilenceListener(MyMinecraftPlugin plugin) {
        super(plugin, ListenerPriority.NORMAL, PacketType.Play.Server.WORLD_EVENT, PacketType.Play.Server.BLOCK_ACTION);
        this.plugin = plugin;
    }

    public void register() {
        ProtocolLibrary.getProtocolManager().addPacketListener(this);
    }

    public void unregister() {
        ProtocolManager manager = ProtocolLibrary.getProtocolManager();
        if (manager != null) {
            manager.removePacketListeners(plugin);
        }
    }

    @Override
    public void onPacketSending(PacketEvent event) {
        Player receiver = event.getPlayer();
        BlackMarketManager blackMarketManager = plugin.getBlackMarketManager();

        Optional<BlockPosition> position = event.getPacket().getBlockPositionModifier().optionRead(0);
        if (position.isEmpty()) {
            return;
        }
        Location eventLocation = position.get().toLocation(receiver.getWorld());

        for (Player silencedPlayer : plugin.getServer().getOnlinePlayers()) {
            if (silencedPlayer.equals(receiver) || !blackMarketManager.isSilenced(silencedPlayer.getUniqueId())) {
                continue;
            }
            if (!silencedPlayer.getWorld().equals(receiver.getWorld())) {
                continue;
            }
            if (silencedPlayer.getLocation().distanceSquared(eventLocation) <= SUPPRESS_RADIUS_SQUARED) {
                event.setCancelled(true);
                return;
            }
        }
    }
}
