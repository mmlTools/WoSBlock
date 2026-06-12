package com.wosblock.listener;

import com.wosblock.automation.VoidRecoveryService;
import com.wosblock.island.IslandData;
import com.wosblock.island.IslandService;
import com.wosblock.util.Text;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public final class GuestBookListener implements Listener {
    private final IslandService islandService;
    private final VoidRecoveryService voidRecoveryService;

    public GuestBookListener(IslandService islandService, VoidRecoveryService voidRecoveryService) {
        this.islandService = islandService;
        this.voidRecoveryService = voidRecoveryService;
    }

    @EventHandler(ignoreCancelled = true)
    public void onGuestBook(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) {
            return;
        }
        if (event.getClickedBlock().getType() != Material.ENCHANTING_TABLE) {
            return;
        }
        IslandData island = islandService.islandAt(event.getClickedBlock().getLocation()).orElse(null);
        if (island == null) {
            return;
        }

        Inventory inventory = Bukkit.createInventory(null, 27, Text.plain("Island Guest Book"));
        inventory.setItem(10, named(Material.PLAYER_HEAD, "Owner: " + playerName(island.ownerId())));
        inventory.setItem(12, named(Material.OAK_SIGN, "Trusted: " + island.trustedMembers().size()));
        inventory.setItem(14, named(Material.COBBLESTONE, "Generator Level: " + island.generatorLevel()));
        inventory.setItem(16, named(Material.NETHER_STAR, "Achievement Level: " + island.achievementLevel()));
        inventory.setItem(20, named(Material.HOPPER, "Void Cache: " + voidRecoveryService.cachedStacks(island) + "/27 stacks"));
        inventory.setItem(22, named(Material.COMPASS, "Visit Mode: " + island.visitMode().name()));
        inventory.setItem(24, named(Material.ENDER_PEARL, "Waypoints: " + island.waypoints().size() + "/3"));
        inventory.setItem(26, named(Material.BARRIER, "Bounds N/S/E/W: "
            + island.expandNorth() + "/" + island.expandSouth() + "/" + island.expandEast() + "/" + island.expandWest()));
        event.getPlayer().openInventory(inventory);
        event.setCancelled(true);
    }

    private ItemStack named(Material material, String name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Text.plain(name));
        item.setItemMeta(meta);
        return item;
    }

    private String playerName(UUID uuid) {
        OfflinePlayer player = Bukkit.getOfflinePlayer(uuid);
        return player.getName() == null ? uuid.toString() : player.getName();
    }
}
