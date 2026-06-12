package com.wosblock.island;

import com.wosblock.WoSBlockPlugin;
import java.util.LinkedHashMap;
import java.util.Map;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.TreeType;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public final class IslandBuilder {
    private final WoSBlockPlugin plugin;
    private final IslandService islandService;

    public IslandBuilder(WoSBlockPlugin plugin, IslandService islandService) {
        this.plugin = plugin;
        this.islandService = islandService;
    }

    public void build(IslandData island) {
        Location center = island.centerLocation();
        clearStarterVolume(center);
        buildGround(center);
        buildTree(center.clone().add(-2, 1, -2));
        buildChest(center.clone().add(2, 1, 0));
        islandService.placeGuestBook(center.clone().add(-2, 1, 0).getBlock());
    }

    private void clearStarterVolume(Location center) {
        for (int x = -7; x <= 7; x++) {
            for (int y = -6; y <= 8; y++) {
                for (int z = -7; z <= 7; z++) {
                    center.clone().add(x, y, z).getBlock().setType(Material.AIR, false);
                }
            }
        }
    }

    private void buildGround(Location center) {
        for (int x = -4; x <= 4; x++) {
            for (int z = -4; z <= 4; z++) {
                double distance = Math.sqrt((x * x) + (z * z));
                if (distance > 4.5) {
                    continue;
                }
                Material top = distance > 3.7 ? Material.DIRT : Material.GRASS_BLOCK;
                center.clone().add(x, 0, z).getBlock().setType(top, false);
                if (distance <= 3.7) {
                    center.clone().add(x, -1, z).getBlock().setType(materialForLayer(x, -1, z), false);
                }
                if (distance <= 3.1) {
                    center.clone().add(x, -2, z).getBlock().setType(materialForLayer(x, -2, z), false);
                }
                if (distance <= 2.2) {
                    center.clone().add(x, -3, z).getBlock().setType(materialForLayer(x, -3, z), false);
                }
            }
        }
        carveCave(center);
        center.clone().add(0, -4, 0).getBlock().setType(Material.BEDROCK, false);
        center.getBlock().setType(Material.GRASS_BLOCK, false);
        center.clone().add(0, 1, 0).getBlock().setType(Material.AIR, false);
        center.clone().add(0, 2, 0).getBlock().setType(Material.AIR, false);
    }

    private void buildTree(Location location) {
        location.getBlock().setType(Material.GRASS_BLOCK, false);
        location.getWorld().generateTree(location.clone().add(0, 1, 0), TreeType.TREE);
    }

    private Material materialForLayer(int x, int y, int z) {
        int hash = Math.abs((x * 31) + (y * 17) + (z * 13));
        if (y <= -2 && hash % 11 == 0) {
            return Material.COPPER_ORE;
        }
        if (y <= -1 && hash % 7 == 0) {
            return Material.COAL_ORE;
        }
        return y == -1 ? Material.DIRT : Material.STONE;
    }

    private void carveCave(Location center) {
        for (int x = -2; x <= 2; x++) {
            for (int y = -3; y <= -1; y++) {
                for (int z = 1; z <= 3; z++) {
                    double shape = (x * x * 0.7) + ((y + 2) * (y + 2)) + ((z - 2) * (z - 2) * 0.5);
                    if (shape <= 3.2) {
                        center.clone().add(x, y, z).getBlock().setType(Material.AIR, false);
                    }
                }
            }
        }
        center.clone().add(0, -2, 3).getBlock().setType(Material.AIR, false);
        center.clone().add(0, -1, 3).getBlock().setType(Material.AIR, false);
        for (int x = -1; x <= 1; x++) {
            for (int y = -2; y <= 1; y++) {
                center.clone().add(x, y, 4).getBlock().setType(Material.AIR, false);
            }
        }
        center.clone().add(0, 0, 4).getBlock().setType(Material.AIR, false);
        center.clone().add(0, 1, 4).getBlock().setType(Material.AIR, false);
        center.clone().add(-1, 0, 4).getBlock().setType(Material.STONE, false);
        center.clone().add(1, 0, 4).getBlock().setType(Material.STONE, false);
        center.clone().add(0, -3, 4).getBlock().setType(Material.STONE, false);
        center.clone().add(1, -3, 2).getBlock().setType(Material.COPPER_ORE, false);
        center.clone().add(-1, -2, 1).getBlock().setType(Material.COAL_ORE, false);
    }

    private void buildChest(Location chestLocation) {
        Block block = chestLocation.getBlock();
        block.setType(Material.CHEST, false);
        if (!(block.getState() instanceof Chest chest)) {
            return;
        }
        islandService.addStartingNpcEggs(chest);
        addStarterItems(chest.getInventory());
    }

    private void addStarterItems(Inventory inventory) {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("islands.starter-chest");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                Material material = Material.matchMaterial(key);
                if (material == null) {
                    plugin.getLogger().warning("Unknown starter item material: " + key);
                    continue;
                }
                int amount = Math.max(1, section.getInt(key));
                while (amount > 0) {
                    int stackAmount = Math.min(amount, material.getMaxStackSize());
                    inventory.addItem(new ItemStack(material, stackAmount));
                    amount -= stackAmount;
                }
            }
        }
        addFallbackStarterItems(inventory);
    }

    private void addFallbackStarterItems(Inventory inventory) {
        Map<Material, Integer> fallback = new LinkedHashMap<>();
        fallback.put(Material.WATER_BUCKET, 1);
        fallback.put(Material.LAVA_BUCKET, 1);
        fallback.put(Material.WHEAT_SEEDS, 8);
        fallback.put(Material.CARROT, 2);
        fallback.put(Material.POTATO, 2);
        for (Map.Entry<Material, Integer> entry : fallback.entrySet()) {
            if (!inventory.contains(entry.getKey())) {
                inventory.addItem(new ItemStack(entry.getKey(), entry.getValue()));
            }
        }
    }
}
