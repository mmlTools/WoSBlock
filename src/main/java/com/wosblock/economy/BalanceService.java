package com.wosblock.economy;

import com.wosblock.WoSBlockPlugin;
import com.wosblock.storage.Storage;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.entity.Player;

public final class BalanceService {
    private final WoSBlockPlugin plugin;
    private final Storage storage;
    private final Map<String, Double> balances = new ConcurrentHashMap<>();
    private final Set<String> loaded = ConcurrentHashMap.newKeySet();
    private final Set<String> loading = ConcurrentHashMap.newKeySet();

    public BalanceService(WoSBlockPlugin plugin, Storage storage) {
        this.plugin = plugin;
        this.storage = storage;
    }

    public double balance(Player player) {
        String key = key(player);
        balances.putIfAbsent(key, startingBalance());
        preload(player);
        return balances.get(key);
    }

    public boolean withdraw(Player player, double amount) {
        if (amount < 0 || balance(player) < amount) {
            return false;
        }
        String key = key(player);
        double updated = balance(player) - amount;
        balances.put(key, updated);
        loaded.add(key);
        storage.saveBalance(player.getUniqueId(), player.getWorld().getName(), updated)
            .exceptionally(ex -> {
                plugin.getLogger().warning("Could not save balance: " + ex.getMessage());
                return null;
            });
        return true;
    }

    public void deposit(Player player, double amount) {
        if (amount <= 0) {
            return;
        }
        String key = key(player);
        double updated = balance(player) + amount;
        balances.put(key, updated);
        loaded.add(key);
        storage.saveBalance(player.getUniqueId(), player.getWorld().getName(), updated)
            .exceptionally(ex -> {
                plugin.getLogger().warning("Could not save balance: " + ex.getMessage());
                return null;
            });
    }

    public void preload(Player player) {
        preload(player.getUniqueId(), player.getWorld().getName());
    }

    public void preload(UUID playerId, String worldName) {
        String key = key(playerId, worldName);
        balances.putIfAbsent(key, startingBalance());
        if (loaded.contains(key) || !loading.add(key)) {
            return;
        }
        storage.loadBalance(playerId, worldName, startingBalance()).whenComplete((loadedBalance, ex) -> {
            loading.remove(key);
            if (ex != null) {
                plugin.getLogger().warning("Could not load balance: " + ex.getMessage());
                return;
            }
            if (!loaded.contains(key)) {
                balances.put(key, loadedBalance);
                loaded.add(key);
            }
        });
    }

    private double startingBalance() {
        return plugin.getConfig().getDouble("economy.starting-balance", 1000.0);
    }

    private String key(Player player) {
        return key(player.getUniqueId(), player.getWorld().getName());
    }

    private String key(UUID playerId, String worldName) {
        return playerId + ":" + worldName;
    }
}
