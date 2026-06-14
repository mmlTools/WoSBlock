package com.wosblock.storage;

import com.wosblock.WoSBlockPlugin;
import com.wosblock.auction.AuctionListing;
import com.wosblock.island.IslandData;
import com.wosblock.island.VisitMode;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.stream.Collectors;
import org.bukkit.inventory.ItemStack;

public final class MySqlStorage implements Storage {
    private static final String MIGRATION_INDEX = "db/migrations/index.txt";

    private final WoSBlockPlugin plugin;
    private final HikariDataSource dataSource;
    private final CompletableFuture<Void> ready;

    public MySqlStorage(WoSBlockPlugin plugin) {
        this.plugin = plugin;
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(jdbcUrl());
        config.setUsername(plugin.getConfig().getString("mysql.username"));
        config.setPassword(plugin.getConfig().getString("mysql.password"));
        config.setMaximumPoolSize(plugin.getConfig().getInt("mysql.pool.maximum-pool-size", 10));
        config.setMinimumIdle(plugin.getConfig().getInt("mysql.pool.minimum-idle", 2));
        config.setConnectionTimeout(plugin.getConfig().getLong("mysql.pool.connection-timeout-ms", 10000));
        dataSource = new HikariDataSource(config);
        ready = runMigrations();
    }

    private String jdbcUrl() {
        String url = plugin.getConfig().getString("mysql.jdbc-url", "jdbc:mysql://localhost:3306/wosblock?useSSL=false&serverTimezone=UTC");
        String database = plugin.getConfig().getString("mysql.database", "").trim();
        if (database.isEmpty()) {
            return url;
        }

        int schemeEnd = url.indexOf("://");
        int pathStart = schemeEnd < 0 ? -1 : url.indexOf('/', schemeEnd + 3);
        if (pathStart < 0) {
            return url + "/" + database;
        }

        int queryStart = url.indexOf('?', pathStart);
        if (queryStart < 0) {
            return url.substring(0, pathStart + 1) + database;
        }
        return url.substring(0, pathStart + 1) + database + url.substring(queryStart);
    }

