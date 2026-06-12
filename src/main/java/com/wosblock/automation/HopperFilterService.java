package com.wosblock.automation;

import com.wosblock.util.Text;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public final class HopperFilterService {
    private final Map<Location, ItemStack[]> filters = new HashMap<>();

    public void open(Player player, Location location) {
        Inventory inventory = Bukkit.createInventory(null, 9, Text.plain("Hopper Filter"));
        ItemStack[] contents = filters.get(location);
        if (contents != null) {
            inventory.setContents(contents);
        }
        player.openInventory(inventory);
        player.setMetadata("wos_filter_location", new org.bukkit.metadata.FixedMetadataValue(
            org.bukkit.plugin.java.JavaPlugin.getProvidingPlugin(getClass()), serialize(location)
        ));
    }

    public void save(Player player, Inventory inventory) {
        if (!player.hasMetadata("wos_filter_location")) {
            return;
        }
        Location location = deserialize(player.getMetadata("wos_filter_location").getFirst().asString());
        filters.put(location, Arrays.copyOf(inventory.getContents(), inventory.getSize()));
        player.removeMetadata("wos_filter_location", org.bukkit.plugin.java.JavaPlugin.getProvidingPlugin(getClass()));
    }

    private String serialize(Location location) {
        return location.getWorld().getName() + ";" + location.getBlockX() + ";" + location.getBlockY() + ";" + location.getBlockZ();
    }

    private Location deserialize(String value) {
        String[] parts = value.split(";");
        return new Location(Bukkit.getWorld(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]), Integer.parseInt(parts[3]));
    }
}
