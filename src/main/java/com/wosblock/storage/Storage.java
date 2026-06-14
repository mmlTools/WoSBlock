package com.wosblock.storage;

import com.wosblock.auction.AuctionListing;
import com.wosblock.island.IslandData;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface Storage extends AutoCloseable {
    CompletableFuture<Optional<IslandData>> loadIsland(UUID ownerId);

    CompletableFuture<List<IslandData>> loadAllIslands();

    CompletableFuture<Void> saveIsland(IslandData island);

    CompletableFuture<Void> deleteIsland(UUID ownerId);

    CompletableFuture<Void> deleteIslandPlayerData(UUID playerId, String worldName);

    CompletableFuture<Integer> nextIslandIndex();

    CompletableFuture<Double> loadBalance(UUID playerId, String worldName, double defaultBalance);

    CompletableFuture<Void> saveBalance(UUID playerId, String worldName, double balance);

    CompletableFuture<Map<String, Long>> loadQuestCompletions(UUID playerId);

    CompletableFuture<Void> saveQuestCompletion(UUID playerId, String questId, long completedAt);

    CompletableFuture<Map<String, Integer>> loadQuestProgress(UUID playerId);

    CompletableFuture<Void> saveQuestProgress(UUID playerId, String questId, int progress);

    CompletableFuture<Void> deleteQuestProgress(UUID playerId, String questId);

    CompletableFuture<List<AuctionListing>> loadAuctionListings();

    CompletableFuture<Void> saveAuctionListing(AuctionListing listing);

    CompletableFuture<Void> deleteAuctionListing(UUID listingId);

    @Override
    void close();
}
