package com.wosblock.auction;

import java.util.UUID;
import org.bukkit.inventory.ItemStack;

public record AuctionListing(UUID id, UUID sellerId, String sellerName, ItemStack item, double price) {
}
