package com.wosblock.listener;

import com.wosblock.WoSBlockPlugin;
import com.wosblock.automation.CompactorService;
import com.wosblock.island.IslandData;
import com.wosblock.island.IslandService;
import com.wosblock.util.Text;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.Chunk;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.block.Container;
import org.bukkit.block.DoubleChest;
import org.bukkit.block.Hopper;
import org.bukkit.block.Sign;
import org.bukkit.block.sign.Side;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.Location;

public final class AutomationListener implements Listener {
    private static final int RECEIVER_CACHE_MILLIS = 5000;
    private static final long WIRELESS_SCAN_PERIOD_TICKS = 8L;
    private static final int MAX_WIRELESS_STACKS_PER_SCAN = 5;

    private record CommandSign(String command, String argument) {
    }

    private record ReceiverCache(Location location, long expiresAt) {
    }

    private final WoSBlockPlugin plugin;
    private final CompactorService compactorService;
    private final IslandService islandService;
    private final Map<String, ReceiverCache> receiverCache = new java.util.HashMap<>();
    private BukkitTask wirelessTask;

    public AutomationListener(WoSBlockPlugin plugin, CompactorService compactorService, IslandService islandService) {
        this.plugin = plugin;
        this.compactorService = compactorService;
        this.islandService = islandService;
    }

    public void start() {
        wirelessTask = org.bukkit.Bukkit.getScheduler().runTaskTimer(plugin, this::drainWirelessHoppers, WIRELESS_SCAN_PERIOD_TICKS, WIRELESS_SCAN_PERIOD_TICKS);
    }

