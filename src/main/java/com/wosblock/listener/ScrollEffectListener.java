package com.wosblock.listener;

import com.wosblock.island.IslandService;
import com.wosblock.item.ScrollService;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.block.BlockGrowEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;

public final class ScrollEffectListener implements Listener {
    private static final BlockFace[] FACES = {
        BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST, BlockFace.UP, BlockFace.DOWN
    };

    private final ScrollService scrollService;
    private final IslandService islandService;
    private final Set<Block> processing = new HashSet<>();

    public ScrollEffectListener(ScrollService scrollService, IslandService islandService) {
        this.scrollService = scrollService;
        this.islandService = islandService;
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockDrops(BlockDropItemEvent event) {
        Player player = event.getPlayer();
        if (!scrollService.hasMagnet(player) || islandService.islandAt(event.getBlock().getLocation()).isEmpty()) {
            return;
        }
        event.getItems().removeIf(item -> {
            giveOrDrop(player, List.of(item.getItemStack()));
            item.remove();
            return true;
        });
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        Player killer = entity.getKiller();
        if (killer == null || !scrollService.hasMagnet(killer) || islandService.islandAt(entity.getLocation()).isEmpty()) {
            return;
        }
        giveOrDrop(killer, event.getDrops());
        event.getDrops().clear();
    }

    @EventHandler(ignoreCancelled = true)
    public void onFertilizedGrow(BlockGrowEvent event) {
        if (!scrollService.isFertilized(event.getBlock().getChunk())) {
            return;
        }
        if (event.getNewState().getBlockData() instanceof Ageable ageable) {
            int boost = Math.max(1, ageable.getMaximumAge() / 5);
            ageable.setAge(Math.min(ageable.getMaximumAge(), ageable.getAge() + boost));
            event.getNewState().setBlockData(ageable);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onExpressoMining(BlockBreakEvent event) {
        if (processing.remove(event.getBlock()) || !scrollService.hasExpresso(event.getPlayer())) {
            return;
        }
        if (islandService.islandAt(event.getBlock().getLocation()).isEmpty()) {
            return;
        }
        Material targetType = event.getBlock().getType();
        if (targetType == Material.AIR) {
            return;
        }
        int radius = 3;
        int broken = 0;
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    if (broken >= 24) {
                        return;
                    }
                    Block block = event.getBlock().getRelative(x, y, z);
                    if (block.equals(event.getBlock()) || block.getType() != targetType) {
                        continue;
                    }
                    processing.add(block);
                    block.breakNaturally(event.getPlayer().getInventory().getItemInMainHand());
                    broken++;
                }
            }
        }
    }

    private void giveOrDrop(Player player, Collection<ItemStack> drops) {
        for (ItemStack drop : drops) {
            player.getInventory().addItem(drop.clone()).values()
                .forEach(leftover -> player.getWorld().dropItemNaturally(player.getLocation(), leftover));
        }
    }
}
