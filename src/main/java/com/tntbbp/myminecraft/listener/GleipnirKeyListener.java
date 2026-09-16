package com.tntbbp.myminecraft.listener;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketEvent;
import com.tntbbp.myminecraft.MyMinecraftPlugin;
import com.tntbbp.myminecraft.manager.GleipnirManager;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * L키(도전과제 화면 열기)를 글레이프니르의 '절대봉인' 발동 키로 쓴다.
 *
 * <p>전투모드는 <b>웅크리기 + L</b>이므로, 여기서는 웅크리지 않은 상태의 L만 처리해 충돌을 피한다.
 * ProtocolLib이 설치되어 있을 때만 등록되며, 이 클래스는 ProtocolLib 클래스에 직접 의존하므로
 * ProtocolLib이 없을 때는 절대 인스턴스화하면 안 된다.
 */
public class GleipnirKeyListener extends PacketAdapter {

    private final MyMinecraftPlugin plugin;

    public GleipnirKeyListener(MyMinecraftPlugin plugin) {
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
        // 패킷은 네트워크 스레드에서 오므로 실제 처리는 메인 스레드로 넘긴다.
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!player.isOnline() || player.isSneaking()) {
                return;
            }
            GleipnirManager manager = plugin.getGleipnirManager();
            ItemStack hand = player.getInventory().getItemInMainHand();
            if (!manager.isGleipnir(hand)) {
                return;
            }
            if (!manager.absoluteSealEnabled()) {
                player.sendMessage(ChatColor.GRAY + "절대봉인은 이 서버에서 비활성화되어 있습니다.");
                return;
            }
            if (manager.isSealed(player.getUniqueId())) {
                player.sendMessage(ChatColor.GRAY + "봉인되어 스킬을 쓸 수 없습니다.");
                return;
            }

            Player target = manager.useAbsoluteSeal(player);
            if (target == null) {
                return;
            }

            // 성공했을 때만 아이템을 소모한다.
            if (hand.getAmount() <= 1) {
                player.getInventory().setItemInMainHand(null);
            } else {
                hand.setAmount(hand.getAmount() - 1);
                player.getInventory().setItemInMainHand(hand);
            }
            player.sendMessage(ChatColor.DARK_RED + "절대봉인을 발동했습니다. 글레이프니르가 사라졌습니다.");
        });
    }
}
