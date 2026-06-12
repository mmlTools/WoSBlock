package com.wosblock.listener;

import com.wosblock.island.IslandData;
import com.wosblock.island.IslandService;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

public final class IslandProtectionListener implements Listener {
    private final IslandService islandService;

    public IslandProtectionListener(IslandService islandService) {
        this.islandService = islandService;
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        IslandData island = islandService.islandAt(event.getBlock().getLocation()).orElse(null);
        if (island == null || islandService.isOwnerOrTrusted(event.getPlayer(), island)) {
            return;
        }
        if (!island.visitMode().blockBreakingAllowed()) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("This island does not allow visitor block breaking.");
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        IslandData island = islandService.islandAt(event.getBlock().getLocation()).orElse(null);
        if (island == null || islandService.isOwnerOrTrusted(event.getPlayer(), island)) {
            return;
        }
        if (!island.visitMode().blockBreakingAllowed()) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("This island does not allow visitor building.");
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onCombat(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker) || !(event.getEntity() instanceof Player)) {
            return;
        }
        IslandData island = islandService.islandAt(attacker.getLocation()).orElse(null);
        if (island == null || islandService.isOwnerOrTrusted(attacker, island)) {
            return;
        }
        if (!island.visitMode().combatAllowed()) {
            event.setCancelled(true);
            attacker.sendMessage("This island does not allow visitor combat.");
        }
    }
}
