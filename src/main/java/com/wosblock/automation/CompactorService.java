package com.wosblock.automation;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public final class CompactorService {
    private static final Map<Material, Material> COMPACTABLES = Map.of(
        Material.IRON_INGOT, Material.IRON_BLOCK,
        Material.GOLD_INGOT, Material.GOLD_BLOCK,
        Material.REDSTONE, Material.REDSTONE_BLOCK,
        Material.LAPIS_LAZULI, Material.LAPIS_BLOCK,
        Material.DIAMOND, Material.DIAMOND_BLOCK,
        Material.EMERALD, Material.EMERALD_BLOCK,
        Material.COAL, Material.COAL_BLOCK
    );

    private final Set<UUID> enabled = ConcurrentHashMap.newKeySet();

    public boolean toggle(Player player) {
        if (!enabled.add(player.getUniqueId())) {
            enabled.remove(player.getUniqueId());
            return false;
        }
        compact(player);
        return true;
    }

    public boolean enabled(Player player) {
        return enabled.contains(player.getUniqueId());
    }

    public void compact(Player player) {
        if (!enabled(player)) {
            return;
        }
        for (Map.Entry<Material, Material> entry : COMPACTABLES.entrySet()) {
            int count = count(player, entry.getKey());
            int blocks = count / 9;
            if (blocks <= 0) {
                continue;
            }
            remove(player, entry.getKey(), blocks * 9);
            player.getInventory().addItem(new ItemStack(entry.getValue(), blocks)).values()
                .forEach(leftover -> player.getWorld().dropItemNaturally(player.getLocation(), leftover));
        }
    }

    private int count(Player player, Material material) {
        int count = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() == material) {
                count += item.getAmount();
            }
        }
        return count;
    }

    private void remove(Player player, Material material, int amount) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (amount <= 0) {
                return;
            }
            if (item == null || item.getType() != material) {
                continue;
            }
            int removed = Math.min(amount, item.getAmount());
            item.setAmount(item.getAmount() - removed);
            amount -= removed;
        }
    }
}
