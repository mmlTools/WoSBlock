package com.wosblock.island;

import com.wosblock.WoSBlockPlugin;
import com.wosblock.item.ItemFactory;
import com.wosblock.storage.Storage;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.scheduler.BukkitRunnable;

public final class IslandService {
    private final WoSBlockPlugin plugin;
    private final Storage storage;
    private final ItemFactory itemFactory;
    private final Map<UUID, IslandData> islandCache = new ConcurrentHashMap<>();
    private final Set<UUID> clearingIslands = ConcurrentHashMap.newKeySet();
    private final IslandBuilder islandBuilder;
    private final World islandWorld;
    private CompletableFuture<Void> preloadFuture = CompletableFuture.completedFuture(null);
    private BiFunction<Player, IslandData, CompletableFuture<Void>> islandDataCleanup =
        (player, island) -> CompletableFuture.completedFuture(null);

    public IslandService(WoSBlockPlugin plugin, Storage storage) {
        this.plugin = plugin;
        this.storage = storage;
        this.itemFactory = new ItemFactory(plugin);
        this.islandWorld = loadIslandWorld();
        this.islandBuilder = new IslandBuilder(plugin, this);
        this.preloadFuture = preloadIslands();
    }

    public Optional<IslandData> islandAt(Location location) {
        if (location == null || location.getWorld() == null) {
            return Optional.empty();
        }
        for (IslandData island : islandCache.values()) {
            if (island.contains(location)) {
                return Optional.of(island);
            }
        }
        return Optional.empty();
    }

    public IslandData islandFor(Player player) {
        IslandData data = islandCache.computeIfAbsent(player.getUniqueId(), IslandData::new);
        storage.loadIsland(player.getUniqueId()).thenAccept(optional -> optional.ifPresent(loaded -> islandCache.put(player.getUniqueId(), loaded)));
        return data;
    }

    public Optional<IslandData> cachedIsland(UUID ownerId) {
        return Optional.ofNullable(islandCache.get(ownerId));
    }

    public void setIslandDataCleanup(BiFunction<Player, IslandData, CompletableFuture<Void>> islandDataCleanup) {
        this.islandDataCleanup = islandDataCleanup == null
            ? (player, island) -> CompletableFuture.completedFuture(null)
            : islandDataCleanup;
    }

    public boolean isOwnerOrTrusted(Player player, IslandData island) {
        return island.ownerId().equals(player.getUniqueId()) || island.trustedMembers().contains(player.getUniqueId());
    }

