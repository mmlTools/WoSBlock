package com.wosblock.listener;

import com.wosblock.WoSBlockPlugin;
import com.wosblock.item.CustomEnchantService;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Random;
import java.util.Set;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Ageable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

public final class CustomEnchantListener implements Listener {
    private static final BlockFace[] FACES = {
        BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST, BlockFace.UP, BlockFace.DOWN
    };
    private static final Set<Material> LOGS = Set.of(
        Material.OAK_LOG, Material.SPRUCE_LOG, Material.BIRCH_LOG, Material.JUNGLE_LOG,
        Material.ACACIA_LOG, Material.DARK_OAK_LOG, Material.MANGROVE_LOG, Material.CHERRY_LOG,
        Material.PALE_OAK_LOG, Material.CRIMSON_STEM, Material.WARPED_STEM
    );
    private static final Map<Material, Material> REPLANT_SEEDS = Map.of(
        Material.WHEAT, Material.WHEAT_SEEDS,
        Material.CARROTS, Material.CARROT,
        Material.POTATOES, Material.POTATO,
        Material.BEETROOTS, Material.BEETROOT_SEEDS,
        Material.NETHER_WART, Material.NETHER_WART
    );

    private final WoSBlockPlugin plugin;
    private final CustomEnchantService enchantService;
    private final Set<Block> processing = new HashSet<>();
    private final Random random = new Random();
    private static final Map<Material, Material> SMELTS = Map.of(
        Material.IRON_ORE, Material.IRON_INGOT,
        Material.DEEPSLATE_IRON_ORE, Material.IRON_INGOT,
        Material.GOLD_ORE, Material.GOLD_INGOT,
        Material.DEEPSLATE_GOLD_ORE, Material.GOLD_INGOT,
        Material.COPPER_ORE, Material.COPPER_INGOT,
        Material.DEEPSLATE_COPPER_ORE, Material.COPPER_INGOT,
        Material.ANCIENT_DEBRIS, Material.NETHERITE_SCRAP
    );

    public CustomEnchantListener(WoSBlockPlugin plugin, CustomEnchantService enchantService) {
        this.plugin = plugin;
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
    public void onCustomToolBreak(BlockBreakEvent event) {
        if (processing.remove(event.getBlock())) {
            return;
        }
        ItemStack tool = event.getPlayer().getInventory().getItemInMainHand();
        handleDropEnchantments(event, tool);
        handleExperienceBoost(event, tool);
        handleHarvesterFortune(event, tool);
        handleReplant(event, tool);
        handleLumberjack(event, tool);
        handleBlastMining(event, tool);
    }

    private void handleBlastMining(BlockBreakEvent event, ItemStack tool) {
        if (!enchantService.hasEnchant(tool, "blast-mining")) {
            return;
        }
        if (!event.getBlock().getType().name().endsWith("_ORE")) {
            return;
        }

        int max = plugin.extraConfig("enchants.yml").getInt("custom-enchants.blast-mining.max-adjacent-blocks", 3);
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
            relative.breakNaturally(tool);
            broken++;
        }
    }

    private void handleLumberjack(BlockBreakEvent event, ItemStack tool) {
        if (!enchantService.hasEnchant(tool, "lumberjack") || !LOGS.contains(event.getBlock().getType())) {
            return;
        }
        int maxLogs = plugin.extraConfig("enchants.yml").getInt("custom-enchants.lumberjack.max-logs", 32);
        Material targetType = event.getBlock().getType();
        Queue<Block> queue = new ArrayDeque<>();
        Set<Block> visited = new HashSet<>();
        queue.add(event.getBlock());

        int broken = 0;
        while (!queue.isEmpty() && broken < maxLogs) {
            Block block = queue.remove();
            if (!visited.add(block) || block.equals(event.getBlock()) || block.getType() != targetType) {
                continue;
            }
            processing.add(block);
            block.breakNaturally(tool);
            broken++;
            for (BlockFace face : FACES) {
                queue.add(block.getRelative(face));
            }
        }
    }

    private void handleExperienceBoost(BlockBreakEvent event, ItemStack tool) {
        if (!enchantService.hasEnchant(tool, "experience-boost")) {
            return;
        }
        double multiplier = plugin.extraConfig("enchants.yml").getDouble("custom-enchants.experience-boost.multiplier", 1.5);
        event.setExpToDrop((int) Math.ceil(event.getExpToDrop() * multiplier));
    }

    private void handleHarvesterFortune(BlockBreakEvent event, ItemStack tool) {
        if (!enchantService.hasEnchant(tool, "harvester-fortune") || !(event.getBlock().getBlockData() instanceof Ageable ageable)) {
            return;
        }
        if (ageable.getAge() < ageable.getMaximumAge() || !REPLANT_SEEDS.containsKey(event.getBlock().getType())) {
            return;
        }
        double chance = plugin.extraConfig("enchants.yml").getDouble("custom-enchants.harvester-fortune.extra-drop-chance", 0.35);
        if (random.nextDouble() > chance) {
            return;
        }
        List<ItemStack> drops = new ArrayList<>(event.getBlock().getDrops(tool, event.getPlayer()));
        if (drops.isEmpty()) {
            return;
        }
        ItemStack bonus = drops.get(random.nextInt(drops.size())).clone();
        bonus.setAmount(1);
        event.getPlayer().getInventory().addItem(bonus).values()
            .forEach(leftover -> event.getBlock().getWorld().dropItemNaturally(event.getBlock().getLocation(), leftover));
    }

    private void handleReplant(BlockBreakEvent event, ItemStack tool) {
        if (!enchantService.hasEnchant(tool, "replant") || !(event.getBlock().getBlockData() instanceof Ageable ageable)) {
            return;
        }
        Material seed = REPLANT_SEEDS.get(event.getBlock().getType());
        if (seed == null || ageable.getAge() < ageable.getMaximumAge() || !removeOneSeed(event, seed)) {
            return;
        }
        Block block = event.getBlock();
        Material crop = block.getType();
        org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> {
            block.setType(crop, false);
            if (block.getBlockData() instanceof Ageable replanted) {
                replanted.setAge(0);
                block.setBlockData(replanted, false);
            }
        });
    }

    private boolean removeOneSeed(BlockBreakEvent event, Material seed) {
        ItemStack seedStack = new ItemStack(seed, 1);
        if (event.getPlayer().getInventory().containsAtLeast(seedStack, 1)) {
            event.getPlayer().getInventory().removeItem(seedStack);
            return true;
        }
        return false;
    }

    private void handleDropEnchantments(BlockBreakEvent event, ItemStack tool) {
        if (enchantService.hasEnchant(tool, "auto-smelt")) {
            Material smelted = SMELTS.get(event.getBlock().getType());
            if (smelted != null) {
                event.setDropItems(false);
                giveOrDrop(event, List.of(new ItemStack(smelted)));
                return;
            }
        }
        if (enchantService.hasEnchant(tool, "telekinesis")) {
            Collection<ItemStack> drops = event.getBlock().getDrops(tool, event.getPlayer());
            event.setDropItems(false);
            giveOrDrop(event, drops);
        }
    }

    private void giveOrDrop(BlockBreakEvent event, Collection<ItemStack> drops) {
        for (ItemStack drop : drops) {
            event.getPlayer().getInventory().addItem(drop).values()
                .forEach(leftover -> event.getBlock().getWorld().dropItemNaturally(event.getBlock().getLocation(), leftover));
        }
    }
}
