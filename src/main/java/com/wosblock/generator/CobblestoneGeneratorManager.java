package com.wosblock.generator;

import com.wosblock.WoSBlockPlugin;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.ConfigurationSection;

public final class CobblestoneGeneratorManager {
    private final Map<Integer, Material[]> cachedDrops = new HashMap<>();
    private final Map<Integer, Long> requiredXp = new HashMap<>();
    private final Map<Material, Integer> miningXp = new HashMap<>();
    private final Material[] fallback = new Material[] { Material.COBBLESTONE };

    public CobblestoneGeneratorManager(WoSBlockPlugin plugin) {
        FileConfiguration config = plugin.extraConfig("generator.yml");
        ConfigurationSection xpSection = config.getConfigurationSection("cobblestone-generator.mining-xp");
        if (xpSection != null) {
            for (String materialName : xpSection.getKeys(false)) {
                Material material = Material.matchMaterial(materialName);
                if (material != null) {
                    miningXp.put(material, Math.max(0, xpSection.getInt(materialName)));
                }
            }
        }
        miningXp.putIfAbsent(Material.COBBLESTONE, 1);

        ConfigurationSection levels = config.getConfigurationSection("cobblestone-generator.levels");
        if (levels == null) {
            cachedDrops.put(1, fallback);
            return;
        }
        for (String levelKey : levels.getKeys(false)) {
            ConfigurationSection drops = levels.getConfigurationSection(levelKey + ".drops");
            if (drops == null) {
                continue;
            }
            int level = Integer.parseInt(levelKey);
            cachedDrops.put(level, buildHundredSlotTable(drops));
            requiredXp.put(level, levels.getLong(levelKey + ".required-xp", 0));
        }
        cachedDrops.putIfAbsent(1, fallback);
        requiredXp.putIfAbsent(1, 0L);
    }

    public Material rollMaterial(int level) {
        Material[] table = cachedDrops.getOrDefault(level, cachedDrops.getOrDefault(1, fallback));
        return table[ThreadLocalRandom.current().nextInt(table.length)];
    }

    public int levelForXp(long xp) {
        int best = 1;
        for (Map.Entry<Integer, Long> entry : requiredXp.entrySet()) {
            if (xp >= entry.getValue() && entry.getKey() > best) {
                best = entry.getKey();
            }
        }
        return best;
    }

    public int miningXp(Material material) {
        return miningXp.getOrDefault(material, material.name().endsWith("_ORE") ? 2 : 0);
    }

    private Material[] buildHundredSlotTable(ConfigurationSection drops) {
        Material[] table = new Material[100];
        int cursor = 0;
        Material lastMaterial = Material.COBBLESTONE;
        for (String materialName : drops.getKeys(false)) {
            Material material = Material.matchMaterial(materialName);
            if (material == null) {
                continue;
            }
            lastMaterial = material;
            int weight = Math.max(0, drops.getInt(materialName));
            for (int i = 0; i < weight && cursor < table.length; i++) {
                table[cursor++] = material;
            }
        }
        while (cursor < table.length) {
            table[cursor++] = lastMaterial;
        }
        return table;
    }
}
