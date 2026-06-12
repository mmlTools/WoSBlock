package com.wosblock.island;

import com.wosblock.WoSBlockPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.WorldBorder;
import org.bukkit.entity.Player;

public final class BoundaryService {
    private final WoSBlockPlugin plugin;
    private final IslandService islandService;
    private int taskId = -1;

    public BoundaryService(WoSBlockPlugin plugin, IslandService islandService) {
        this.plugin = plugin;
        this.islandService = islandService;
    }

    public void start() {
        stop();
        taskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, this::updateAll, 20L, 20L);
    }

    public void stop() {
        if (taskId != -1) {
            Bukkit.getScheduler().cancelTask(taskId);
            taskId = -1;
        }
    }

    private void updateAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            IslandData island = islandService.islandAt(player.getLocation()).orElse(null);
            if (island == null) {
                player.setWorldBorder(null);
                continue;
            }
            applyWorldBorder(player, island);
            drawNearbyBoundary(player, island);
        }
    }

    private void applyWorldBorder(Player player, IslandData island) {
        WorldBorder border = Bukkit.createWorldBorder();
        border.setCenter(island.centerX(), island.centerZ());
        border.setSize(Math.max(island.width(), island.depth()));
        border.setWarningDistance(8);
        border.setDamageAmount(0);
        player.setWorldBorder(border);
    }

    private void drawNearbyBoundary(Player player, IslandData island) {
        Location base = player.getLocation();
        double minX = island.centerX() - island.expandWest();
        double maxX = island.centerX() + island.expandEast();
        double minZ = island.centerZ() - island.expandNorth();
        double maxZ = island.centerZ() + island.expandSouth();
        int yStart = Math.max(base.getBlockY() - 2, base.getWorld().getMinHeight());
        int yEnd = Math.min(base.getBlockY() + 8, base.getWorld().getMaxHeight());

        for (int y = yStart; y <= yEnd; y += 2) {
            for (int offset = -8; offset <= 8; offset += 2) {
                if (Math.abs(base.getX() - minX) < 12) {
                    particle(player, minX, y, base.getZ() + offset);
                }
                if (Math.abs(base.getX() - maxX) < 12) {
                    particle(player, maxX, y, base.getZ() + offset);
                }
                if (Math.abs(base.getZ() - minZ) < 12) {
                    particle(player, base.getX() + offset, y, minZ);
                }
                if (Math.abs(base.getZ() - maxZ) < 12) {
                    particle(player, base.getX() + offset, y, maxZ);
                }
            }
        }
    }

    private void particle(Player player, double x, double y, double z) {
        player.spawnParticle(Particle.END_ROD, x, y, z, 1, 0, 0, 0, 0);
    }
}
