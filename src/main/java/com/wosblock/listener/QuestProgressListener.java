package com.wosblock.listener;

import com.wosblock.island.IslandService;
import com.wosblock.quest.QuestService;
import com.wosblock.quest.QuestType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.entity.Player;

public final class QuestProgressListener implements Listener {
    private final QuestService questService;
    private final IslandService islandService;

    public QuestProgressListener(QuestService questService, IslandService islandService) {
        this.questService = questService;
        this.islandService = islandService;
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (islandService.islandAt(event.getBlock().getLocation()).isEmpty()) {
            return;
        }
        questService.record(event.getPlayer(), QuestType.BREAK_BLOCK, event.getBlock().getType(), 1);
    }

    @EventHandler(ignoreCancelled = true)
    public void onFish(PlayerFishEvent event) {
        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH || !(event.getCaught() instanceof org.bukkit.entity.Item item)) {
            return;
        }
        if (islandService.islandAt(event.getPlayer().getLocation()).isEmpty()) {
            return;
        }
        ItemStack stack = item.getItemStack();
        questService.record(event.getPlayer(), QuestType.FISH, stack.getType(), stack.getAmount());
    }

    @EventHandler(ignoreCancelled = true)
    public void onCraft(CraftItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (islandService.islandAt(player.getLocation()).isEmpty()) {
            return;
        }
        ItemStack result = event.getRecipe().getResult();
        questService.record(player, QuestType.CRAFT_ITEM, result.getType(), result.getAmount());
    }
}
