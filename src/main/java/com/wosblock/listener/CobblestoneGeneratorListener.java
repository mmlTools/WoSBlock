package com.wosblock.listener;

import com.wosblock.generator.CobblestoneGeneratorManager;
import com.wosblock.island.IslandData;
import com.wosblock.island.IslandService;
import java.util.Optional;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockFromToEvent;

public final class CobblestoneGeneratorListener implements Listener {
    private static final BlockFace[] CHECK_FACES = {
        BlockFace.NORTH,
        BlockFace.EAST,
        BlockFace.SOUTH,
        BlockFace.WEST,
        BlockFace.UP,
        BlockFace.DOWN
    };

    private final CobblestoneGeneratorManager generatorManager;
    private final IslandService islandService;

    public CobblestoneGeneratorListener(CobblestoneGeneratorManager generatorManager, IslandService islandService) {
        this.generatorManager = generatorManager;
        this.islandService = islandService;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFluidGenerate(BlockFromToEvent event) {
        Material sourceType = event.getBlock().getType();
        if (sourceType != Material.LAVA && sourceType != Material.WATER) {
            return;
        }

        Block target = event.getToBlock();
        if (!isGeneratorCollision(sourceType, target)) {
            return;
        }

        Optional<IslandData> island = islandService.islandAt(target.getLocation());
        if (island.isEmpty()) {
            return;
        }

        event.setCancelled(true);
        Material generated = generatorManager.rollMaterial(island.get().generatorLevel());
        target.setType(generated, false);
    }

    private boolean isGeneratorCollision(Material sourceType, Block target) {
        Material opposite = sourceType == Material.LAVA ? Material.WATER : Material.LAVA;
        for (BlockFace face : CHECK_FACES) {
            if (target.getRelative(face).getType() == opposite) {
                return true;
            }
        }
        return false;
    }
}
