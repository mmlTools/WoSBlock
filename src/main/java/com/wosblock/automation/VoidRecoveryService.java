package com.wosblock.automation;

import com.wosblock.WoSBlockPlugin;
import com.wosblock.island.IslandData;
import com.wosblock.island.IslandService;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.inventory.ItemStack;

public final class VoidRecoveryService {
    private final WoSBlockPlugin plugin;
    private final IslandService islandService;
    private final Map<UUID, List<ItemStack>> cache = new ConcurrentHashMap<>();
    private int taskId = -1;

    public VoidRecoveryService(WoSBlockPlugin plugin, IslandService islandService) {
        this.plugin = plugin;
        this.islandService = islandService;
    }

    public void start() {
        stop();
        taskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, this::scan, 20L, 20L);
    }

    public void stop() {
        if (taskId != -1) {
            Bukkit.getScheduler().cancelTask(taskId);
            taskId = -1;
        }
    }

    public int cachedStacks(IslandData island) {
        return cache.getOrDefault(island.ownerId(), List.of()).size();
    }

    public void clear(UUID ownerId) {
        cache.remove(ownerId);
    }

    private void scan() {
        for (World world : Bukkit.getWorlds()) {
            int threshold = world.getMinHeight() + 8;
            for (Entity entity : world.getEntitiesByClass(Item.class)) {
                if (entity.getLocation().getY() > threshold) {
                    continue;
                }
                IslandData island = islandService.islandAt(entity.getLocation()).orElse(null);
                if (island == null) {
                    continue;
                }
                List<ItemStack> stacks = cache.computeIfAbsent(island.ownerId(), ignored -> new ArrayList<>());
                if (stacks.size() >= 27) {
                    continue;
                }
                stacks.add(((Item) entity).getItemStack().clone());
                entity.remove();
            }
        }
    }
}
