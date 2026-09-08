package com.tntbbp.myminecraft.manager;

import com.tntbbp.myminecraft.MyMinecraftPlugin;
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
        return plugin.getConfig().getString("economy.currency-name", "포인트");
    }

    public double getBalance(UUID uuid) {
        if (!data.contains(uuid.toString())) {
            data.set(uuid.toString(), startingBalance);
            save();
        }
        return data.getDouble(uuid.toString(), startingBalance);
    }

    public boolean has(UUID uuid, double amount) {
        return getBalance(uuid) >= amount;
    }

    public void add(UUID uuid, double amount) {
        double balance = getBalance(uuid) + amount;
        data.set(uuid.toString(), balance);
        save();
    }

    public boolean subtract(UUID uuid, double amount) {
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
            data.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("economy.yml 저장 실패: " + e.getMessage());
        }
    }
}
