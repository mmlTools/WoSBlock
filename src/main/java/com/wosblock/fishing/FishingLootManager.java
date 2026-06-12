package com.wosblock.fishing;

import com.wosblock.WoSBlockPlugin;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.inventory.ItemStack;

public final class FishingLootManager {
    private final Material[] tierTable;
    private final List<Material> commonFallback = List.of(Material.COD);

    public FishingLootManager(WoSBlockPlugin plugin) {
        FileConfiguration config = plugin.extraConfig("fishing.yml");
        ConfigurationSection tiers = config.getConfigurationSection("fishing.loot");
        if (tiers == null) {
            tierTable = new Material[] { Material.COD };
            return;
        }
        List<Material> weighted = new ArrayList<>();
        for (String tier : tiers.getKeys(false)) {
            int tierWeight = Math.max(0, tiers.getInt(tier + ".weight"));
            Material material = rollConfiguredItem(tiers.getConfigurationSection(tier + ".items"));
            for (int i = 0; i < tierWeight; i++) {
                weighted.add(material);
            }
        }
        tierTable = weighted.isEmpty() ? new Material[] { Material.COD } : weighted.toArray(Material[]::new);
    }

    public ItemStack rollCatch() {
        Material material = tierTable[ThreadLocalRandom.current().nextInt(tierTable.length)];
        return new ItemStack(material);
    }

    private Material rollConfiguredItem(ConfigurationSection items) {
        if (items == null) {
            return commonFallback.getFirst();
        }
        List<Material> weighted = new ArrayList<>();
        for (String materialName : items.getKeys(false)) {
            Material material = Material.matchMaterial(materialName);
            if (material == null) {
                continue;
            }
            int weight = Math.max(0, items.getInt(materialName));
            for (int i = 0; i < weight; i++) {
                weighted.add(material);
            }
        }
        if (weighted.isEmpty()) {
            return commonFallback.getFirst();
        }
        return weighted.get(ThreadLocalRandom.current().nextInt(weighted.size()));
    }
}
