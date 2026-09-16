package com.tntbbp.myminecraft.listener;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketEvent;
import com.tntbbp.myminecraft.MyMinecraftPlugin;
import org.bukkit.entity.Player;

/**
 * 웅크리기 + L키(도전과제 화면 열기)를 전투모드 진입/해제 트리거로 사용한다.
 * L키를 누르면 클라이언트가 ServerboundSeenAdvancementsPacket을 보내는데, ProtocolLib에는
 * 이 패킷 전용 래퍼가 없어 원시 필드(선언 순서상 첫 번째 필드인 Action enum)를 이름으로 비교해
 * "OPENED_TAB"(도전과제 화면을 여는 순간)만 감지한다. 탭을 전환할 때도 같은 값이 다시 오므로
 * 실제 진입/해제 여부는 CombatStanceManager의 토글 쿨다운으로 걸러진다.
 * ProtocolLib이 설치되어 있을 때만 등록되며(softdepend), 이 클래스 자체는 ProtocolLib 클래스에
 * 직접 의존하므로 ProtocolLib이 없을 때는 절대 인스턴스화하면 안 된다.
 */
public class CombatStanceKeyListener extends PacketAdapter {

    private final MyMinecraftPlugin plugin;

    public CombatStanceKeyListener(MyMinecraftPlugin plugin) {
        super(plugin, ListenerPriority.NORMAL, PacketType.Play.Client.ADVANCEMENTS);
        this.plugin = plugin;
    }

    public void register() {
        ProtocolLibrary.getProtocolManager().addPacketListener(this);
    }

    public void unregister() {
        ProtocolManager manager = ProtocolLibrary.getProtocolManager();
        if (manager != null) {
            manager.removePacketListener(this);
        }
    }

    @Override
    public void onPacketReceiving(PacketEvent event) {
        Object action = event.getPacket().getModifier().read(0);
        if (action == null || !"OPENED_TAB".equals(action.toString())) {
            return;
        }

        Player player = event.getPlayer();
        // 패킷 처리는 네트워크(넷티) 스레드에서 일어나므로, 인벤토리 조작은 메인 스레드로 넘긴다.
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (player.isOnline() && player.isSneaking()) {
                plugin.getCombatStanceManager().toggle(player);
            }
        });
    }
}
