package com.wosblock.hud;

import com.wosblock.WoSBlockPlugin;
import com.wosblock.economy.BalanceService;
import com.wosblock.island.IslandData;
import com.wosblock.island.IslandService;
import com.wosblock.quest.QuestService;
import com.wosblock.util.Text;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;

public final class HudService {
    private final WoSBlockPlugin plugin;
    private final IslandService islandService;
    private final BalanceService balanceService;
    private final QuestService questService;
    private final Set<UUID> hudHidden = new HashSet<>();
    private final Set<UUID> questieHidden = new HashSet<>();
    private final java.util.Map<UUID, BossBar> questBars = new java.util.HashMap<>();
    private int taskId = -1;

    public HudService(WoSBlockPlugin plugin, IslandService islandService, BalanceService balanceService, QuestService questService) {
        this.plugin = plugin;
        this.islandService = islandService;
        this.balanceService = balanceService;
        this.questService = questService;
    }

    public void start() {
        stop();
        taskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, this::updateAll, 20L, 40L);
    }

    public void stop() {
        if (taskId != -1) {
            Bukkit.getScheduler().cancelTask(taskId);
            taskId = -1;
        }
        questBars.values().forEach(BossBar::removeAll);
        questBars.clear();
    }

    public boolean toggleHud(Player player) {
        if (hudVisible(player)) {
            hudHidden.add(player.getUniqueId());
            player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
            return false;
        }
        hudHidden.remove(player.getUniqueId());
        updateHud(player);
        return true;
    }

    public boolean toggleQuestie(Player player) {
        if (questieVisible(player)) {
            questieHidden.add(player.getUniqueId());
            clearQuestie(player);
            return false;
        }
        questieHidden.remove(player.getUniqueId());
        updateQuestie(player);
        return true;
    }

    private boolean hudVisible(Player player) {
        Objective objective = player.getScoreboard().getObjective(DisplaySlot.SIDEBAR);
        return objective != null && objective.getName().equals("wos_hud");
    }

    private boolean questieVisible(Player player) {
        BossBar bar = questBars.get(player.getUniqueId());
        return bar != null && bar.getPlayers().contains(player);
    }

    public boolean canDisplay(Player player) {
        return islandForDisplay(player) != null;
    }

    private void updateAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            IslandData island = islandForDisplay(player);
            if (island == null) {
                clearPlayerDisplays(player);
                continue;
            }
            if (!hudHidden.contains(player.getUniqueId())) {
                updateHud(player, island);
            }
            if (!questieHidden.contains(player.getUniqueId())) {
                updateQuestie(player, island);
            }
        }
    }

    private void updateHud(Player player) {
        IslandData island = islandForDisplay(player);
        if (island == null) {
            clearPlayerDisplays(player);
            return;
        }
        updateHud(player, island);
    }

    private void updateHud(Player player, IslandData island) {
        Scoreboard scoreboard = Bukkit.getScoreboardManager().getNewScoreboard();
        Objective objective = scoreboard.registerNewObjective("wos_hud", Criteria.DUMMY, Text.legacy("&bwosblock HUD"));
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        int line = 10;
        objective.getScore("§6Coins: §f" + balanceService.balance(player)).setScore(line--);
        objective.getScore("§aGen: §f" + island.generatorLevel() + " (" + island.generatorXp() + " XP)").setScore(line--);
        objective.getScore("§dAch: §f" + island.achievementLevel()).setScore(line--);
        objective.getScore("§eMode: §f" + island.visitMode().name()).setScore(line--);
        objective.getScore("§9Bounds N/S: §f" + island.expandNorth() + "/" + island.expandSouth()).setScore(line--);
        objective.getScore("§9Bounds E/W: §f" + island.expandEast() + "/" + island.expandWest()).setScore(line--);
        player.setScoreboard(scoreboard);
    }

    private void updateQuestie(Player player) {
        IslandData island = islandForDisplay(player);
        if (island == null) {
            clearQuestie(player);
            return;
        }
        updateQuestie(player, island);
    }

    private void updateQuestie(Player player, IslandData island) {
        BossBar bar = questBars.computeIfAbsent(player.getUniqueId(), ignored -> {
            BossBar created = Bukkit.createBossBar("Questie", BarColor.BLUE, BarStyle.SEGMENTED_10);
            created.addPlayer(player);
            return created;
        });
        if (!bar.getPlayers().contains(player)) {
            bar.addPlayer(player);
        }
        java.util.List<String> quests = questService.activeSummaries(player, 3);
        bar.setTitle("§eQuestie: §f" + (quests.isEmpty() ? "No active quests" : String.join(" | ", quests)));
        bar.setProgress(Math.max(0.05, Math.min(1.0, quests.size() / 3.0)));
    }

    private IslandData islandForDisplay(Player player) {
        IslandData island = islandService.islandAt(player.getLocation()).orElse(null);
        if (island != null) {
            return island;
        }
        String islandWorld = plugin.getConfig().getString("islands.world-name", "world_wosblock");
        if (player.getWorld().getName().equals(islandWorld)) {
            return islandService.cachedIsland(player.getUniqueId()).orElse(null);
        }
        return null;
    }

    private void clearPlayerDisplays(Player player) {
        player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
        clearQuestie(player);
    }

    private void clearQuestie(Player player) {
        BossBar bar = questBars.remove(player.getUniqueId());
        if (bar != null) {
            bar.removeAll();
        }
    }
}
