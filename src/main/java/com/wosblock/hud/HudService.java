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
    private final Set<UUID> hudLocked = new HashSet<>();
    private final Set<UUID> questieLocked = new HashSet<>();
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
        if (!hudHidden.add(player.getUniqueId())) {
            hudHidden.remove(player.getUniqueId());
            return true;
        }
        player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
        return false;
    }

    public boolean toggleQuestie(Player player) {
        if (!questieHidden.add(player.getUniqueId())) {
            questieHidden.remove(player.getUniqueId());
            return true;
        }
        BossBar bar = questBars.remove(player.getUniqueId());
        if (bar != null) {
            bar.removeAll();
        }
        return false;
    }

    public boolean toggleHudLock(Player player) {
        return toggle(hudLocked, player.getUniqueId());
    }

    public boolean toggleQuestieLock(Player player) {
        return toggle(questieLocked, player.getUniqueId());
    }

    private boolean toggle(Set<UUID> set, UUID uuid) {
        if (!set.add(uuid)) {
            set.remove(uuid);
            return false;
        }
        return true;
    }

    private void updateAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (islandService.islandAt(player.getLocation()).isEmpty()) {
                clearPlayerDisplays(player);
                continue;
            }
            if (!hudHidden.contains(player.getUniqueId())) {
                updateHud(player);
            }
            if (!questieHidden.contains(player.getUniqueId())) {
                updateQuestie(player);
            }
        }
    }

    private void updateHud(Player player) {
        IslandData island = islandService.islandAt(player.getLocation()).orElse(null);
        if (island == null) {
            clearPlayerDisplays(player);
            return;
        }
        Scoreboard scoreboard = Bukkit.getScoreboardManager().getNewScoreboard();
        Objective objective = scoreboard.registerNewObjective("wos_hud", Criteria.DUMMY, Text.legacy("&bSkyBlock HUD"));
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        int line = 10;
        objective.getScore("§6Coins: §f" + balanceService.balance(player)).setScore(line--);
        objective.getScore("§aGen: §f" + island.generatorLevel() + " (" + island.generatorXp() + " XP)").setScore(line--);
        objective.getScore("§dAch: §f" + island.achievementLevel()).setScore(line--);
        objective.getScore("§eMode: §f" + island.visitMode().name()).setScore(line--);
        objective.getScore("§9Bounds N/S: §f" + island.expandNorth() + "/" + island.expandSouth()).setScore(line--);
        objective.getScore("§9Bounds E/W: §f" + island.expandEast() + "/" + island.expandWest()).setScore(line--);
        objective.getScore("§8" + (hudLocked.contains(player.getUniqueId()) ? "HUD locked" : "HUD unlocked")).setScore(line);
        player.setScoreboard(scoreboard);
    }

    private void updateQuestie(Player player) {
        if (islandService.islandAt(player.getLocation()).isEmpty()) {
            clearQuestie(player);
            return;
        }
        BossBar bar = questBars.computeIfAbsent(player.getUniqueId(), ignored -> {
            BossBar created = Bukkit.createBossBar("Questie", BarColor.BLUE, BarStyle.SEGMENTED_10);
            created.addPlayer(player);
            return created;
        });
        if (!bar.getPlayers().contains(player)) {
            bar.addPlayer(player);
        }
        java.util.List<String> quests = questService.activeSummaries(player, 3);
        bar.setTitle("§eQuestie: §f" + String.join(" | ", quests)
            + "§8" + (questieLocked.contains(player.getUniqueId()) ? " [locked]" : " [unlocked]"));
        bar.setProgress(Math.max(0.05, Math.min(1.0, quests.size() / 3.0)));
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
