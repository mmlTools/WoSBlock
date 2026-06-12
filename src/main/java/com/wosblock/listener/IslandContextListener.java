package com.wosblock.listener;

import com.wosblock.island.IslandService;
import org.bukkit.GameMode;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerMoveEvent;

public final class IslandContextListener implements Listener {
    private final IslandService islandService;

    public IslandContextListener(IslandService islandService) {
        this.islandService = islandService;
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        enforceIslandContext(event.getPlayer());
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (event.getFrom().getBlockX() == event.getTo().getBlockX()
            && event.getFrom().getBlockY() == event.getTo().getBlockY()
            && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) {
            return;
        }
        enforceIslandContext(event.getPlayer());
    }

    private void enforceIslandContext(org.bukkit.entity.Player player) {
        if (player.getGameMode() == GameMode.CREATIVE) {
            return;
        }
        if (islandService.islandAt(player.getLocation()).isEmpty()) {
            player.setAllowFlight(false);
            player.setFlying(false);
        }
    }
}
