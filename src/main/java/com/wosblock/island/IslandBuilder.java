package com.wosblock.island;

import com.wosblock.WoSBlockPlugin;
import com.wosblock.util.Text;
import java.util.LinkedHashMap;
import java.util.Map;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Leaves;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

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
        buildTree(center.clone().add(-3, 1, -2));
        buildChest(center.clone().add(3, 1, 2));
    }

    // ─── Volume clear ──────────────────────────────────────────────────────────

    private void clearStarterVolume(Location center) {
        for (int x = -10; x <= 10; x++) {
            for (int y = -7; y <= 12; y++) {
                for (int z = -10; z <= 10; z++) {
                    center.clone().add(x, y, z).getBlock().setType(Material.AIR, false);
                }
            }
        }
    }

    // ─── Ground ───────────────────────────────────────────────────────────────

    private void buildGround(Location center) {
        for (int x = -7; x <= 7; x++) {
            for (int z = -7; z <= 7; z++) {
                double dist = Math.sqrt((x * x) + (z * z));
                if (dist > 7.4)
                    continue;

                // Surface layer
                Material top;
                if (dist > 6.5) {
                    top = Material.DIRT;
                } else if (dist > 5.5) {
                    top = Material.COARSE_DIRT;
                } else {
                    top = Material.GRASS_BLOCK;
                }
                center.clone().add(x, 0, z).getBlock().setType(top, false);

                // Sub-layers
                if (dist <= 6.5) {
                    center.clone().add(x, -1, z).getBlock().setType(materialForLayer(x, -1, z), false);
                }
                if (dist <= 5.5) {
                    center.clone().add(x, -2, z).getBlock().setType(materialForLayer(x, -2, z), false);
                }
                if (dist <= 4.2) {
                    center.clone().add(x, -3, z).getBlock().setType(materialForLayer(x, -3, z), false);
                }
                if (dist <= 3.0) {
                    center.clone().add(x, -4, z).getBlock().setType(Material.STONE, false);
                }
            }
        }
        center.clone().add(0, -5, 0).getBlock().setType(Material.BEDROCK, false);
        // Make sure spawn point is clear grass
        center.getBlock().setType(Material.GRASS_BLOCK, false);
        center.clone().add(0, 1, 0).getBlock().setType(Material.AIR, false);
        center.clone().add(0, 2, 0).getBlock().setType(Material.AIR, false);
    }

    private Material materialForLayer(int x, int y, int z) {
        int hash = Math.abs((x * 31) + (y * 17) + (z * 13));
        if (y <= -3 && hash % 9 == 0)
            return Material.IRON_ORE;
        if (y <= -2 && hash % 11 == 0)
            return Material.COPPER_ORE;
        if (y <= -1 && hash % 7 == 0)
            return Material.COAL_ORE;
        return y == -1 ? Material.DIRT : Material.STONE;
    }

    // ─── Tree ─────────────────────────────────────────────────────────────────

    private void buildTree(Location base) {
        base.getBlock().setType(Material.PODZOL, false);
        // Trunk — 6 tall for a proper RPG oak
        for (int y = 1; y <= 6; y++) {
            base.clone().add(0, y, 0).getBlock().setType(Material.OAK_LOG, false);
        }
        // Canopy — layered sphere
        for (int x = -3; x <= 3; x++) {
            for (int y = 3; y <= 8; y++) {
                for (int z = -3; z <= 3; z++) {
                    double dist = Math.sqrt((x * x) + ((y - 6) * (y - 6) * 1.2) + (z * z));
                    if (dist <= 3.2 && !(x == 0 && z == 0 && y <= 6)) {
                        setLeaf(base.clone().add(x, y, z).getBlock());
                    }
                }
            }
        }
        // Moss on the base
        base.clone().add(1, 1, 0).getBlock().setType(Material.MOSS_BLOCK, false);
        base.clone().add(-1, 1, 0).getBlock().setType(Material.MOSS_BLOCK, false);
    }

    // ─── Chest ────────────────────────────────────────────────────────────────

    private void buildChest(Location chestLocation) {
        Block block = chestLocation.getBlock();
        block.setType(Material.CHEST, false);
        if (!(block.getState() instanceof Chest chest))
            return;
        islandService.addStartingNpcEggs(chest);
        addGuestBook(chest.getInventory());
        addStarterItems(chest.getInventory());
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private void set(Location center, int x, int y, int z, Material material) {
        center.clone().add(x, y, z).getBlock().setType(material, false);
    }

    private void setLeaf(Block block) {
        block.setType(Material.OAK_LEAVES, false);
        BlockData data = block.getBlockData();
        if (data instanceof Leaves leaves) {
            leaves.setPersistent(true);
            block.setBlockData(leaves, false);
        }
    }

    // ─── Starter items ────────────────────────────────────────────────────────

    private void addGuestBook(Inventory inventory) {
        ItemStack guestBook = new ItemStack(Material.ENCHANTING_TABLE);
        ItemMeta meta = guestBook.getItemMeta();
        meta.displayName(Text.plain("Island Guest Book"));
        meta.lore(Text.lore("&7Place this on your island.", "&7Right-click it to view island info."));
        guestBook.setItemMeta(meta);
        inventory.addItem(guestBook);
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
        fallback.put(Material.WHEAT_SEEDS, 16);
        fallback.put(Material.CARROT, 4);
        fallback.put(Material.POTATO, 4);
        fallback.put(Material.OAK_SAPLING, 2);
        fallback.put(Material.BONE_MEAL, 8);
        fallback.put(Material.TORCH, 8);
        for (Map.Entry<Material, Integer> entry : fallback.entrySet()) {
            if (!inventory.contains(entry.getKey())) {
                inventory.addItem(new ItemStack(entry.getKey(), entry.getValue()));
            }
        }
    }
}
