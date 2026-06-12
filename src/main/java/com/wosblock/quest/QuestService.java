package com.wosblock.quest;

import com.wosblock.WoSBlockPlugin;
import com.wosblock.economy.BalanceService;
import com.wosblock.island.IslandData;
import com.wosblock.island.IslandService;
import com.wosblock.item.ItemFactory;
import com.wosblock.storage.Storage;
import com.wosblock.util.Text;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public final class QuestService {
    private final WoSBlockPlugin plugin;
    private final BalanceService balanceService;
    private final IslandService islandService;
    private final Storage storage;
    private final ItemFactory itemFactory;
    private final Map<String, QuestDefinition> definitions = new LinkedHashMap<>();
    private final Map<UUID, Map<String, QuestProgress>> active = new ConcurrentHashMap<>();
    private final Map<UUID, Map<String, Long>> completions = new ConcurrentHashMap<>();
    private final Set<UUID> loadedPlayers = ConcurrentHashMap.newKeySet();
    private final Set<UUID> loadingPlayers = ConcurrentHashMap.newKeySet();

    public QuestService(WoSBlockPlugin plugin, BalanceService balanceService, IslandService islandService, Storage storage) {
        this.plugin = plugin;
        this.balanceService = balanceService;
        this.islandService = islandService;
        this.storage = storage;
        this.itemFactory = new ItemFactory(plugin);
        loadDefinitions();
    }

    public Collection<String> questIds() {
        return definitions.keySet();
    }

    public int activeCount(Player player) {
        preload(player);
        return active.computeIfAbsent(player.getUniqueId(), ignored -> new ConcurrentHashMap<>()).size();
    }

    public java.util.List<String> activeSummaries(Player player, int limit) {
        preload(player);
        Map<String, QuestProgress> playerQuests = active.get(player.getUniqueId());
        if (playerQuests == null || playerQuests.isEmpty()) {
            return java.util.List.of("No active quests");
        }
        return playerQuests.values().stream()
            .limit(limit)
            .map(progress -> progress.definition().displayName() + " " + progress.progress() + "/" + progress.definition().amount())
            .toList();
    }

    public ItemStack displayItem(Player player, String questId) {
        preload(player);
        QuestDefinition definition = definitions.get(questId);
        QuestProgress progress = active.computeIfAbsent(player.getUniqueId(), ignored -> new ConcurrentHashMap<>()).get(questId);
        ItemStack item = new ItemStack(definition.material() == Material.AIR ? Material.PAPER : definition.material());
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Text.legacy(definition.displayName()));
        String availability = availability(player, definition);
        int visibleProgress = definition.type() == QuestType.GATHER_ITEM ? countAvailableTurnInItems(player, definition.material()) : progress == null ? 0 : progress.progress();
        boolean complete = definition.type() == QuestType.GATHER_ITEM ? visibleProgress >= definition.amount() : progress != null && progress.complete();
        String status = progress == null ? availability : Math.min(visibleProgress, definition.amount()) + "/" + definition.amount() + (complete ? " - click to claim" : "");
        meta.lore(Text.lore(
            "Schedule: " + definition.schedule().name(),
            "Task: " + definition.type().name(),
            "Target: " + definition.material().name(),
            "Progress: " + status,
            "Reward: " + definition.rewardMoney() + " coins, " + definition.rewardXp() + " XP"
                + (definition.rewardItems().isEmpty() ? "" : ", items: " + definition.rewardItems())
        ));
        item.setItemMeta(meta);
        return item;
    }

    public void acceptOrClaim(Player player, String questId) {
        preload(player);
        QuestDefinition definition = definitions.get(questId);
        if (definition == null) {
            return;
        }
        Map<String, QuestProgress> playerQuests = active.computeIfAbsent(player.getUniqueId(), ignored -> new ConcurrentHashMap<>());
        QuestProgress progress = playerQuests.get(questId);
        if (progress == null) {
            if (!available(player, definition)) {
                player.sendMessage("That quest is not available yet. " + availability(player, definition));
                return;
            }
            int max = plugin.getConfig().getInt("islands.max-active-quests", 3);
            if (playerQuests.size() >= max) {
                player.sendMessage("You already have the maximum number of active quests.");
                return;
            }
            playerQuests.put(questId, new QuestProgress(definition));
            saveQuestProgress(player.getUniqueId(), questId, 0);
            player.sendMessage("Accepted quest: " + definition.displayName());
            return;
        }
        boolean complete = definition.type() == QuestType.GATHER_ITEM
            ? countAvailableTurnInItems(player, definition.material()) >= definition.amount()
            : progress.complete();
        if (!complete) {
            player.sendMessage("Quest is not complete yet.");
            return;
        }
        if (requiresItemTurnIn(definition) && countAvailableTurnInItems(player, definition.material()) < definition.amount()) {
            player.sendMessage("You need " + definition.amount() + "x " + definition.material().name() + " in your island inventory to claim this quest.");
            return;
        }
        if (requiresItemTurnIn(definition)) {
            removeTurnInItems(player, definition.material(), definition.amount());
        }
        balanceService.deposit(player, definition.rewardMoney());
        player.giveExp(definition.rewardXp());
        giveRewardItems(player, definition);
        markCompleted(player, definition);
        if (definition.schedule() == QuestSchedule.REPEATABLE) {
            progress.reset();
            saveQuestProgress(player.getUniqueId(), questId, 0);
        } else {
            playerQuests.remove(questId);
            deleteQuestProgress(player.getUniqueId(), questId);
        }
        player.sendMessage("Claimed quest reward: " + definition.displayName());
    }

    public void record(Player player, QuestType type, Material material, int amount) {
        preload(player);
        Map<String, QuestProgress> playerQuests = active.get(player.getUniqueId());
        if (playerQuests == null) {
            return;
        }
        for (QuestProgress progress : playerQuests.values()) {
            QuestDefinition definition = progress.definition();
            if (definition.type() == type && definition.material() == material) {
                progress.addProgress(amount);
                saveQuestProgress(player.getUniqueId(), definition.id(), progress.progress());
                if (progress.complete()) {
                    player.sendMessage("Quest complete: " + definition.displayName() + ". Return to the Clerk.");
                }
            }
        }
    }

    public void preload(Player player) {
        UUID playerId = player.getUniqueId();
        if (loadedPlayers.contains(playerId) || !loadingPlayers.add(playerId)) {
            return;
        }
        completions.computeIfAbsent(playerId, ignored -> new ConcurrentHashMap<>());
        active.computeIfAbsent(playerId, ignored -> new ConcurrentHashMap<>());
        storage.loadQuestCompletions(playerId).thenCombine(
            storage.loadQuestProgress(playerId),
            (loadedCompletions, loadedProgress) -> {
                Map<String, Long> playerCompletions = completions.computeIfAbsent(playerId, ignored -> new ConcurrentHashMap<>());
                loadedCompletions.forEach(playerCompletions::putIfAbsent);

                Map<String, QuestProgress> playerProgress = active.computeIfAbsent(playerId, ignored -> new ConcurrentHashMap<>());
                loadedProgress.forEach((questId, progress) -> {
                    QuestDefinition definition = definitions.get(questId);
                    if (definition != null) {
                        playerProgress.putIfAbsent(questId, new QuestProgress(definition, progress));
                    }
                });
                return null;
            }
        ).whenComplete((ignored, ex) -> {
            loadingPlayers.remove(playerId);
            if (ex != null) {
                plugin.getLogger().warning("Could not load quest stats: " + ex.getMessage());
                return;
            }
            loadedPlayers.add(playerId);
        });
    }

    private void loadDefinitions() {
        FileConfiguration config = plugin.extraConfig("quests.yml");
        ConfigurationSection section = config.getConfigurationSection("quests");
        if (section == null) {
            return;
        }
        for (String id : section.getKeys(false)) {
            Material material = Material.matchMaterial(section.getString(id + ".material", "AIR"));
            QuestType type = QuestType.valueOf(section.getString(id + ".type", "BREAK_BLOCK"));
            definitions.put(id, new QuestDefinition(
                id,
                section.getString(id + ".display-name", id),
                QuestSchedule.valueOf(section.getString(id + ".schedule", "ONCE").toUpperCase()),
                type,
                material == null ? Material.AIR : material,
                section.getInt(id + ".amount", 1),
                section.getDouble(id + ".reward-money", 0),
                section.getInt(id + ".reward-xp", 0),
                readRewardItems(section.getConfigurationSection(id + ".reward-items"))
            ));
        }
    }

    private boolean available(Player player, QuestDefinition definition) {
        Long completedAt = completions
            .computeIfAbsent(player.getUniqueId(), ignored -> new ConcurrentHashMap<>())
            .get(definition.id());
        if (definition.schedule() == QuestSchedule.REPEATABLE) {
            return true;
        }
        if (completedAt == null) {
            return true;
        }
        if (definition.schedule() == QuestSchedule.ONCE) {
            return false;
        }
        return System.currentTimeMillis() >= completedAt + definition.schedule().cooldownMillis();
    }

    private String availability(Player player, QuestDefinition definition) {
        Long completedAt = completions
            .computeIfAbsent(player.getUniqueId(), ignored -> new ConcurrentHashMap<>())
            .get(definition.id());
        if (definition.schedule() == QuestSchedule.REPEATABLE) {
            return "Click to accept";
        }
        if (completedAt == null) {
            return "Click to accept";
        }
        if (definition.schedule() == QuestSchedule.ONCE) {
            return "Already completed";
        }
        long remaining = (completedAt + definition.schedule().cooldownMillis()) - System.currentTimeMillis();
        if (remaining <= 0) {
            return "Click to accept";
        }
        long hours = Math.max(1, java.util.concurrent.TimeUnit.MILLISECONDS.toHours(remaining));
        return "Available in " + hours + "h";
    }

    private void markCompleted(Player player, QuestDefinition definition) {
        long completedAt = System.currentTimeMillis();
        completions
            .computeIfAbsent(player.getUniqueId(), ignored -> new ConcurrentHashMap<>())
            .put(definition.id(), completedAt);
        storage.saveQuestCompletion(player.getUniqueId(), definition.id(), completedAt)
            .exceptionally(ex -> {
                plugin.getLogger().warning("Could not save quest completion: " + ex.getMessage());
                return null;
            });
    }

    private void saveQuestProgress(UUID playerId, String questId, int progress) {
        storage.saveQuestProgress(playerId, questId, progress)
            .exceptionally(ex -> {
                plugin.getLogger().warning("Could not save quest progress: " + ex.getMessage());
                return null;
            });
    }

    private void deleteQuestProgress(UUID playerId, String questId) {
        storage.deleteQuestProgress(playerId, questId)
            .exceptionally(ex -> {
                plugin.getLogger().warning("Could not delete quest progress: " + ex.getMessage());
                return null;
            });
    }

    private Map<String, Integer> readRewardItems(ConfigurationSection section) {
        Map<String, Integer> rewards = new LinkedHashMap<>();
        if (section == null) {
            return rewards;
        }
        for (String key : section.getKeys(false)) {
            rewards.put(key, Math.max(1, section.getInt(key)));
        }
        return rewards;
    }

    private boolean requiresItemTurnIn(QuestDefinition definition) {
        return definition.type() == QuestType.GATHER_ITEM || definition.type() == QuestType.BREAK_BLOCK;
    }

    private int countAvailableTurnInItems(Player player, Material material) {
        int found = countInventory(player.getInventory(), material);
        IslandData island = islandService.islandAt(player.getLocation()).orElse(null);
        if (island == null) {
            return found;
        }
        return found + scanIslandContainers(island, material, false, Integer.MAX_VALUE);
    }

    private int countInventory(Inventory inventory, Material material) {
        int found = 0;
        for (ItemStack item : inventory.getContents()) {
            if (item != null && item.getType() == material) {
                found += item.getAmount();
            }
        }
        return found;
    }

    private void removeTurnInItems(Player player, Material material, int amount) {
        int remaining = removeFromInventory(player.getInventory(), material, amount);
        if (remaining <= 0) {
            return;
        }
        IslandData island = islandService.islandAt(player.getLocation()).orElse(null);
        if (island != null) {
            scanIslandContainers(island, material, true, remaining);
        }
    }

    private int removeFromInventory(Inventory inventory, Material material, int amount) {
        int remaining = amount;
        for (ItemStack item : inventory.getContents()) {
            if (remaining <= 0) {
                return 0;
            }
            if (item == null || item.getType() != material) {
                continue;
            }
            int removed = Math.min(remaining, item.getAmount());
            item.setAmount(item.getAmount() - removed);
            remaining -= removed;
        }
        return remaining;
    }

    private int scanIslandContainers(IslandData island, Material material, boolean remove, int amountLimit) {
        World world = org.bukkit.Bukkit.getWorld(island.worldName());
        if (world == null) {
            return 0;
        }
        int total = 0;
        int minX = island.centerX() - island.expandWest();
        int maxX = island.centerX() + island.expandEast();
        int minZ = island.centerZ() - island.expandNorth();
        int maxZ = island.centerZ() + island.expandSouth();
        int minY = Math.max(world.getMinHeight(), island.centerY() - 64);
        int maxY = Math.min(world.getMaxHeight() - 1, island.centerY() + 128);

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    BlockState state = world.getBlockAt(x, y, z).getState();
                    if (!(state instanceof Container container)) {
                        continue;
                    }
                    if (remove) {
                        int before = amountLimit - total;
                        int remaining = removeFromInventory(container.getInventory(), material, before);
                        total += before - remaining;
                        if (total >= amountLimit) {
                            return total;
                        }
                    } else {
                        total += countInventory(container.getInventory(), material);
                    }
                }
            }
        }
        return total;
    }

    private void giveRewardItems(Player player, QuestDefinition definition) {
        for (Map.Entry<String, Integer> reward : definition.rewardItems().entrySet()) {
            ItemStack item;
            if (reward.getKey().equalsIgnoreCase("ENDER_WORLD_EGG")) {
                item = itemFactory.enderWorldEgg(reward.getValue());
            } else {
                Material material = Material.matchMaterial(reward.getKey());
                if (material == null) {
                    plugin.getLogger().warning("Unknown quest reward item: " + reward.getKey());
                    continue;
                }
                item = new ItemStack(material, reward.getValue());
            }
            player.getInventory().addItem(item).values().forEach(leftover -> player.getWorld().dropItemNaturally(player.getLocation(), leftover));
        }
    }
}
