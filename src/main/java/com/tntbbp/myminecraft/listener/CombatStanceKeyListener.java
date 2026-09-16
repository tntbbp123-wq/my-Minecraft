package com.tntbbp.myminecraft.listener;

import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientAdvancementTab;
import com.tntbbp.myminecraft.MyMinecraftPlugin;
import org.bukkit.entity.Player;

/**
 * 웅크리기 + L키(도전과제 화면 열기)를 전투모드 진입/해제 트리거로 사용한다.
 *
 * <p>L키를 누르면 클라이언트가 도전과제 탭 패킷을 보내는데, 그중 "탭을 여는 순간"만 잡는다.
 * 탭을 전환할 때도 같은 값이 다시 오므로 실제 진입/해제 여부는 CombatStanceManager의 토글
 * 쿨다운으로 걸러진다.
 */
public class CombatStanceKeyListener extends PacketListenerAbstract {

    private final MyMinecraftPlugin plugin;

    public CombatStanceKeyListener(MyMinecraftPlugin plugin) {
        super(PacketListenerPriority.NORMAL);
        this.plugin = plugin;
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (event.getPacketType() != PacketType.Play.Client.ADVANCEMENT_TAB) {
            return;
        }
        if (new WrapperPlayClientAdvancementTab(event).getAction()
                != WrapperPlayClientAdvancementTab.Action.OPENED_TAB) {
            return;
        }

        Player player = event.getPlayer();
        if (player == null) {
            return;
        }

        // 패킷 처리는 네트워크 스레드에서 일어나므로, 인벤토리 조작은 메인 스레드로 넘긴다.
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (player.isOnline() && player.isSneaking()) {
                plugin.getCombatStanceManager().toggle(player);
            }
        });
    }
}
