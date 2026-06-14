package com.wosblock.auction;

import com.wosblock.WoSBlockPlugin;
import com.wosblock.economy.BalanceService;
import com.wosblock.storage.Storage;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public final class AuctionHouseService {
    private final WoSBlockPlugin plugin;
    private final BalanceService balanceService;
    private final Storage storage;
    private final List<AuctionListing> listings = new CopyOnWriteArrayList<>();

    public AuctionHouseService(WoSBlockPlugin plugin, BalanceService balanceService, Storage storage) {
        this.plugin = plugin;
        this.balanceService = balanceService;
        this.storage = storage;
        loadListings();
    }

    public String list(Player seller, ItemStack item, double price) {
        if (item == null || item.getType() == Material.AIR) {
            return "Hold the item you want to list.";
        }
        if (plugin.getConfig().getStringList("auction-house.blocked-materials").contains(item.getType().name())) {
            return "That item is blocked from the Auction House.";
        }
        int maxListings = plugin.getConfig().getInt("auction-house.max-listings-per-player", 5);
        long activeListings = listings.stream()
            .filter(listing -> listing.sellerId().equals(seller.getUniqueId()))
            .count();
        if (maxListings > 0 && activeListings >= maxListings) {
            return "You can only list " + maxListings + " auction items at once.";
        }
        ConfigurationSection limits = plugin.getConfig().getConfigurationSection("auction-house.price-limits." + item.getType().name());
        if (limits != null) {
            double min = limits.getDouble("min-price", 0);
            double max = limits.getDouble("max-price", Double.MAX_VALUE);
            if (price < min || price > max) {
                return "Price must be between " + min + " and " + max + ".";
            }
        }

        ItemStack listed = item.clone();
        seller.getInventory().setItemInMainHand(null);
        AuctionListing listing = new AuctionListing(UUID.randomUUID(), seller.getUniqueId(), seller.getName(), listed, price);
        listings.add(listing);
        storage.saveAuctionListing(listing).exceptionally(ex -> {
            plugin.getLogger().warning("Could not save auction listing: " + ex.getMessage());
            return null;
        });
        return "Listed " + listed.getAmount() + "x " + listed.getType().name() + " for " + price + ".";
    }

    public List<AuctionListing> listings() {
        return List.copyOf(listings);
    }

    public Optional<AuctionListing> listing(UUID listingId) {
        return listings.stream().filter(listing -> listing.id().equals(listingId)).findFirst();
    }

    public boolean buy(Player buyer, UUID listingId) {
        Optional<AuctionListing> optional = listing(listingId);
        if (optional.isEmpty()) {
            buyer.sendMessage("That listing is no longer available.");
            return false;
        }
        AuctionListing listing = optional.get();
        if (listing.sellerId().equals(buyer.getUniqueId())) {
            buyer.sendMessage("You cannot buy your own listing.");
            return false;
        }
        if (!balanceService.withdraw(buyer, listing.price())) {
            buyer.sendMessage("You do not have enough coins.");
            return false;
        }
        listings.remove(listing);
        storage.deleteAuctionListing(listing.id()).exceptionally(ex -> {
            plugin.getLogger().warning("Could not delete auction listing: " + ex.getMessage());
            return null;
        });
        buyer.getInventory().addItem(listing.item()).values().forEach(leftover -> buyer.getWorld().dropItemNaturally(buyer.getLocation(), leftover));
        Player seller = Bukkit.getPlayer(listing.sellerId());
        if (seller != null) {
            balanceService.deposit(seller, listing.price());
            seller.sendMessage("Your auction sold for " + listing.price() + " coins.");
        }
        buyer.sendMessage("Purchased auction for " + listing.price() + " coins.");
        return true;
    }

    public boolean cancel(Player seller, UUID listingId) {
        Optional<AuctionListing> optional = listing(listingId);
        if (optional.isEmpty()) {
            seller.sendMessage("That listing is no longer available.");
            return false;
        }
        AuctionListing listing = optional.get();
        if (!listing.sellerId().equals(seller.getUniqueId())) {
            seller.sendMessage("Only the seller can cancel this listing.");
            return false;
        }

        double fee = listing.price() * 0.10;
        if (!balanceService.withdraw(seller, fee)) {
            seller.sendMessage("You need " + fee + " coins to cancel this auction.");
            return false;
        }

        listings.remove(listing);
        storage.deleteAuctionListing(listing.id()).exceptionally(ex -> {
            plugin.getLogger().warning("Could not delete auction listing: " + ex.getMessage());
            return null;
        });
        seller.getInventory().addItem(listing.item()).values().forEach(leftover -> seller.getWorld().dropItemNaturally(seller.getLocation(), leftover));
        seller.sendMessage("Cancelled auction for a " + fee + " coin fee.");
        return true;
    }

    public CompletableFuture<Void> deleteSellerListings(UUID sellerId) {
        List<AuctionListing> removed = new ArrayList<>();
        for (AuctionListing listing : listings) {
            if (listing.sellerId().equals(sellerId) && listings.remove(listing)) {
                removed.add(listing);
            }
        }
        if (removed.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }
        CompletableFuture<?>[] deletes = removed.stream()
            .map(listing -> storage.deleteAuctionListing(listing.id()))
            .toArray(CompletableFuture[]::new);
        return CompletableFuture.allOf(deletes);
    }

    private void loadListings() {
        storage.loadAuctionListings().whenComplete((loaded, ex) -> {
            if (ex != null) {
                plugin.getLogger().warning("Could not load auction listings: " + ex.getMessage());
                return;
            }
            listings.clear();
            listings.addAll(loaded);
            plugin.getLogger().info("Loaded " + loaded.size() + " auction listings.");
        });
    }
}
