package com.wosblock.listener;

import com.wosblock.automation.CompactorService;
import com.wosblock.automation.HopperFilterService;
import com.wosblock.island.IslandData;
import com.wosblock.island.IslandService;
import com.wosblock.util.Text;
import java.util.Comparator;
import java.util.List;
import org.bukkit.Material;
import org.bukkit.block.Chest;
import org.bukkit.block.Hopper;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public final class AutomationListener implements Listener {
    private final CompactorService compactorService;
    private final HopperFilterService hopperFilterService;
    private final IslandService islandService;

    public AutomationListener(CompactorService compactorService, HopperFilterService hopperFilterService, IslandService islandService) {
        this.compactorService = compactorService;
        this.hopperFilterService = hopperFilterService;
        this.islandService = islandService;
    }

    @EventHandler(ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player) {
            org.bukkit.Bukkit.getScheduler().runTaskLater(
                org.bukkit.plugin.java.JavaPlugin.getProvidingPlugin(getClass()),
                () -> compactorService.compact(player),
                1L
            );
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onShiftInteract(PlayerInteractEvent event) {
        if (!event.getPlayer().isSneaking() || event.getClickedBlock() == null) {
            return;
        }
        IslandData island = islandService.islandAt(event.getClickedBlock().getLocation()).orElse(null);
        if (island != null && !islandService.isOwnerOrTrusted(event.getPlayer(), island)) {
            return;
        }
        if (event.getClickedBlock().getState() instanceof Hopper) {
            hopperFilterService.open(event.getPlayer(), event.getClickedBlock().getLocation());
            event.setCancelled(true);
            return;
        }
        if (event.getClickedBlock().getState() instanceof Chest chest) {
            sort(chest.getInventory());
            event.getPlayer().sendMessage("Chest sorted.");
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onFilterClose(InventoryCloseEvent event) {
        if (Text.plainText(event.getView().title()).equals("Hopper Filter") && event.getPlayer() instanceof Player player) {
            hopperFilterService.save(player, event.getInventory());
        }
    }

    private void sort(Inventory inventory) {
        List<ItemStack> sorted = java.util.Arrays.stream(inventory.getContents())
            .filter(item -> item != null && item.getType() != Material.AIR)
            .sorted(Comparator.comparing(item -> item.getType().name()))
            .toList();
        inventory.clear();
        sorted.forEach(inventory::addItem);
    }
}
