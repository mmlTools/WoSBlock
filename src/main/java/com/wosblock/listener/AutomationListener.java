package com.wosblock.listener;

import com.wosblock.WoSBlockPlugin;
import com.wosblock.automation.CompactorService;
import com.wosblock.automation.HopperFilterService;
import com.wosblock.island.IslandData;
import com.wosblock.island.IslandService;
import com.wosblock.util.Text;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Chest;
import org.bukkit.block.DoubleChest;
import org.bukkit.block.Hopper;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.Location;

public final class AutomationListener implements Listener {
    private enum ButtonType {
        SORT_CHEST,
        HOPPER_FILTER
    }

    private record ButtonContext(Inventory inventory, int slot, ButtonType type, Location location) {
    }

    private final WoSBlockPlugin plugin;
    private final CompactorService compactorService;
    private final HopperFilterService hopperFilterService;
    private final IslandService islandService;
    private final NamespacedKey buttonKey;
    private final Map<UUID, ButtonContext> buttons = new HashMap<>();

    public AutomationListener(WoSBlockPlugin plugin, CompactorService compactorService, HopperFilterService hopperFilterService, IslandService islandService) {
        this.plugin = plugin;
        this.compactorService = compactorService;
        this.hopperFilterService = hopperFilterService;
        this.islandService = islandService;
        this.buttonKey = new NamespacedKey(plugin, "automation_button");
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
            addButton(player, inventory, ButtonType.SORT_CHEST, location, Material.HOPPER, "Sort Chest");
            return;
        }
        if (holder instanceof Hopper) {
            addButton(player, inventory, ButtonType.HOPPER_FILTER, location, Material.COMPARATOR, "Hopper Filter");
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        ButtonContext context = buttons.get(player.getUniqueId());
        if (context == null || event.getClickedInventory() != context.inventory() || event.getSlot() != context.slot()) {
            return;
        }

        ItemStack clicked = event.getCurrentItem();
        if (!isButton(clicked, context.type())) {
            buttons.remove(player.getUniqueId());
            return;
        }

        event.setCancelled(true);
        context.inventory().setItem(context.slot(), null);
        buttons.remove(player.getUniqueId());

        if (context.type() == ButtonType.SORT_CHEST) {
            sort(context.inventory());
            player.sendMessage("Chest sorted.");
            return;
        }

        player.closeInventory();
        org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> hopperFilterService.open(player, context.location()));
    }

    @EventHandler
    public void onContainerClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player player) {
            ButtonContext context = buttons.remove(player.getUniqueId());
            if (context != null && isButton(context.inventory().getItem(context.slot()), context.type())) {
                context.inventory().setItem(context.slot(), null);
            }
        }
    }

    private void addButton(Player player, Inventory inventory, ButtonType type, Location location, Material material, String name) {
        removeStrayButtons(inventory);
        int slot = firstEmptySlot(inventory);
        if (slot == -1) {
            return;
        }
        inventory.setItem(slot, button(material, name, type));
        buttons.put(player.getUniqueId(), new ButtonContext(inventory, slot, type, location));
    }

    private int firstEmptySlot(Inventory inventory) {
        for (int slot = inventory.getSize() - 1; slot >= 0; slot--) {
            ItemStack item = inventory.getItem(slot);
            if (item == null || item.getType() == Material.AIR) {
                return slot;
            }
        }
        return -1;
    }

    private ItemStack button(Material material, String name, ButtonType type) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Text.plain(name));
        meta.lore(Text.lore("Click to use."));
        meta.getPersistentDataContainer().set(buttonKey, PersistentDataType.STRING, type.name());
        item.setItemMeta(meta);
        return item;
    }

    private void removeStrayButtons(Inventory inventory) {
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            if (isAnyButton(inventory.getItem(slot))) {
                inventory.setItem(slot, null);
            }
        }
    }

    private boolean isButton(ItemStack item, ButtonType type) {
        return item != null
            && item.hasItemMeta()
            && type.name().equals(item.getItemMeta().getPersistentDataContainer().get(buttonKey, PersistentDataType.STRING));
    }

    private boolean isAnyButton(ItemStack item) {
        return item != null
            && item.hasItemMeta()
            && item.getItemMeta().getPersistentDataContainer().has(buttonKey, PersistentDataType.STRING);
    }

    private boolean canUse(Player player, Location location) {
        IslandData island = islandService.islandAt(location).orElse(null);
        return island == null || islandService.isOwnerOrTrusted(player, island);
    }

    @EventHandler
    public void onFilterClose(InventoryCloseEvent event) {
        if (Text.plainText(event.getView().title()).equals("Hopper Filter") && event.getPlayer() instanceof Player player) {
            hopperFilterService.save(player, event.getInventory());
        }
    }

    private void sort(Inventory inventory) {
        List<ItemStack> sorted = java.util.Arrays.stream(inventory.getContents())
            .filter(item -> item != null && item.getType() != Material.AIR && !isAnyButton(item))
            .sorted(Comparator.comparing(item -> item.getType().name()))
            .toList();
        inventory.clear();
        sorted.forEach(inventory::addItem);
    }
}
