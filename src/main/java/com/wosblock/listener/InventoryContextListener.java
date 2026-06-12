package com.wosblock.listener;

import com.wosblock.inventory.InventoryContextService;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class InventoryContextListener implements Listener {
    private final InventoryContextService inventoryContextService;

    public InventoryContextListener(InventoryContextService inventoryContextService) {
        this.inventoryContextService = inventoryContextService;
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        inventoryContextService.switchContext(
            event.getPlayer(),
            event.getFrom().getName(),
            event.getPlayer().getWorld().getName()
        );
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        inventoryContextService.load(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        inventoryContextService.save(event.getPlayer());
    }
}
