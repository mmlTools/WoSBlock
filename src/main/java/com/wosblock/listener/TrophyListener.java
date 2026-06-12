package com.wosblock.listener;

import com.wosblock.island.IslandData;
import com.wosblock.island.IslandService;
import com.wosblock.trophy.TrophyService;
import java.util.HashMap;
import java.util.Map;
import org.bukkit.Location;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;

public final class TrophyListener implements Listener {
    private final TrophyService trophyService;
    private final IslandService islandService;
    private final Map<Location, String> placedTrophies = new HashMap<>();

    public TrophyListener(TrophyService trophyService, IslandService islandService) {
        this.trophyService = trophyService;
        this.islandService = islandService;
    }

    @EventHandler(ignoreCancelled = true)
    public void onTrophyPlace(BlockPlaceEvent event) {
        String tier = trophyService.readTier(event.getItemInHand());
        if (tier == null) {
            return;
        }
        IslandData island = islandService.islandAt(event.getBlock().getLocation()).orElse(null);
        if (island == null || !islandService.isOwnerOrTrusted(event.getPlayer(), island)) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("Trophies can only be placed on your island.");
            return;
        }
        placedTrophies.put(event.getBlock().getLocation(), tier);
        island.achievementLevel(island.achievementLevel() + trophyService.value(tier));
        islandService.save(island);
        event.getPlayer().sendMessage("Placed " + tier + " trophy. Achievement Level: " + island.achievementLevel());
    }

    @EventHandler(ignoreCancelled = true)
    public void onTrophyBreak(BlockBreakEvent event) {
        String tier = placedTrophies.remove(event.getBlock().getLocation());
        if (tier == null) {
            return;
        }
        IslandData island = islandService.islandAt(event.getBlock().getLocation()).orElse(null);
        if (island == null) {
            return;
        }
        island.achievementLevel(island.achievementLevel() - trophyService.value(tier));
        islandService.save(island);
        event.getPlayer().sendMessage("Removed " + tier + " trophy. Achievement Level: " + island.achievementLevel());
    }
}