    private CompletableFuture<Void> runMigrations() {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                ensureMigrationTable(connection);
                Set<String> applied = appliedMigrations(connection);
                for (String migration : migrationIndex()) {
                    if (applied.contains(migration)) {
                        continue;
                    }
                    executeMigration(connection, migration);
                    recordMigration(connection, migration);
                    plugin.getLogger().info("Applied MySQL migration " + migration);
                }
                connection.commit();
            } catch (SQLException | IOException | RuntimeException ex) {
                connection.rollback();
                throw ex;
            }
            plugin.getLogger().info("MySQL migrations ready.");
            return CompletableFuture.completedFuture(null);
        } catch (SQLException | IOException ex) {
            plugin.getLogger().severe("Could not run MySQL migrations: " + ex.getMessage());
            return CompletableFuture.failedFuture(ex);
        }
    }

    private void ensureMigrationTable(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
             CREATE TABLE IF NOT EXISTS wos_schema_migrations (
               migration_id VARCHAR(191) PRIMARY KEY,
               applied_at BIGINT NOT NULL
             )
             """)) {
            statement.executeUpdate();
        }
    }

    private Set<String> appliedMigrations(Connection connection) throws SQLException {
        Set<String> applied = new HashSet<>();
        try (PreparedStatement statement = connection.prepareStatement("SELECT migration_id FROM wos_schema_migrations");
             ResultSet result = statement.executeQuery()) {
            while (result.next()) {
                applied.add(result.getString("migration_id"));
            }
        }
        return applied;
    }

    private List<String> migrationIndex() throws IOException {
        try (InputStream stream = plugin.getResource(MIGRATION_INDEX)) {
            if (stream == null) {
                throw new IOException("Missing migration index: " + MIGRATION_INDEX);
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                return reader.lines()
                    .map(String::trim)
                    .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                    .toList();
            }
        }
    }

    private void executeMigration(Connection connection, String migration) throws SQLException, IOException {
        String path = "db/migrations/" + migration;
        try (InputStream stream = plugin.getResource(path)) {
            if (stream == null) {
                throw new IOException("Missing migration file: " + path);
            }
            String sql = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            try (Statement statement = connection.createStatement()) {
                for (String command : sql.split(";")) {
                    String trimmed = command.trim();
                    if (!trimmed.isEmpty()) {
                        statement.execute(trimmed);
                    }
                }
            }
        }
    }

    private void recordMigration(Connection connection, String migration) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
             INSERT INTO wos_schema_migrations (migration_id, applied_at)
             VALUES (?, ?)
             """)) {
            statement.setString(1, migration);
            statement.setLong(2, System.currentTimeMillis());
            statement.executeUpdate();
        }
    }

    @Override
    public CompletableFuture<Optional<IslandData>> loadIsland(UUID ownerId) {
        CompletableFuture<Optional<IslandData>> future = new CompletableFuture<>();
        runWhenReady(future, () -> {
                try (Connection connection = dataSource.getConnection();
                     PreparedStatement statement = connection.prepareStatement("SELECT * FROM wos_islands WHERE owner_id = ?")) {
                    statement.setString(1, ownerId.toString());
                    try (ResultSet result = statement.executeQuery()) {
                        if (!result.next()) {
                            future.complete(Optional.empty());
                            return;
                        }
                        IslandData data = new IslandData(ownerId);
                        data.worldName(result.getString("world_name"));
                        data.parcelIndex(result.getInt("parcel_index"));
                        data.centerX(result.getInt("center_x"));
                        data.centerY(result.getInt("center_y"));
                        data.centerZ(result.getInt("center_z"));
                        data.radius(result.getInt("radius"));
                        data.expandNorth(result.getInt("expand_north"));
                        data.expandSouth(result.getInt("expand_south"));
                        data.expandEast(result.getInt("expand_east"));
                        data.expandWest(result.getInt("expand_west"));
                        data.visitMode(VisitMode.valueOf(result.getString("visit_mode")));
                        data.generatorLevel(result.getInt("generator_level"));
                        data.addGeneratorXp(result.getLong("generator_xp"));
                        data.achievementLevel(result.getInt("achievement_level"));
                        readWaypoints(result.getString("waypoints"), data);
                        String members = result.getString("trusted_members");
                        if (members != null && !members.isBlank()) {
                            Arrays.stream(members.split(","))
                                .filter(value -> !value.isBlank())
                                .map(UUID::fromString)
                                .forEach(data.trustedMembers()::add);
                        }
                        future.complete(Optional.of(data));
                    }
                } catch (SQLException ex) {
                    future.completeExceptionally(ex);
                }
        });
        return future;
    }

    @Override
    public CompletableFuture<List<IslandData>> loadAllIslands() {
        CompletableFuture<List<IslandData>> future = new CompletableFuture<>();
        runWhenReady(future, () -> {
                List<IslandData> islands = new ArrayList<>();
                try (Connection connection = dataSource.getConnection();
                     PreparedStatement statement = connection.prepareStatement("SELECT * FROM wos_islands");
                     ResultSet result = statement.executeQuery()) {
                    while (result.next()) {
                        UUID ownerId = UUID.fromString(result.getString("owner_id"));
                        IslandData data = new IslandData(ownerId);
                        data.worldName(result.getString("world_name"));
                        data.parcelIndex(result.getInt("parcel_index"));
                        data.centerX(result.getInt("center_x"));
                        data.centerY(result.getInt("center_y"));
                        data.centerZ(result.getInt("center_z"));
                        data.radius(result.getInt("radius"));
                        data.expandNorth(result.getInt("expand_north"));
                        data.expandSouth(result.getInt("expand_south"));
                        data.expandEast(result.getInt("expand_east"));
                        data.expandWest(result.getInt("expand_west"));
                        data.visitMode(VisitMode.valueOf(result.getString("visit_mode")));
                        data.generatorLevel(result.getInt("generator_level"));
                        data.addGeneratorXp(result.getLong("generator_xp"));
                        data.achievementLevel(result.getInt("achievement_level"));
                        readWaypoints(result.getString("waypoints"), data);
                        String members = result.getString("trusted_members");
                        if (members != null && !members.isBlank()) {
                            Arrays.stream(members.split(","))
                                .filter(value -> !value.isBlank())
                                .map(UUID::fromString)
                                .forEach(data.trustedMembers()::add);
                        }
                        islands.add(data);
                    }
                    future.complete(islands);
                } catch (SQLException ex) {
                    future.completeExceptionally(ex);
                }
        });
        return future;
    }

    @Override
    public CompletableFuture<Void> saveIsland(IslandData island) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        runWhenReady(future, () -> {
                String members = island.trustedMembers().stream().map(UUID::toString).collect(Collectors.joining(","));
                try (Connection connection = dataSource.getConnection();
                     PreparedStatement statement = connection.prepareStatement("""
                         INSERT INTO wos_islands (
                           owner_id, world_name, parcel_index, center_x, center_y, center_z, radius,
                           expand_north, expand_south, expand_east, expand_west,
                           visit_mode, generator_level, generator_xp, achievement_level, waypoints, trusted_members
                         )
                         VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                         ON DUPLICATE KEY UPDATE
                           world_name = VALUES(world_name),
                           parcel_index = VALUES(parcel_index),
                           center_x = VALUES(center_x),
                           center_y = VALUES(center_y),
                           center_z = VALUES(center_z),
                           radius = VALUES(radius),
                           expand_north = VALUES(expand_north),
                           expand_south = VALUES(expand_south),
                           expand_east = VALUES(expand_east),
                           expand_west = VALUES(expand_west),
                           visit_mode = VALUES(visit_mode),
                           generator_level = VALUES(generator_level),
                           generator_xp = VALUES(generator_xp),
                           achievement_level = VALUES(achievement_level),
                           waypoints = VALUES(waypoints),
                           trusted_members = VALUES(trusted_members)
                         """)) {
                    statement.setString(1, island.ownerId().toString());
                    statement.setString(2, island.worldName());
                    statement.setInt(3, island.parcelIndex());
                    statement.setInt(4, island.centerX());
                    statement.setInt(5, island.centerY());
                    statement.setInt(6, island.centerZ());
                    statement.setInt(7, island.radius());
                    statement.setInt(8, island.expandNorth());
                    statement.setInt(9, island.expandSouth());
                    statement.setInt(10, island.expandEast());
                    statement.setInt(11, island.expandWest());
                    statement.setString(12, island.visitMode().name());
                    statement.setInt(13, island.generatorLevel());
                    statement.setLong(14, island.generatorXp());
                    statement.setInt(15, island.achievementLevel());
                    statement.setString(16, writeWaypoints(island));
                    statement.setString(17, members);
                    statement.executeUpdate();
                    future.complete(null);
                } catch (SQLException ex) {
                    future.completeExceptionally(ex);
                }
        });
        return future;
    }

    private void readWaypoints(String value, IslandData data) {
        if (value == null || value.isBlank()) {
            return;
        }
        for (String entry : value.split("\\|")) {
            String[] parts = entry.split(":");
            if (parts.length != 4) {
                continue;
            }
            data.waypoints().put(Integer.parseInt(parts[0]), new int[] {
                Integer.parseInt(parts[1]),
                Integer.parseInt(parts[2]),
                Integer.parseInt(parts[3])
            });
        }
    }

    private String writeWaypoints(IslandData island) {
        return island.waypoints().entrySet().stream()
            .map(entry -> entry.getKey() + ":" + entry.getValue()[0] + ":" + entry.getValue()[1] + ":" + entry.getValue()[2])
            .collect(Collectors.joining("|"));
    }

    @Override
    public CompletableFuture<Integer> nextIslandIndex() {
        CompletableFuture<Integer> future = new CompletableFuture<>();
        runWhenReady(future, () -> {
                try (Connection connection = dataSource.getConnection();
                     PreparedStatement statement = connection.prepareStatement("SELECT COALESCE(MAX(parcel_index), -1) + 1 AS next_index FROM wos_islands");
                     ResultSet result = statement.executeQuery()) {
                    result.next();
                    future.complete(result.getInt("next_index"));
                } catch (SQLException ex) {
                    future.completeExceptionally(ex);
                }
        });
        return future;
    }

    @Override
    public CompletableFuture<Void> deleteIsland(UUID ownerId) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        runWhenReady(future, () -> {
                try (Connection connection = dataSource.getConnection();
                     PreparedStatement statement = connection.prepareStatement("DELETE FROM wos_islands WHERE owner_id = ?")) {
                    statement.setString(1, ownerId.toString());
                    statement.executeUpdate();
                    future.complete(null);
                } catch (SQLException ex) {
                    future.completeExceptionally(ex);
                }
        });
        return future;
    }

    @Override
    public CompletableFuture<Void> deleteIslandPlayerData(UUID playerId, String worldName) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        runWhenReady(future, () -> {
                try (Connection connection = dataSource.getConnection()) {
                    connection.setAutoCommit(false);
                    try {
                        try (PreparedStatement statement = connection.prepareStatement("DELETE FROM wos_balances WHERE player_id = ? AND world_name = ?")) {
                            statement.setString(1, playerId.toString());
                            statement.setString(2, worldName);
                            statement.executeUpdate();
                        }
                        try (PreparedStatement statement = connection.prepareStatement("DELETE FROM wos_quest_completions WHERE player_id = ?")) {
                            statement.setString(1, playerId.toString());
                            statement.executeUpdate();
                        }
                        try (PreparedStatement statement = connection.prepareStatement("DELETE FROM wos_quest_progress WHERE player_id = ?")) {
                            statement.setString(1, playerId.toString());
                            statement.executeUpdate();
                        }
                        connection.commit();
                        future.complete(null);
                    } catch (SQLException ex) {
                        connection.rollback();
                        future.completeExceptionally(ex);
                    }
                } catch (SQLException ex) {
                    future.completeExceptionally(ex);
                }
        });
        return future;
    }

    @Override
    public CompletableFuture<Double> loadBalance(UUID playerId, String worldName, double defaultBalance) {
        CompletableFuture<Double> future = new CompletableFuture<>();
        runWhenReady(future, () -> {
                try (Connection connection = dataSource.getConnection();
                     PreparedStatement statement = connection.prepareStatement("SELECT balance FROM wos_balances WHERE player_id = ? AND world_name = ?")) {
                    statement.setString(1, playerId.toString());
                    statement.setString(2, worldName);
                    try (ResultSet result = statement.executeQuery()) {
                        future.complete(result.next() ? result.getDouble("balance") : defaultBalance);
                    }
                } catch (SQLException ex) {
                    future.completeExceptionally(ex);
                }
        });
        return future;
    }

    @Override
    public CompletableFuture<Void> saveBalance(UUID playerId, String worldName, double balance) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        runWhenReady(future, () -> {
                try (Connection connection = dataSource.getConnection();
                     PreparedStatement statement = connection.prepareStatement("""
                         INSERT INTO wos_balances (player_id, world_name, balance)
                         VALUES (?, ?, ?)
                         ON DUPLICATE KEY UPDATE balance = VALUES(balance)
                         """)) {
                    statement.setString(1, playerId.toString());
                    statement.setString(2, worldName);
                    statement.setDouble(3, balance);
                    statement.executeUpdate();
                    future.complete(null);
                } catch (SQLException ex) {
                    future.completeExceptionally(ex);
                }
        });
        return future;
    }

    @Override
    public CompletableFuture<Map<String, Long>> loadQuestCompletions(UUID playerId) {
        CompletableFuture<Map<String, Long>> future = new CompletableFuture<>();
        runWhenReady(future, () -> {
                Map<String, Long> completions = new LinkedHashMap<>();
                try (Connection connection = dataSource.getConnection();
                     PreparedStatement statement = connection.prepareStatement("SELECT quest_id, completed_at FROM wos_quest_completions WHERE player_id = ?")) {
                    statement.setString(1, playerId.toString());
                    try (ResultSet result = statement.executeQuery()) {
                        while (result.next()) {
                            completions.put(result.getString("quest_id"), result.getLong("completed_at"));
                        }
                    }
                    future.complete(completions);
                } catch (SQLException ex) {
                    future.completeExceptionally(ex);
                }
        });
        return future;
    }

    @Override
    public CompletableFuture<Void> saveQuestCompletion(UUID playerId, String questId, long completedAt) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        runWhenReady(future, () -> {
                try (Connection connection = dataSource.getConnection();
                     PreparedStatement statement = connection.prepareStatement("""
                         INSERT INTO wos_quest_completions (player_id, quest_id, completed_at)
                         VALUES (?, ?, ?)
                         ON DUPLICATE KEY UPDATE completed_at = VALUES(completed_at)
                         """)) {
                    statement.setString(1, playerId.toString());
                    statement.setString(2, questId);
                    statement.setLong(3, completedAt);
                    statement.executeUpdate();
                    future.complete(null);
                } catch (SQLException ex) {
                    future.completeExceptionally(ex);
                }
        });
        return future;
    }

    @Override
    public CompletableFuture<Map<String, Integer>> loadQuestProgress(UUID playerId) {
        CompletableFuture<Map<String, Integer>> future = new CompletableFuture<>();
        runWhenReady(future, () -> {
                Map<String, Integer> progress = new LinkedHashMap<>();
                try (Connection connection = dataSource.getConnection();
                     PreparedStatement statement = connection.prepareStatement("SELECT quest_id, progress FROM wos_quest_progress WHERE player_id = ?")) {
                    statement.setString(1, playerId.toString());
                    try (ResultSet result = statement.executeQuery()) {
                        while (result.next()) {
                            progress.put(result.getString("quest_id"), result.getInt("progress"));
                        }
                    }
                    future.complete(progress);
                } catch (SQLException ex) {
                    future.completeExceptionally(ex);
                }
        });
        return future;
    }

    @Override
    public CompletableFuture<Void> saveQuestProgress(UUID playerId, String questId, int progress) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        runWhenReady(future, () -> {
                try (Connection connection = dataSource.getConnection();
                     PreparedStatement statement = connection.prepareStatement("""
                         INSERT INTO wos_quest_progress (player_id, quest_id, progress)
                         VALUES (?, ?, ?)
                         ON DUPLICATE KEY UPDATE progress = VALUES(progress)
                         """)) {
                    statement.setString(1, playerId.toString());
                    statement.setString(2, questId);
                    statement.setInt(3, progress);
                    statement.executeUpdate();
                    future.complete(null);
                } catch (SQLException ex) {
                    future.completeExceptionally(ex);
                }
        });
        return future;
    }

    @Override
    public CompletableFuture<Void> deleteQuestProgress(UUID playerId, String questId) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        runWhenReady(future, () -> {
                try (Connection connection = dataSource.getConnection();
                     PreparedStatement statement = connection.prepareStatement("DELETE FROM wos_quest_progress WHERE player_id = ? AND quest_id = ?")) {
                    statement.setString(1, playerId.toString());
                    statement.setString(2, questId);
                    statement.executeUpdate();
                    future.complete(null);
                } catch (SQLException ex) {
                    future.completeExceptionally(ex);
                }
        });
        return future;
    }

    @Override
    public CompletableFuture<List<AuctionListing>> loadAuctionListings() {
        CompletableFuture<List<AuctionListing>> future = new CompletableFuture<>();
        runWhenReady(future, () -> {
                List<AuctionListing> listings = new ArrayList<>();
                try (Connection connection = dataSource.getConnection();
                     PreparedStatement statement = connection.prepareStatement("SELECT * FROM wos_auction_listings ORDER BY created_at ASC");
                     ResultSet result = statement.executeQuery()) {
                    while (result.next()) {
                        listings.add(new AuctionListing(
                            UUID.fromString(result.getString("listing_id")),
                            UUID.fromString(result.getString("seller_id")),
                            result.getString("seller_name"),
                            deserializeItem(result.getBytes("item_blob")),
                            result.getDouble("price")
                        ));
                    }
                    future.complete(listings);
                } catch (SQLException | IllegalArgumentException ex) {
                    future.completeExceptionally(ex);
                }
        });
        return future;
    }

    @Override
    public CompletableFuture<Void> saveAuctionListing(AuctionListing listing) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        runWhenReady(future, () -> {
                try (Connection connection = dataSource.getConnection();
                     PreparedStatement statement = connection.prepareStatement("""
                         INSERT INTO wos_auction_listings (listing_id, seller_id, seller_name, item_blob, price, created_at)
                         VALUES (?, ?, ?, ?, ?, ?)
                         ON DUPLICATE KEY UPDATE
                           seller_id = VALUES(seller_id),
                           seller_name = VALUES(seller_name),
                           item_blob = VALUES(item_blob),
                           price = VALUES(price)
                         """)) {
                    statement.setString(1, listing.id().toString());
                    statement.setString(2, listing.sellerId().toString());
                    statement.setString(3, listing.sellerName());
                    statement.setBytes(4, serializeItem(listing.item()));
                    statement.setDouble(5, listing.price());
                    statement.setLong(6, System.currentTimeMillis());
                    statement.executeUpdate();
                    future.complete(null);
                } catch (SQLException | IllegalArgumentException ex) {
                    future.completeExceptionally(ex);
                }
        });
        return future;
    }

    @Override
    public CompletableFuture<Void> deleteAuctionListing(UUID listingId) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        runWhenReady(future, () -> {
                try (Connection connection = dataSource.getConnection();
                     PreparedStatement statement = connection.prepareStatement("DELETE FROM wos_auction_listings WHERE listing_id = ?")) {
                    statement.setString(1, listingId.toString());
                    statement.executeUpdate();
                    future.complete(null);
                } catch (SQLException ex) {
                    future.completeExceptionally(ex);
                }
        });
        return future;
    }

    @Override
    public void close() {
        dataSource.close();
    }

    private byte[] serializeItem(ItemStack item) {
        return item.serializeAsBytes();
    }

    private ItemStack deserializeItem(byte[] bytes) {
        return ItemStack.deserializeBytes(bytes);
    }

    private void runWhenReady(CompletableFuture<?> future, Runnable task) {
        ready.whenComplete((ignored, ex) -> {
            if (ex != null) {
                future.completeExceptionally(ex);
                return;
            }
            CompletableFuture.runAsync(() -> {
                try {
                    task.run();
                } catch (Throwable throwable) {
                    plugin.getLogger().warning("MySQL storage task failed: " + throwable.getMessage());
                    future.completeExceptionally(throwable);
                }
            }).exceptionally(throwable -> {
                Throwable cause = throwable instanceof CompletionException && throwable.getCause() != null
                    ? throwable.getCause()
                    : throwable;
                plugin.getLogger().warning("Could not schedule MySQL storage task: " + cause.getMessage());
                future.completeExceptionally(cause);
                return null;
            });
        });
    }
}
