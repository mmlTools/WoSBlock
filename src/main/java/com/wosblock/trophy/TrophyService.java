package com.wosblock.trophy;

import com.wosblock.WoSBlockPlugin;
import com.wosblock.util.Text;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

public final class TrophyService {
    private final WoSBlockPlugin plugin;
    private final NamespacedKey tierKey;

    public TrophyService(WoSBlockPlugin plugin) {
        this.plugin = plugin;
        this.tierKey = new NamespacedKey(plugin, "trophy_tier");
    }

    public ItemStack createTrophy(String tier) {
        String normalized = tier.toUpperCase();
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Text.legacy("&6" + normalized + " Trophy"));
        meta.lore(Text.lore("&7Place on your island to increase Achievement Level."));
        meta.getPersistentDataContainer().set(tierKey, PersistentDataType.STRING, normalized);
        item.setItemMeta(meta);
        return item;
    }

    public String readTier(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }
        return item.getItemMeta().getPersistentDataContainer().get(tierKey, PersistentDataType.STRING);
    }

    public int value(String tier) {
        return plugin.getConfig().getInt("achievements.tier-values." + tier, 0);
    }
}
