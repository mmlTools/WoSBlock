package com.wosblock.storage;

import com.wosblock.WoSBlockPlugin;
import com.wosblock.auction.AuctionListing;
import com.wosblock.island.IslandData;
import com.wosblock.island.VisitMode;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

public final class FlatFileStorage implements Storage {
    private final WoSBlockPlugin plugin;
    private final File islandFolder;
    private final File playerStatsFolder;
    private final File auctionsFile;

    public FlatFileStorage(WoSBlockPlugin plugin) {
        this.plugin = plugin;
        this.islandFolder = new File(plugin.getDataFolder(), "islands");
        this.playerStatsFolder = new File(plugin.getDataFolder(), "player-stats");
        this.auctionsFile = new File(plugin.getDataFolder(), "auctions.yml");
        if (!islandFolder.exists() && !islandFolder.mkdirs()) {
            plugin.getLogger().warning("Could not create island storage folder.");
        }
        if (!playerStatsFolder.exists() && !playerStatsFolder.mkdirs()) {
            plugin.getLogger().warning("Could not create player stats storage folder.");
        }
    }

    @Override
    public CompletableFuture<Optional<IslandData>> loadIsland(UUID ownerId) {
        CompletableFuture<Optional<IslandData>> future = new CompletableFuture<>();
        new BukkitRunnable() {
            @Override
            public void run() {
                File file = new File(islandFolder, ownerId + ".yml");
                if (!file.exists()) {
                    future.complete(Optional.empty());
                    return;
                }
                future.complete(Optional.of(readIsland(file, ownerId)));
            }
        }.runTaskAsynchronously(plugin);
        return future;
    }

    @Override
    public CompletableFuture<List<IslandData>> loadAllIslands() {
        CompletableFuture<List<IslandData>> future = new CompletableFuture<>();
        new BukkitRunnable() {
            @Override
            public void run() {
                List<IslandData> islands = new ArrayList<>();
                File[] files = islandFolder.listFiles((dir, name) -> name.endsWith(".yml"));
                if (files != null) {
                    for (File file : files) {
                        String uuidText = file.getName().substring(0, file.getName().length() - 4);
                        try {
                            islands.add(readIsland(file, UUID.fromString(uuidText)));
                        } catch (RuntimeException ex) {
                            plugin.getLogger().warning("Could not load island file " + file.getName() + ": " + ex.getMessage());
                        }
                    }
                }
                future.complete(islands);
            }
        }.runTaskAsynchronously(plugin);
        return future;
    }

