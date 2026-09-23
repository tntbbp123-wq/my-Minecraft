package com.tntbbp.myminecraft.manager.economy;

import com.tntbbp.myminecraft.MyMinecraftPlugin;
import com.tntbbp.myminecraft.util.AtomicYaml;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

/** 외부 Vault 없이 동작하는 내부 포인트 경제 시스템. */
public class EconomyManager {

    private final MyMinecraftPlugin plugin;
    private final File file;
    private final YamlConfiguration data;
    private final double startingBalance;

    public EconomyManager(MyMinecraftPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "economy.yml");
        this.startingBalance = plugin.getConfig().getDouble("economy.starting-balance", 1000.0);
        if (!file.exists()) {
            plugin.getDataFolder().mkdirs();
            try {
                file.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("economy.yml 생성 실패: " + e.getMessage());
            }
        }
        this.data = YamlConfiguration.loadConfiguration(file);
    }

    public String currencyName() {
        return plugin.getConfig().getString("economy.currency-name", "G");
    }

    public double getBalance(UUID uuid) {
        if (!data.contains(uuid.toString())) {
            data.set(uuid.toString(), startingBalance);
            save();
        }
        return data.getDouble(uuid.toString(), startingBalance);
    }

    /**
     * 잔액을 조회만 한다. {@link #getBalance}와 달리 계좌가 없어도 만들거나 저장하지 않고 시작 잔액 값을 돌려준다
     * (웹 관리자 프로필 조회처럼 기록을 남기면 안 되는 곳에서 사용).
     */
    public double peekBalance(UUID uuid) {
        return data.getDouble(uuid.toString(), startingBalance);
    }

    public boolean has(UUID uuid, double amount) {
        return getBalance(uuid) >= amount;
    }

    public void add(UUID uuid, double amount) {
        // 음수를 더하면 사실상 빼기가 되고, NaN이면 잔액이 NaN으로 망가진다. 설정 실수나 계산 오류가
        // 돈을 없애거나 계좌를 망가뜨리지 않도록 여기서 막는다.
        if (!Double.isFinite(amount) || amount < 0) {
            plugin.getLogger().warning("잘못된 입금액을 무시했습니다: " + amount + " (" + uuid + ")");
            return;
        }
        double balance = getBalance(uuid) + amount;
        data.set(uuid.toString(), balance);
        save();
    }

    public boolean subtract(UUID uuid, double amount) {
        // 음수를 빼면 오히려 돈이 생긴다. 예를 들어 상점 가격을 설정에서 실수로 음수로 적으면 그 물건을
        // 살 때마다 돈이 늘어났다. 모든 결제가 여기를 지나가므로 한 곳에서 막는다.
        if (!Double.isFinite(amount) || amount < 0) {
            plugin.getLogger().warning("잘못된 출금액을 거부했습니다: " + amount + " (" + uuid + ")");
            return false;
        }
        double balance = getBalance(uuid);
        if (balance < amount) {
            return false;
        }
        data.set(uuid.toString(), balance - amount);
        save();
        return true;
    }

    public void save() {
        try {
            AtomicYaml.save(data, file);
        } catch (IOException e) {
            plugin.getLogger().severe("economy.yml 저장 실패: " + e.getMessage());
        }
    }
}
