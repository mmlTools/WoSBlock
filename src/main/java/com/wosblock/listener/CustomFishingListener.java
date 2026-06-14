package com.wosblock.listener;

import com.wosblock.fishing.FishingLootManager;
import com.wosblock.island.IslandService;
import com.wosblock.item.ScrollService;
import org.bukkit.entity.Item;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerFishEvent;

public final class CustomFishingListener implements Listener {
    private final FishingLootManager fishingLootManager;
    private final IslandService islandService;
    private final ScrollService scrollService;

    public CustomFishingListener(FishingLootManager fishingLootManager, IslandService islandService, ScrollService scrollService) {
        this.fishingLootManager = fishingLootManager;
        this.islandService = islandService;
        this.scrollService = scrollService;
    }

    @EventHandler(ignoreCancelled = true)
    public void onIslandFishing(PlayerFishEvent event) {
        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH) {
            return;
        }
        if (islandService.islandAt(event.getPlayer().getLocation()).isEmpty()) {
            return;
        }
        if (event.getCaught() instanceof Item item) {
            item.setItemStack(fishingLootManager.rollCatch());
            if (scrollService.hasAquaticBait(event.getPlayer())) {
                org.bukkit.inventory.ItemStack stack = item.getItemStack();
                stack.setAmount(Math.min(stack.getMaxStackSize(), stack.getAmount() * 2));
                item.setItemStack(stack);
            }
        }
    }
}
