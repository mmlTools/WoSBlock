package com.wosblock.listener;

import com.wosblock.island.IslandData;
import com.wosblock.island.IslandService;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

public final class IslandRespawnListener implements Listener {
    private final IslandService islandService;
    private final Map<UUID, IslandData> deathIslands = new ConcurrentHashMap<>();

    public IslandRespawnListener(IslandService islandService) {
        this.islandService = islandService;
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        UUID playerId = event.getEntity().getUniqueId();
        islandService.islandAt(event.getEntity().getLocation()).ifPresentOrElse(
            island -> deathIslands.put(playerId, island),
            () -> deathIslands.remove(playerId)
        );
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        IslandData deathIsland = deathIslands.remove(playerId);
        if (deathIsland == null) {
            return;
        }

        IslandData respawnIsland = islandService.cachedIsland(playerId).orElse(deathIsland);
        event.setRespawnLocation(respawnIsland.spawnLocation());
    }
}
