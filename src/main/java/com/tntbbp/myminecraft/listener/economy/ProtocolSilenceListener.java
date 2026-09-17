package com.tntbbp.myminecraft.listener.economy;

import com.tntbbp.myminecraft.MyMinecraftPlugin;
import com.tntbbp.myminecraft.manager.economy.BlackMarketManager;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.packettype.PacketTypeCommon;
import com.github.retrooper.packetevents.util.Vector3i;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerBlockAction;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEffect;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/**
 * 소음 차단 포션 효과 중인 플레이어 근처에서 발생한 블록 파괴/상자 여는 소리·애니메이션 패킷을
 * 다른 플레이어에게 전송하지 않도록 가로챈다.
 *
 * <p>PacketEvents는 jar에 함께 포함되므로 별도 설치 없이 항상 동작한다.
 */
public class ProtocolSilenceListener extends PacketListenerAbstract {

    private static final double SUPPRESS_RADIUS_SQUARED = 12.0 * 12.0;

    private final MyMinecraftPlugin plugin;

    public ProtocolSilenceListener(MyMinecraftPlugin plugin) {
        super(PacketListenerPriority.NORMAL);
        this.plugin = plugin;
    }

    @Override
    public void onPacketSend(PacketSendEvent event) {
        PacketTypeCommon type = event.getPacketType();

        Vector3i position;
        if (type == PacketType.Play.Server.EFFECT) {
            position = new WrapperPlayServerEffect(event).getPosition();
        } else if (type == PacketType.Play.Server.BLOCK_ACTION) {
            position = new WrapperPlayServerBlockAction(event).getBlockPosition();
        } else {
            return;
        }
        if (position == null) {
            return;
        }

        Player receiver = event.getPlayer();
        if (receiver == null) {
            return;
        }

        BlackMarketManager blackMarketManager = plugin.getBlackMarketManager();
        Location eventLocation = new Location(receiver.getWorld(), position.getX(), position.getY(), position.getZ());

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
