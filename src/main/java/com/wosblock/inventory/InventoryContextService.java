package com.wosblock.inventory;

import com.wosblock.WoSBlockPlugin;
import java.io.File;
import java.io.IOException;
import java.util.UUID;
import org.bukkit.GameMode;
import org.bukkit.attribute.Attribute;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

public final class InventoryContextService {
    private final WoSBlockPlugin plugin;
    private final File folder;

    public InventoryContextService(WoSBlockPlugin plugin) {
        this.plugin = plugin;
        this.folder = new File(plugin.getDataFolder(), "world-inventories");
        if (!folder.exists() && !folder.mkdirs()) {
            plugin.getLogger().warning("Could not create world inventory folder.");
        }
    }

    public void switchContext(Player player, String fromWorld, String toWorld) {
        if (player.getGameMode() == GameMode.CREATIVE) {
            return;
        }
        save(player, fromWorld);
        load(player, toWorld);
    }

    public void save(Player player) {
        if (player.getGameMode() != GameMode.CREATIVE) {
            save(player, player.getWorld().getName());
        }
    }

    public void load(Player player) {
        if (player.getGameMode() != GameMode.CREATIVE) {
            load(player, player.getWorld().getName());
        }
    }

    private void save(Player player, String worldName) {
        UUID uuid = player.getUniqueId();
        ItemStack[] contents = player.getInventory().getContents();
        ItemStack[] armor = player.getInventory().getArmorContents();
        ItemStack offhand = player.getInventory().getItemInOffHand();
        int level = player.getLevel();
        float exp = player.getExp();
        int food = player.getFoodLevel();
        double health = player.getHealth();

        new BukkitRunnable() {
            @Override
            public void run() {
                File file = file(uuid, worldName);
                YamlConfiguration yaml = new YamlConfiguration();
                yaml.set("inventory", contents);
                yaml.set("armor", armor);
                yaml.set("offhand", offhand);
                yaml.set("level", level);
                yaml.set("exp", exp);
                yaml.set("food", food);
                yaml.set("health", health);
                try {
                    yaml.save(file);
                } catch (IOException ex) {
                    plugin.getLogger().warning("Could not save world inventory for " + uuid + ": " + ex.getMessage());
                }
            }
        }.runTaskAsynchronously(plugin);
    }

    private void load(Player player, String worldName) {
        File file = file(player.getUniqueId(), worldName);
        player.getInventory().clear();
        player.getInventory().setArmorContents(null);
        player.getInventory().setItemInOffHand(null);

        if (!file.exists()) {
            player.setLevel(0);
            player.setExp(0);
            player.setFoodLevel(20);
            player.setHealth(maxHealth(player));
            return;
        }

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        player.getInventory().setContents(readItems(yaml, "inventory", player.getInventory().getSize()));
        player.getInventory().setArmorContents(readItems(yaml, "armor", 4));
        ItemStack offhand = yaml.getItemStack("offhand");
        player.getInventory().setItemInOffHand(offhand);
        player.setLevel(yaml.getInt("level", 0));
        player.setExp((float) yaml.getDouble("exp", 0));
        player.setFoodLevel(yaml.getInt("food", 20));
        double maxHealth = maxHealth(player);
        player.setHealth(Math.min(maxHealth, Math.max(1.0, yaml.getDouble("health", maxHealth))));
    }

    private ItemStack[] readItems(YamlConfiguration yaml, String path, int size) {
        ItemStack[] items = new ItemStack[size];
        java.util.List<?> list = yaml.getList(path);
        if (list == null) {
            return items;
        }
        for (int i = 0; i < Math.min(size, list.size()); i++) {
            Object value = list.get(i);
            if (value instanceof ItemStack item) {
                items[i] = item;
            }
        }
        return items;
    }

    private File file(UUID uuid, String worldName) {
        return new File(folder, uuid + "_" + worldName + ".yml");
    }

    private double maxHealth(Player player) {
        org.bukkit.attribute.AttributeInstance attribute = player.getAttribute(Attribute.MAX_HEALTH);
        return attribute == null ? 20.0 : attribute.getValue();
    }
}