    public void stop() {
        if (wirelessTask != null) {
            wirelessTask.cancel();
            wirelessTask = null;
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player) {
            org.bukkit.Bukkit.getScheduler().runTaskLater(
                org.bukkit.plugin.java.JavaPlugin.getProvidingPlugin(getClass()),
                () -> compactorService.compact(player),
                1L
            );
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        Inventory inventory = event.getInventory();
        Location location = inventory.getLocation();
        if (location == null || !canUse(player, location)) {
            return;
        }

        InventoryHolder holder = inventory.getHolder();
        if (holder instanceof Chest || holder instanceof DoubleChest) {
            if (hasCommandSign(location.getBlock(), "/sortable")) {
                sort(inventory);
                player.sendMessage("Sortable chest sign found. Chest sorted.");
            }
        }
    }

    private boolean canUse(Player player, Location location) {
        IslandData island = islandService.islandAt(location).orElse(null);
        return island == null || islandService.isOwnerOrTrusted(player, island);
    }

    @EventHandler(ignoreCancelled = true)
    public void onSignChange(SignChangeEvent event) {
        String firstLine = Text.plainText(event.line(0)).trim();
        if (!firstLine.startsWith("/")) {
            return;
        }

        String[] parts = firstLine.split("\\s+", 2);
        String command = parts[0].toLowerCase(Locale.ROOT);
        String secondLine = Text.plainText(event.line(1)).trim();
        boolean secondLineIsCommand = secondLine.startsWith("/");

        if (command.equals("/sortable") && !secondLineIsCommand) {
            event.line(1, Text.plain("Sorting enabled"));
            return;
        }
        if (command.equals("/filter")) {
            String itemId = parts.length == 2 ? parts[1].trim() : "";
            if (itemId.isBlank()) {
                ItemStack held = event.getPlayer().getInventory().getItemInMainHand();
                if (held.getType() == Material.AIR) {
                    event.getPlayer().sendMessage("Hold the filter item when writing /filter.");
                    event.setCancelled(true);
                    return;
                }
                itemId = held.getType().getKey().getKey();
                held.setAmount(held.getAmount() - 1);
                event.line(0, Text.plain("/filter " + itemId));
            }
            if (secondLineIsCommand) {
                return;
            }
            event.line(1, Text.plain("Filter enabled"));
            return;
        }
        if (parts.length < 2 || parts[1].isBlank() || secondLineIsCommand) {
            return;
        }
        if (command.equals("/sender")) {
            event.line(1, Text.plain("Sender enabled"));
            return;
        }
        if (command.equals("/receiver")) {
            event.line(1, Text.plain("Receiver enabled"));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onHopperMove(InventoryMoveItemEvent event) {
        if (!(event.getDestination().getHolder() instanceof Hopper hopper)) {
            return;
        }

        Block hopperBlock = hopper.getBlock();
        CommandSign filter = commandSign(hopperBlock, "/filter");
        if (filter != null && !matchesFilter(filter.argument(), event.getItem())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onHopperPickup(InventoryPickupItemEvent event) {
        if (!(event.getInventory().getHolder() instanceof Hopper hopper)) {
            return;
        }
        CommandSign filter = commandSign(hopper.getBlock(), "/filter");
        if (filter != null && !matchesFilter(filter.argument(), event.getItem().getItemStack())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onFilteredHopperClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof Hopper hopper)) {
            return;
        }
        CommandSign filter = commandSign(hopper.getBlock(), "/filter");
        if (filter == null) {
            return;
        }

        if (event.isShiftClick() && event.getClickedInventory() != top && !matchesFilter(filter.argument(), event.getCurrentItem())) {
            event.setCancelled(true);
            return;
        }

        if (event.getClickedInventory() == top && event.getCursor() != null && event.getCursor().getType() != Material.AIR
            && !matchesFilter(filter.argument(), event.getCursor())) {
            event.setCancelled(true);
            return;
        }

        if (event.getClickedInventory() == top && event.getHotbarButton() >= 0 && event.getWhoClicked() instanceof Player player) {
            ItemStack hotbarItem = player.getInventory().getItem(event.getHotbarButton());
            if (hotbarItem != null && hotbarItem.getType() != Material.AIR && !matchesFilter(filter.argument(), hotbarItem)) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onFilteredHopperDrag(InventoryDragEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof Hopper hopper)) {
            return;
        }
        CommandSign filter = commandSign(hopper.getBlock(), "/filter");
        if (filter == null) {
            return;
        }

        int topSize = top.getSize();
        for (Map.Entry<Integer, ItemStack> entry : event.getNewItems().entrySet()) {
            if (entry.getKey() < topSize && !matchesFilter(filter.argument(), entry.getValue())) {
                event.setCancelled(true);
                return;
            }
        }
    }

    private void sort(Inventory inventory) {
        List<ItemStack> sorted = java.util.Arrays.stream(inventory.getContents())
            .filter(item -> item != null && item.getType() != Material.AIR)
            .sorted(Comparator.comparing(item -> item.getType().name()))
            .toList();
        inventory.clear();
        sorted.forEach(inventory::addItem);
    }

    private boolean hasCommandSign(Block block, String command) {
        return commandSign(block, command) != null;
    }

    private CommandSign commandSign(Block block, String command) {
        String normalized = command.toLowerCase(Locale.ROOT);
        for (Block nearby : nearbyBlocks(block)) {
            if (!(nearby.getState() instanceof Sign sign)) {
                continue;
            }
            for (CommandSign parsed : parseSign(sign)) {
                if (parsed.command().equals(normalized)) {
                    return parsed;
                }
            }
        }
        return null;
    }

    private List<Block> nearbyBlocks(Block block) {
        java.util.ArrayList<Block> blocks = new java.util.ArrayList<>();
        for (int x = -1; x <= 1; x++) {
            for (int y = -1; y <= 1; y++) {
                for (int z = -1; z <= 1; z++) {
                    if (x == 0 && y == 0 && z == 0) {
                        continue;
                    }
                    blocks.add(block.getRelative(x, y, z));
                }
            }
        }
        return blocks;
    }

    private List<CommandSign> parseSign(Sign sign) {
        java.util.ArrayList<CommandSign> commands = new java.util.ArrayList<>();
        commands.addAll(parseLines(sign, Side.FRONT));
        commands.addAll(parseLines(sign, Side.BACK));
        return commands;
    }

    private List<CommandSign> parseLines(Sign sign, Side side) {
        List<String> lines = sign.getSide(side).lines().stream()
            .map(Text::plainText)
            .map(String::trim)
            .toList();
        java.util.ArrayList<CommandSign> commands = new java.util.ArrayList<>();
        for (int index = 0; index < lines.size(); index++) {
            String line = lines.get(index);
            if (!line.startsWith("/")) {
                continue;
            }
            String[] parts = line.split("\\s+", 2);
            String argument = parts.length == 2 ? parts[1].trim() : "";
            if (argument.isEmpty() && index + 1 < lines.size() && isArgumentLine(lines.get(index + 1))) {
                argument = lines.get(index + 1).trim();
            }
            commands.add(new CommandSign(parts[0].toLowerCase(Locale.ROOT), argument));
        }
        return commands;
    }

    private boolean isArgumentLine(String line) {
        if (line.startsWith("/")) {
            return false;
        }
        String normalized = line.toLowerCase(Locale.ROOT);
        return !normalized.equals("sorting enabled")
            && !normalized.equals("filter enabled")
            && !normalized.equals("sender enabled")
            && !normalized.equals("receiver enabled");
    }

    private boolean matchesFilter(String itemId, ItemStack item) {
        Material material = material(itemId);
        return material != null && item != null && item.getType() == material;
    }

    private Material material(String itemId) {
        if (itemId == null || itemId.isBlank()) {
            return null;
        }
        String normalized = itemId.trim().toUpperCase(Locale.ROOT).replace("MINECRAFT:", "").replace('-', '_');
        return Material.matchMaterial(normalized);
    }

    private Inventory receiverInventory(IslandData island, String code) {
        String key = island.ownerId() + ":" + code.toLowerCase(Locale.ROOT);
        ReceiverCache cached = receiverCache.get(key);
        if (cached != null && cached.expiresAt() > System.currentTimeMillis()) {
            BlockState state = cached.location().getBlock().getState();
            if (state instanceof Container container && island.contains(container.getLocation())) {
                return container.getInventory();
            }
        }

        Inventory found = scanReceiverInventory(island, code);
        if (found != null && found.getLocation() != null) {
            receiverCache.put(key, new ReceiverCache(found.getLocation(), System.currentTimeMillis() + RECEIVER_CACHE_MILLIS));
        }
        return found;
    }

    private Inventory scanReceiverInventory(IslandData island, String code) {
        World world = org.bukkit.Bukkit.getWorld(island.worldName());
        if (world == null) {
            return null;
        }

        int minChunkX = Math.floorDiv(island.centerX() - island.expandWest(), 16);
        int maxChunkX = Math.floorDiv(island.centerX() + island.expandEast(), 16);
        int minChunkZ = Math.floorDiv(island.centerZ() - island.expandNorth(), 16);
        int maxChunkZ = Math.floorDiv(island.centerZ() + island.expandSouth(), 16);
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                if (!world.isChunkLoaded(chunkX, chunkZ)) {
                    continue;
                }
                for (BlockState state : world.getChunkAt(chunkX, chunkZ).getTileEntities()) {
                    if (state instanceof Sign sign && receiverMatches(sign, code)) {
                        Inventory inventory = adjacentContainer(sign.getBlock(), island);
                        if (inventory != null) {
                            return inventory;
                        }
                    }
                }
            }
        }
        return null;
    }

    private boolean receiverMatches(Sign sign, String code) {
        return parseSign(sign).stream()
            .anyMatch(parsed -> parsed.command().equals("/receiver") && parsed.argument().equalsIgnoreCase(code));
    }

    private Inventory adjacentContainer(Block signBlock, IslandData island) {
        for (Block nearby : nearbyBlocks(signBlock)) {
            if (!island.contains(nearby.getLocation())) {
                continue;
            }
            BlockState state = nearby.getState();
            if (state instanceof Chest chest) {
                return chest.getInventory();
            }
        }
        return null;
    }

    private void drainWirelessHoppers() {
        for (World world : org.bukkit.Bukkit.getWorlds()) {
            for (Chunk chunk : world.getLoadedChunks()) {
                for (BlockState state : chunk.getTileEntities()) {
                    if (state instanceof Hopper hopper) {
                        drainWirelessHopper(hopper.getBlock());
                    }
                }
            }
        }
    }

    private void drainWirelessHopper(Block hopperBlock) {
        CommandSign sender = commandSign(hopperBlock, "/sender");
        if (sender == null || sender.argument().isBlank()) {
            return;
        }

        IslandData island = islandService.islandAt(hopperBlock.getLocation()).orElse(null);
        if (island == null) {
            return;
        }

        Inventory receiver = receiverInventory(island, sender.argument());
        if (receiver == null) {
            return;
        }

        CommandSign filter = commandSign(hopperBlock, "/filter");
        if (!(hopperBlock.getState() instanceof Hopper hopper)) {
            return;
        }
        Inventory hopperInventory = hopper.getInventory();
        int movedStacks = 0;
        for (int slot = 0; slot < hopperInventory.getSize(); slot++) {
            ItemStack item = hopperInventory.getItem(slot);
            if (item == null || item.getType() == Material.AIR) {
                continue;
            }
            if (filter != null && !matchesFilter(filter.argument(), item)) {
                continue;
            }
            ItemStack moved = item.clone();
            Map<Integer, ItemStack> leftovers = receiver.addItem(moved);
            int leftoverAmount = leftovers.values().stream().mapToInt(ItemStack::getAmount).sum();
            int acceptedAmount = item.getAmount() - leftoverAmount;
            if (acceptedAmount <= 0) {
                continue;
            }
            if (acceptedAmount >= item.getAmount()) {
                hopperInventory.setItem(slot, null);
            } else {
                item.setAmount(item.getAmount() - acceptedAmount);
                hopperInventory.setItem(slot, item);
            }
            movedStacks++;
            if (leftoverAmount > 0 || movedStacks >= MAX_WIRELESS_STACKS_PER_SCAN) {
                return;
            }
        }
    }
}
