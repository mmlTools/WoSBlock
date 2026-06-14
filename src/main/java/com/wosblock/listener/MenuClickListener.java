package com.wosblock.listener;

import com.wosblock.menu.MenuService;
import com.wosblock.util.Text;
import java.util.Set;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

public final class MenuClickListener implements Listener {
    private static final Set<String> PROTECTED_TITLES = Set.of(
        "Auctioneer",
        "Black Market",
        "Clerk Quests",
        "Island Guest Book"
    );

    private final MenuService menuService;

    public MenuClickListener(MenuService menuService) {
        this.menuService = menuService;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        String title = Text.plainText(event.getView().title());
        if (menuService.handleClick(player, title, event.getRawSlot(), event.getCurrentItem(), event.getClick())) {
            event.setCancelled(true);
            return;
        }
        if (PROTECTED_TITLES.contains(title) && event.getRawSlot() < event.getView().getTopInventory().getSize()) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        String title = Text.plainText(event.getView().title());
        if (!PROTECTED_TITLES.contains(title)) {
            return;
        }
        int topSize = event.getView().getTopInventory().getSize();
        for (int slot : event.getRawSlots()) {
            if (slot < topSize) {
                event.setCancelled(true);
                return;
            }
        }
    }
}