    @Override
    public CompletableFuture<Void> saveIsland(IslandData island) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        new BukkitRunnable() {
            @Override
            public void run() {
                File file = new File(islandFolder, island.ownerId() + ".yml");
                YamlConfiguration yaml = new YamlConfiguration();
                yaml.set("world-name", island.worldName());
                yaml.set("parcel-index", island.parcelIndex());
                yaml.set("center.x", island.centerX());
                yaml.set("center.y", island.centerY());
                yaml.set("center.z", island.centerZ());
                yaml.set("radius", island.radius());
                yaml.set("bounds.north", island.expandNorth());
                yaml.set("bounds.south", island.expandSouth());
                yaml.set("bounds.east", island.expandEast());
                yaml.set("bounds.west", island.expandWest());
                yaml.set("visit-mode", island.visitMode().name());
                yaml.set("generator-level", island.generatorLevel());
                yaml.set("generator-xp", island.generatorXp());
                yaml.set("achievement-level", island.achievementLevel());
                island.waypoints().forEach((slot, point) -> {
                    yaml.set("waypoints." + slot + ".x", point[0]);
                    yaml.set("waypoints." + slot + ".y", point[1]);
                    yaml.set("waypoints." + slot + ".z", point[2]);
                });
                yaml.set("trusted-members", island.trustedMembers().stream().map(UUID::toString).toList());
                try {
                    yaml.save(file);
                    future.complete(null);
                } catch (IOException ex) {
                    future.completeExceptionally(ex);
                }
            }
        }.runTaskAsynchronously(plugin);
        return future;
    }

    @Override
    public CompletableFuture<Integer> nextIslandIndex() {
        CompletableFuture<Integer> future = new CompletableFuture<>();
        new BukkitRunnable() {
            @Override
            public void run() {
                File[] files = islandFolder.listFiles((dir, name) -> name.endsWith(".yml"));
                int next = 0;
                if (files != null) {
                    for (File file : files) {
                        try {
                            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
                            next = Math.max(next, yaml.getInt("parcel-index", -1) + 1);
                        } catch (RuntimeException ex) {
                            plugin.getLogger().warning("Could not inspect parcel index for " + file.getName() + ": " + ex.getMessage());
                        }
                    }
                }
                future.complete(next);
            }
        }.runTaskAsynchronously(plugin);
        return future;
    }

    @Override
    public CompletableFuture<Void> deleteIsland(UUID ownerId) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        new BukkitRunnable() {
            @Override
            public void run() {
                File file = new File(islandFolder, ownerId + ".yml");
                if (file.exists() && !file.delete()) {
                    future.completeExceptionally(new IOException("Could not delete " + file.getAbsolutePath()));
                    return;
                }
                future.complete(null);
            }
        }.runTaskAsynchronously(plugin);
        return future;
    }

    @Override
    public CompletableFuture<Void> deleteIslandPlayerData(UUID playerId, String worldName) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        new BukkitRunnable() {
            @Override
            public void run() {
                File file = playerStatsFile(playerId);
                YamlConfiguration yaml = loadPlayerStats(playerId);
                yaml.set("balances." + worldName, null);
                yaml.set("quest-completions", null);
                yaml.set("quest-progress", null);
                savePlayerStats(file, yaml, future);
            }
        }.runTaskAsynchronously(plugin);
        return future;
    }

    @Override
    public CompletableFuture<Double> loadBalance(UUID playerId, String worldName, double defaultBalance) {
        CompletableFuture<Double> future = new CompletableFuture<>();
        new BukkitRunnable() {
            @Override
            public void run() {
                YamlConfiguration yaml = loadPlayerStats(playerId);
                future.complete(yaml.getDouble("balances." + worldName, defaultBalance));
            }
        }.runTaskAsynchronously(plugin);
        return future;
    }

    @Override
    public CompletableFuture<Void> saveBalance(UUID playerId, String worldName, double balance) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        new BukkitRunnable() {
            @Override
            public void run() {
                File file = playerStatsFile(playerId);
                YamlConfiguration yaml = loadPlayerStats(playerId);
                yaml.set("balances." + worldName, balance);
                savePlayerStats(file, yaml, future);
            }
        }.runTaskAsynchronously(plugin);
        return future;
    }

    @Override
    public CompletableFuture<Map<String, Long>> loadQuestCompletions(UUID playerId) {
        CompletableFuture<Map<String, Long>> future = new CompletableFuture<>();
        new BukkitRunnable() {
            @Override
            public void run() {
                YamlConfiguration yaml = loadPlayerStats(playerId);
                Map<String, Long> completions = new LinkedHashMap<>();
                ConfigurationSection section = yaml.getConfigurationSection("quest-completions");
                if (section != null) {
                    for (String questId : section.getKeys(false)) {
                        completions.put(questId, section.getLong(questId));
                    }
                }
                future.complete(completions);
            }
        }.runTaskAsynchronously(plugin);
        return future;
    }

    @Override
    public CompletableFuture<Void> saveQuestCompletion(UUID playerId, String questId, long completedAt) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        new BukkitRunnable() {
            @Override
            public void run() {
                File file = playerStatsFile(playerId);
                YamlConfiguration yaml = loadPlayerStats(playerId);
                yaml.set("quest-completions." + questId, completedAt);
                savePlayerStats(file, yaml, future);
            }
        }.runTaskAsynchronously(plugin);
        return future;
    }

    @Override
    public CompletableFuture<Map<String, Integer>> loadQuestProgress(UUID playerId) {
        CompletableFuture<Map<String, Integer>> future = new CompletableFuture<>();
        new BukkitRunnable() {
            @Override
            public void run() {
                YamlConfiguration yaml = loadPlayerStats(playerId);
                Map<String, Integer> progress = new LinkedHashMap<>();
                ConfigurationSection section = yaml.getConfigurationSection("quest-progress");
                if (section != null) {
                    for (String questId : section.getKeys(false)) {
                        progress.put(questId, section.getInt(questId));
                    }
                }
                future.complete(progress);
            }
        }.runTaskAsynchronously(plugin);
        return future;
    }

    @Override
    public CompletableFuture<Void> saveQuestProgress(UUID playerId, String questId, int progress) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        new BukkitRunnable() {
            @Override
            public void run() {
                File file = playerStatsFile(playerId);
                YamlConfiguration yaml = loadPlayerStats(playerId);
                yaml.set("quest-progress." + questId, progress);
                savePlayerStats(file, yaml, future);
            }
        }.runTaskAsynchronously(plugin);
        return future;
    }

    @Override
    public CompletableFuture<Void> deleteQuestProgress(UUID playerId, String questId) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        new BukkitRunnable() {
            @Override
            public void run() {
                File file = playerStatsFile(playerId);
                YamlConfiguration yaml = loadPlayerStats(playerId);
                yaml.set("quest-progress." + questId, null);
                savePlayerStats(file, yaml, future);
            }
        }.runTaskAsynchronously(plugin);
        return future;
    }

    @Override
    public CompletableFuture<List<AuctionListing>> loadAuctionListings() {
        CompletableFuture<List<AuctionListing>> future = new CompletableFuture<>();
        new BukkitRunnable() {
            @Override
            public void run() {
                List<AuctionListing> listings = new ArrayList<>();
                YamlConfiguration yaml = YamlConfiguration.loadConfiguration(auctionsFile);
                ConfigurationSection section = yaml.getConfigurationSection("listings");
                if (section != null) {
                    for (String key : section.getKeys(false)) {
                        ItemStack item = section.getItemStack(key + ".item");
                        if (item == null) {
                            continue;
                        }
                        listings.add(new AuctionListing(
                            UUID.fromString(key),
                            UUID.fromString(section.getString(key + ".seller-id")),
                            section.getString(key + ".seller-name", "Unknown"),
                            item,
                            section.getDouble(key + ".price")
                        ));
                    }
                }
                future.complete(listings);
            }
        }.runTaskAsynchronously(plugin);
        return future;
    }

    @Override
    public CompletableFuture<Void> saveAuctionListing(AuctionListing listing) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        new BukkitRunnable() {
            @Override
            public void run() {
                YamlConfiguration yaml = YamlConfiguration.loadConfiguration(auctionsFile);
                String path = "listings." + listing.id();
                yaml.set(path + ".seller-id", listing.sellerId().toString());
                yaml.set(path + ".seller-name", listing.sellerName());
                yaml.set(path + ".item", listing.item());
                yaml.set(path + ".price", listing.price());
                try {
                    yaml.save(auctionsFile);
                    future.complete(null);
                } catch (IOException ex) {
                    future.completeExceptionally(ex);
                }
            }
        }.runTaskAsynchronously(plugin);
        return future;
    }

    @Override
    public CompletableFuture<Void> deleteAuctionListing(UUID listingId) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        new BukkitRunnable() {
            @Override
            public void run() {
                YamlConfiguration yaml = YamlConfiguration.loadConfiguration(auctionsFile);
                yaml.set("listings." + listingId, null);
                try {
                    yaml.save(auctionsFile);
                    future.complete(null);
                } catch (IOException ex) {
                    future.completeExceptionally(ex);
                }
            }
        }.runTaskAsynchronously(plugin);
        return future;
    }

    @Override
    public void close() {
    }

    private IslandData readIsland(File file, UUID ownerId) {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        IslandData data = new IslandData(ownerId);
        data.worldName(yaml.getString("world-name"));
        data.parcelIndex(yaml.getInt("parcel-index", -1));
        data.centerX(yaml.getInt("center.x", 0));
        data.centerY(yaml.getInt("center.y", 100));
        data.centerZ(yaml.getInt("center.z", 0));
        data.radius(yaml.getInt("radius", 75));
        data.expandNorth(yaml.getInt("bounds.north", data.radius()));
        data.expandSouth(yaml.getInt("bounds.south", data.radius()));
        data.expandEast(yaml.getInt("bounds.east", data.radius()));
        data.expandWest(yaml.getInt("bounds.west", data.radius()));
        data.visitMode(VisitMode.valueOf(yaml.getString("visit-mode", "FRIENDLY")));
        data.generatorLevel(yaml.getInt("generator-level", 1));
        data.addGeneratorXp(yaml.getLong("generator-xp", 0));
        data.achievementLevel(yaml.getInt("achievement-level", 0));
        for (String key : yaml.getConfigurationSection("waypoints") == null ? List.<String>of() : yaml.getConfigurationSection("waypoints").getKeys(false)) {
            int slot = Integer.parseInt(key);
            data.waypoints().put(slot, new int[] {
                yaml.getInt("waypoints." + key + ".x"),
                yaml.getInt("waypoints." + key + ".y"),
                yaml.getInt("waypoints." + key + ".z")
            });
        }
        for (String member : yaml.getStringList("trusted-members")) {
            data.trustedMembers().add(UUID.fromString(member));
        }
        return data;
    }

    private File playerStatsFile(UUID playerId) {
        return new File(playerStatsFolder, playerId + ".yml");
    }

    private YamlConfiguration loadPlayerStats(UUID playerId) {
        return YamlConfiguration.loadConfiguration(playerStatsFile(playerId));
    }

    private void savePlayerStats(File file, YamlConfiguration yaml, CompletableFuture<Void> future) {
        try {
            yaml.save(file);
            future.complete(null);
        } catch (IOException ex) {
            future.completeExceptionally(ex);
        }
    }
}
