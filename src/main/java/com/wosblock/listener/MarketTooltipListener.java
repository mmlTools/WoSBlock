package com.wosblock.listener;

import com.wosblock.WoSBlockPlugin;
import com.wosblock.island.IslandService;
import com.wosblock.market.MarketTooltipService;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class MarketTooltipListener implements Listener {
    private final WoSBlockPlugin plugin;
    private final IslandService islandService;
    private final MarketTooltipService tooltipService;

    public MarketTooltipListener(WoSBlockPlugin plugin, IslandService islandService, MarketTooltipService tooltipService) {
        this.plugin = plugin;
        this.islandService = islandService;
        this.tooltipService = tooltipService;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        refreshSoon(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onQuit(PlayerQuitEvent event) {
        tooltipService.remove(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        tooltipService.remove(event.getPlayer());
        refreshSoon(event.getPlayer());
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (event.getFrom().getBlockX() == event.getTo().getBlockX()
            && event.getFrom().getBlockY() == event.getTo().getBlockY()
            && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) {
            return;
        }
        refreshSoon(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            refreshSoon(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            refreshSoon(player);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player player) {
            refreshSoon(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player) {
            refreshSoon(player);
        }
    }

    private void refreshSoon(Player player) {
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            if (islandService.islandAt(player.getLocation()).isPresent()) {
                tooltipService.apply(player);
            } else {
                tooltipService.remove(player);
            }
        }, 1L);
    }
}
