package com.wosblock.listener;

import com.wosblock.generator.CobblestoneGeneratorManager;
import com.wosblock.island.IslandData;
import com.wosblock.island.IslandService;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;

public final class GeneratorMiningXpListener implements Listener {
    private final CobblestoneGeneratorManager generatorManager;
    private final IslandService islandService;

    public GeneratorMiningXpListener(CobblestoneGeneratorManager generatorManager, IslandService islandService) {
        this.generatorManager = generatorManager;
        this.islandService = islandService;
    }

    @EventHandler(ignoreCancelled = true)
    public void onGeneratorBlockMine(BlockBreakEvent event) {
        Material material = event.getBlock().getType();
        int xp = generatorManager.miningXp(material);
        if (xp <= 0) {
            return;
        }
        IslandData island = islandService.islandAt(event.getBlock().getLocation()).orElse(null);
        if (island == null) {
            return;
        }
        island.addGeneratorXp(xp);
        island.generatorLevel(generatorManager.levelForXp(island.generatorXp()));
        islandService.save(island);
    }
}
