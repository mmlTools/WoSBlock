package com.wosblock.listener;

import com.wosblock.item.ScrollService;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;

public final class ScrollUseListener implements Listener {
    private final ScrollService scrollService;

    public ScrollUseListener(ScrollService scrollService) {
        this.scrollService = scrollService;
    }

    @EventHandler(ignoreCancelled = true)
    public void onScrollUse(PlayerInteractEvent event) {
        if (event.getAction() == Action.LEFT_CLICK_BLOCK && scrollService.handlePlatformSelection(event.getPlayer(), event.getClickedBlock(), true)) {
            event.setCancelled(true);
            return;
        }
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            if (scrollService.handlePlatformSelection(event.getPlayer(), event.getClickedBlock(), false)
                || scrollService.handleHopperLinkSelection(event.getPlayer(), event.getClickedBlock())) {
                event.setCancelled(true);
                return;
            }
        }
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        if (scrollService.use(event.getPlayer(), event.getItem())) {
            event.setCancelled(true);
        }
    }
}
