package com.wosblock.listener;

import com.wosblock.island.IslandData;
import com.wosblock.island.IslandService;
import com.wosblock.item.ScrollService;
import org.bukkit.attribute.Attribute;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

public final class VoidFallListener implements Listener {
    private final IslandService islandService;
    private final ScrollService scrollService;

    public VoidFallListener(IslandService islandService, ScrollService scrollService) {
        this.islandService = islandService;
        this.scrollService = scrollService;
    }

    @EventHandler(ignoreCancelled = true)
    public void onVoidFall(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.CREATIVE || player.getLocation().getY() > player.getWorld().getMinHeight() + 2) {
            return;
        }
        IslandData island = islandService.islandAt(player.getLocation()).orElse(null);
        if (island == null) {
            return;
        }
        if (scrollService.hasVoidFallProtection(player)) {
            player.teleport(island.spawnLocation());
            player.setFallDistance(0);
            player.sendMessage("Void fall protection returned you to the island.");
            return;
        }
        if (island.ownerId().equals(player.getUniqueId()) || island.trustedMembers().contains(player.getUniqueId())) {
            player.teleport(island.spawnLocation());
            player.damage(Math.max(1.0, maxHealth(player) / 2.0));
            player.setFallDistance(0);
            player.sendMessage("Void insurance returned you to the island.");
        }
    }

    private double maxHealth(Player player) {
        org.bukkit.attribute.AttributeInstance attribute = player.getAttribute(Attribute.MAX_HEALTH);
        return attribute == null ? 20.0 : attribute.getValue();
    }
}
