package com.wosblock.listener;

import com.wosblock.economy.BalanceService;
import com.wosblock.quest.QuestService;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;

public final class PlayerStatsListener implements Listener {
    private final BalanceService balanceService;
    private final QuestService questService;

    public PlayerStatsListener(BalanceService balanceService, QuestService questService) {
        this.balanceService = balanceService;
        this.questService = questService;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        balanceService.preload(event.getPlayer());
        questService.preload(event.getPlayer());
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        balanceService.preload(event.getPlayer());
        questService.preload(event.getPlayer());
    }
}