    public void startIsland(Player player) {
        if (clearingIslands.contains(player.getUniqueId())) {
            player.sendMessage("Your old island is still being cleared. Try again in a moment.");
            return;
        }
        player.sendMessage("Preparing your island...");
        preloadFuture.thenCompose(ignored -> storage.loadIsland(player.getUniqueId())).thenAccept(optional -> {
            if (optional.isPresent()) {
                IslandData loaded = optional.get();
                islandCache.put(player.getUniqueId(), loaded);
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (isIslandMissing(loaded)) {
                        islandBuilder.build(loaded);
                    }
                    player.teleport(loaded.spawnLocation());
                    player.sendMessage("Welcome back to your island.");
                });
                return;
            }

            Bukkit.getScheduler().runTask(plugin, () -> {
                int index = nextAvailableParcelIndex();
                IslandData created = createIslandData(player.getUniqueId(), index);
                islandCache.put(player.getUniqueId(), created);
                islandBuilder.build(created);
                save(created);
                player.teleport(created.spawnLocation());
                player.sendMessage("Island started. Your starter chest contains the Auctioneer, Thief, and Clerk eggs.");
            });
        }).exceptionally(ex -> {
            plugin.getLogger().warning("Could not load island for " + player.getName() + ": " + ex.getMessage());
            player.sendMessage("Could not load your island. Check the server console.");
            return null;
        });
    }

    public void leaveIsland(Player player) {
        player.teleport(returnLocation());
        player.setWorldBorder(null);
        player.sendMessage("Returned to the normal world.");
    }

    public boolean clearIsland(Player player) {
        IslandData island = islandCache.get(player.getUniqueId());
        if (island == null) {
            player.sendMessage("You do not have a loaded island to clear.");
            return false;
        }
        if (!island.ownerId().equals(player.getUniqueId())) {
            player.sendMessage("Only the true island owner can clear this island.");
            return false;
        }

        clearingIslands.add(player.getUniqueId());
        leaveIsland(player);
        islandCache.remove(player.getUniqueId());
        clearIslandEntities(island);
        CompletableFuture<Void> deleteFuture = storage.deleteIsland(player.getUniqueId());
        CompletableFuture<Void> cleanupFuture = islandDataCleanup.apply(player, island);
        CompletableFuture<Void> clearFuture = clearIslandBlocks(island);
        CompletableFuture.allOf(deleteFuture, cleanupFuture, clearFuture).whenComplete((ignored, ex) -> Bukkit.getScheduler().runTask(plugin, () -> {
            clearingIslands.remove(player.getUniqueId());
            if (ex != null) {
                islandCache.put(player.getUniqueId(), island);
                plugin.getLogger().warning("Could not fully clear island for " + player.getName() + ": " + ex.getMessage());
                player.sendMessage("Could not fully clear your island. Check the server console.");
                return;
            }
            player.sendMessage("Island cleared. You can start a fresh island with /is start.");
        }));
        player.sendMessage("Island data is being deleted and the old island is being cleared.");
        return true;
    }

    public boolean rebuildIsland(Player player) {
        IslandData island = islandCache.get(player.getUniqueId());
        if (island == null) {
            player.sendMessage("You do not have a loaded island to rebuild.");
            return false;
        }
        if (!island.ownerId().equals(player.getUniqueId())) {
            player.sendMessage("Only the true island owner can rebuild this island.");
            return false;
        }

        islandBuilder.build(island);
        player.teleport(island.spawnLocation());
        player.sendMessage("Island rebuilt with the starter RPG layout.");
        return true;
    }

    public boolean canManageNpc(Player player, Location location) {
        return islandAt(location)
            .map(island -> isOwnerOrTrusted(player, island))
            .orElse(true);
    }

    public void trust(IslandData island, UUID trustedPlayer) {
        island.trustedMembers().add(trustedPlayer);
        save(island);
    }

    public VisitMode cycleVisitMode(IslandData island) {
        VisitMode[] values = VisitMode.values();
        VisitMode next = values[(island.visitMode().ordinal() + 1) % values.length];
        island.visitMode(next);
        save(island);
        return next;
    }

    public void save(IslandData island) {
        storage.saveIsland(island).exceptionally(ex -> {
            plugin.getLogger().warning("Could not save island " + island.ownerId() + ": " + ex.getMessage());
            return null;
        });
    }

    private CompletableFuture<Void> preloadIslands() {
        return storage.loadAllIslands().thenAccept(islands -> {
            for (IslandData island : islands) {
                islandCache.put(island.ownerId(), island);
            }
            plugin.getLogger().info("Loaded " + islands.size() + " island records into memory.");
        }).exceptionally(ex -> {
            plugin.getLogger().warning("Could not preload islands: " + ex.getMessage());
            return null;
        });
    }

    private int nextAvailableParcelIndex() {
        int next = 0;
        for (IslandData island : islandCache.values()) {
            next = Math.max(next, island.parcelIndex() + 1);
        }
        return next;
    }

    public IslandData initializeNewIsland(Player owner, Location islandSpawn) {
        IslandData data = new IslandData(owner.getUniqueId());
        islandCache.put(owner.getUniqueId(), data);

        Block chestBlock = islandSpawn.clone().add(1, 0, 0).getBlock();
        chestBlock.setType(Material.CHEST, false);
        if (chestBlock.getState() instanceof Chest chest) {
            addStartingNpcEggs(chest);
        }

        placeGuestBook(islandSpawn.clone().add(-1, 0, 0).getBlock());
        save(data);
        return data;
    }

    private IslandData createIslandData(UUID ownerId, int parcelIndex) {
        int[] parcel = parcelCoordinates(parcelIndex);
        int spacing = plugin.getConfig().getInt("islands.parcel-spacing", 512);
        IslandData data = new IslandData(ownerId);
        data.worldName(islandWorld.getName());
        data.parcelIndex(parcelIndex);
        data.centerX(parcel[0] * spacing);
        data.centerY(plugin.getConfig().getInt("islands.start-y", 100));
        data.centerZ(parcel[1] * spacing);
        int halfSize = plugin.getConfig().getInt("islands.default-half-size", plugin.getConfig().getInt("islands.default-radius", 75));
        data.radius(halfSize);
        data.expandNorth(halfSize);
        data.expandSouth(halfSize);
        data.expandEast(halfSize);
        data.expandWest(halfSize);
        return data;
    }

    public boolean expandSide(IslandData island, org.bukkit.block.BlockFace direction, int amount) {
        int spacing = plugin.getConfig().getInt("islands.parcel-spacing", 512);
        int safeParcelMax = Math.max(1, (spacing / 2) - 16);
        int max = Math.min(plugin.getConfig().getInt("islands.max-half-size", 240), safeParcelMax);
        int current = switch (direction) {
            case NORTH -> island.expandNorth();
            case SOUTH -> island.expandSouth();
            case EAST -> island.expandEast();
            case WEST -> island.expandWest();
            default -> island.radius();
        };
        if (current >= max) {
            return false;
        }
        int expanded = Math.min(max, current + Math.max(1, amount));
        switch (direction) {
            case NORTH -> island.expandNorth(expanded);
            case SOUTH -> island.expandSouth(expanded);
            case EAST -> island.expandEast(expanded);
            case WEST -> island.expandWest(expanded);
            default -> island.radius(expanded);
        }
        save(island);
        return true;
    }

    private int[] parcelCoordinates(int index) {
        return new int[] { Math.max(0, index), 0 };
    }

    private boolean isIslandMissing(IslandData island) {
        return island.centerLocation().getBlock().getType() == Material.AIR;
    }

    private Location returnLocation() {
        String returnWorldName = plugin.getConfig().getString("islands.return-world", "world");
        World returnWorld = Bukkit.getWorld(returnWorldName);
        if (returnWorld == null || returnWorld.getName().equals(islandWorld.getName())) {
            returnWorld = Bukkit.getWorlds().stream()
                .filter(world -> !world.getName().equals(islandWorld.getName()))
                .findFirst()
                .orElse(Bukkit.getWorlds().getFirst());
        }
        return returnWorld.getSpawnLocation().add(0.5, 0.0, 0.5);
    }

    private CompletableFuture<Void> clearIslandBlocks(IslandData island) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        World world = Bukkit.getWorld(island.worldName());
        if (world == null) {
            future.complete(null);
            return future;
        }
        int minX = island.centerX() - island.expandWest();
        int maxX = island.centerX() + island.expandEast();
        int minZ = island.centerZ() - island.expandNorth();
        int maxZ = island.centerZ() + island.expandSouth();
        int minY = Math.max(world.getMinHeight(), island.centerY() + plugin.getConfig().getInt("islands.clear-min-y-offset", -64));
        int maxY = Math.min(world.getMaxHeight() - 1, island.centerY() + plugin.getConfig().getInt("islands.clear-max-y-offset", 128));

        new BukkitRunnable() {
            private int x = minX;
            private int y = minY;
            private int z = minZ;

            @Override
            public void run() {
                int processed = 0;
                while (processed < 5000) {
                    world.getBlockAt(x, y, z).setType(Material.AIR, false);
                    processed++;

                    z++;
                    if (z > maxZ) {
                        z = minZ;
                        y++;
                    }
                    if (y > maxY) {
                        y = minY;
                        x++;
                    }
                    if (x > maxX) {
                        future.complete(null);
                        cancel();
                        return;
                    }
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
        return future;
    }

    private void clearIslandEntities(IslandData island) {
        World world = Bukkit.getWorld(island.worldName());
        if (world == null) {
            return;
        }
        Location center = island.centerLocation();
        double radiusX = Math.max(island.expandEast(), island.expandWest()) + 8.0;
        double radiusZ = Math.max(island.expandNorth(), island.expandSouth()) + 8.0;
        double radiusY = Math.max(16.0, plugin.getConfig().getInt("islands.clear-max-y-offset", 128));
        for (Entity entity : world.getNearbyEntities(center, radiusX, radiusY, radiusZ)) {
            if (!(entity instanceof Player) && island.contains(entity.getLocation())) {
                entity.remove();
            }
        }
    }

    private World loadIslandWorld() {
        String worldName = plugin.getConfig().getString("islands.world-name", "world_wosblock");
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            world = new WorldCreator(worldName)
                .generator(new VoidWorldGenerator())
                .generateStructures(false)
                .createWorld();
        }
        if (world != null) {
            world.setSpawnLocation(0, plugin.getConfig().getInt("islands.start-y", 100), 0);
            world.setGameRule(GameRule.DO_MOB_SPAWNING, false);
            world.setGameRule(GameRule.DO_PATROL_SPAWNING, false);
            world.setGameRule(GameRule.DO_TRADER_SPAWNING, false);
        }
        return world;
    }

    public void addStartingNpcEggs(Chest chest) {
        Inventory inventory = chest.getInventory();
        ConfigurationSection eggs = plugin.getConfig().getConfigurationSection("npcs.eggs");
        if (eggs == null) {
            return;
        }
        for (String type : eggs.getKeys(false)) {
            Material material = Material.matchMaterial(eggs.getString(type + ".material", "VILLAGER_SPAWN_EGG"));
            if (material == null) {
                material = Material.VILLAGER_SPAWN_EGG;
            }
            inventory.addItem(itemFactory.npcEgg(material, type, eggs.getString(type + ".display-name", type)));
        }
    }

    public void placeGuestBook(Block block) {
        block.setType(Material.ENCHANTING_TABLE, false);
    }
}
