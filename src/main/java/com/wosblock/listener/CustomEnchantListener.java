package com.wosblock.listener;

import com.wosblock.item.CustomEnchantService;
import java.util.HashSet;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

public final class CustomEnchantListener implements Listener {
    private static final BlockFace[] FACES = {
        BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST, BlockFace.UP, BlockFace.DOWN
    };

    private final CustomEnchantService enchantService;
    private final Set<Block> processing = new HashSet<>();
    private static final Map<Material, Material> SMELTS = Map.of(
        Material.IRON_ORE, Material.IRON_INGOT,
        Material.DEEPSLATE_IRON_ORE, Material.IRON_INGOT,
        Material.GOLD_ORE, Material.GOLD_INGOT,
        Material.DEEPSLATE_GOLD_ORE, Material.GOLD_INGOT,
        Material.COPPER_ORE, Material.COPPER_INGOT,
        Material.DEEPSLATE_COPPER_ORE, Material.COPPER_INGOT,
        Material.ANCIENT_DEBRIS, Material.NETHERITE_SCRAP
    );

    public CustomEnchantListener(CustomEnchantService enchantService) {
        this.enchantService = enchantService;
    }

    @EventHandler(ignoreCancelled = true)
    public void onApplyBook(InventoryClickEvent event) {
        ItemStack cursor = event.getCursor();
        ItemStack target = event.getCurrentItem();
        if (enchantService.apply(target, cursor)) {
            event.setCancelled(true);
            event.getWhoClicked().sendMessage("Custom enchant applied.");
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlastMining(BlockBreakEvent event) {
        if (processing.remove(event.getBlock())) {
            return;
        }
        handleDropEnchantments(event);
        if (!enchantService.hasEnchant(event.getPlayer().getInventory().getItemInMainHand(), "blast-mining")) {
            return;
        }
        if (!event.getBlock().getType().name().endsWith("_ORE")) {
            return;
        }

        int max = 3;
        Material targetType = event.getBlock().getType();
        int broken = 0;
        for (BlockFace face : FACES) {
            if (broken >= max) {
                break;
            }
            Block relative = event.getBlock().getRelative(face);
            if (relative.getType() != targetType) {
                continue;
            }
            processing.add(relative);
            relative.breakNaturally(event.getPlayer().getInventory().getItemInMainHand());
            broken++;
        }

    }

    private void handleDropEnchantments(BlockBreakEvent event) {
        ItemStack tool = event.getPlayer().getInventory().getItemInMainHand();
        if (enchantService.hasEnchant(tool, "auto-smelt")) {
            Material smelted = SMELTS.get(event.getBlock().getType());
            if (smelted != null) {
                event.setDropItems(false);
                event.getPlayer().getInventory().addItem(new ItemStack(smelted)).values()
                    .forEach(leftover -> event.getBlock().getWorld().dropItemNaturally(event.getBlock().getLocation(), leftover));
                return;
            }
        }
        if (enchantService.hasEnchant(tool, "telekinesis")) {
            Collection<ItemStack> drops = event.getBlock().getDrops(tool, event.getPlayer());
            event.setDropItems(false);
            for (ItemStack drop : drops) {
                event.getPlayer().getInventory().addItem(drop).values()
                    .forEach(leftover -> event.getBlock().getWorld().dropItemNaturally(event.getBlock().getLocation(), leftover));
            }
        }
    }
}
