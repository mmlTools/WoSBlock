package com.wosblock.menu;

import com.wosblock.WoSBlockPlugin;
import com.wosblock.auction.AuctionHouseService;
import com.wosblock.auction.AuctionListing;
import com.wosblock.economy.BalanceService;
import com.wosblock.market.MarketInfoService;
import com.wosblock.market.MarketInfoService.BlackMarketOffer;
import com.wosblock.market.MarketTooltipService;
import com.wosblock.item.ScrollService;
import com.wosblock.quest.QuestService;
import com.wosblock.util.Text;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public final class MenuService {
    private static final String AUCTION_TITLE = "Auctioneer";
    private static final String THIEF_TITLE = "Black Market";
    private static final String CLERK_TITLE = "Clerk Quests";

    private final WoSBlockPlugin plugin;
    private final BalanceService balanceService;
    private final AuctionHouseService auctionHouseService;
    private final QuestService questService;
    private final MarketInfoService marketInfoService;
    private final MarketTooltipService marketTooltipService;
    private final ScrollService scrollService;
    private final Map<Integer, UUID> visibleAuctionSlots = new HashMap<>();
    private final Map<UUID, Map<Integer, String>> visibleQuestSlots = new HashMap<>();
    private final Map<UUID, Integer> questPages = new HashMap<>();
    private final Map<Integer, BlackMarketOffer> visibleBlackMarketSlots = new HashMap<>();

    public MenuService(WoSBlockPlugin plugin, BalanceService balanceService, AuctionHouseService auctionHouseService,
                       QuestService questService, MarketInfoService marketInfoService, MarketTooltipService marketTooltipService,
                       ScrollService scrollService) {
        this.plugin = plugin;
        this.balanceService = balanceService;
        this.auctionHouseService = auctionHouseService;
        this.questService = questService;
        this.marketInfoService = marketInfoService;
        this.marketTooltipService = marketTooltipService;
        this.scrollService = scrollService;
    }

    public void openNpcMenu(Player player, String npcType) {
        switch (npcType.toLowerCase()) {
            case "auctioneer" -> openAuctioneer(player);
            case "thief" -> openThief(player);
            case "clerk" -> openClerk(player);
            default -> player.sendMessage("Unknown NPC type: " + npcType);
        }
    }

    public void openAuctioneer(Player player) {
        Inventory inventory = Bukkit.createInventory(null, 54, Text.plain(AUCTION_TITLE));
        visibleAuctionSlots.clear();
        int slot = 0;
        for (AuctionListing listing : auctionHouseService.listings()) {
            if (slot >= 45) {
                break;
            }
            ItemStack display = listing.item().clone();
            ItemMeta meta = display.getItemMeta();
            String action = listing.sellerId().equals(player.getUniqueId()) ? "Left-click to cancel" : "Click to buy";
            meta.lore(Text.lore("Seller: " + listing.sellerName(), "Price: " + listing.price() + " coins", action));
            display.setItemMeta(meta);
            inventory.setItem(slot, display);
            visibleAuctionSlots.put(slot, listing.id());
            slot++;
        }
        inventory.setItem(49, named(Material.GOLD_INGOT, "Balance: " + balanceService.balance(player) + " coins"));
        player.openInventory(inventory);
    }

    public void openThief(Player player) {
        Inventory inventory = Bukkit.createInventory(null, 54, Text.plain(THIEF_TITLE));
        visibleBlackMarketSlots.clear();
        marketInfoService.currentBlackMarketOffer().ifPresentOrElse(offer -> {
            inventory.setItem(22, named(
                offer.material(),
                offer.id(),
                "Black Market price: " + offer.price() + " coins",
                "Offer rotates in " + formatSeconds(marketInfoService.secondsUntilRotation())
            ));
            visibleBlackMarketSlots.put(22, offer);
        }, () -> inventory.setItem(22, named(Material.BARRIER, "No black market offer configured")));
        inventory.setItem(31, named(Material.CLOCK, "Next offer in " + formatSeconds(marketInfoService.secondsUntilRotation())));
        inventory.setItem(49, named(Material.GOLD_INGOT, "Balance: " + balanceService.balance(player) + " coins"));
        player.openInventory(inventory);
    }

    public void openClerk(Player player) {
        openClerk(player, questPages.getOrDefault(player.getUniqueId(), 0));
    }

    private void openClerk(Player player, int requestedPage) {
        List<String> quests = new ArrayList<>(questService.questIds());
        int pageSize = 18;
        int maxPage = Math.max(0, (quests.size() - 1) / pageSize);
        int page = Math.max(0, Math.min(requestedPage, maxPage));
        questPages.put(player.getUniqueId(), page);

        Inventory inventory = Bukkit.createInventory(null, 27, Text.plain(CLERK_TITLE));
        Map<Integer, String> playerQuestSlots = new HashMap<>();
        visibleQuestSlots.put(player.getUniqueId(), playerQuestSlots);
        int slot = 0;
        int start = page * pageSize;
        int end = Math.min(quests.size(), start + pageSize);
        for (int index = start; index < end; index++) {
            String questId = quests.get(index);
            inventory.setItem(slot, questService.displayItem(player, questId));
            playerQuestSlots.put(slot, questId);
            slot++;
        }
        if (page > 0) {
            inventory.setItem(18, named(Material.ARROW, "Previous Page"));
        }
        inventory.setItem(22, named(Material.BOOK, "Active quests: " + questService.activeCount(player)));
        inventory.setItem(23, named(Material.MAP, "Page " + (page + 1) + "/" + (maxPage + 1)));
        if (page < maxPage) {
            inventory.setItem(26, named(Material.ARROW, "Next Page"));
        }
        player.openInventory(inventory);
    }

    public boolean handleClick(Player player, String title, int slot, ItemStack clicked, ClickType click) {
        if (title.equals(AUCTION_TITLE)) {
            UUID listingId = visibleAuctionSlots.get(slot);
            if (listingId != null) {
                auctionHouseService.listing(listingId).ifPresentOrElse(listing -> {
                    if (listing.sellerId().equals(player.getUniqueId()) && click == ClickType.LEFT) {
                        auctionHouseService.cancel(player, listingId);
                    } else {
                        auctionHouseService.buy(player, listingId);
                    }
                }, () -> player.sendMessage("That listing is no longer available."));
                openAuctioneer(player);
            }
            return true;
        }
        if (title.equals(THIEF_TITLE)) {
            buyBlackMarketItem(player, slot, clicked);
            openThief(player);
            return true;
        }
        if (title.equals(CLERK_TITLE)) {
            if (slot == 18) {
                openClerk(player, questPages.getOrDefault(player.getUniqueId(), 0) - 1);
                return true;
            }
            if (slot == 26) {
                openClerk(player, questPages.getOrDefault(player.getUniqueId(), 0) + 1);
                return true;
            }
            String questId = visibleQuestSlots.getOrDefault(player.getUniqueId(), Map.of()).get(slot);
            if (questId != null) {
                questService.acceptOrClaim(player, questId);
                openClerk(player);
            }
            return true;
        }
        return false;
    }

    private void buyBlackMarketItem(Player player, int slot, ItemStack clicked) {
        if (clicked == null || clicked.getType() == Material.AIR || clicked.getType() == Material.GOLD_INGOT) {
            return;
        }
        BlackMarketOffer offer = visibleBlackMarketSlots.get(slot);
        if (offer == null) {
            return;
        }
        double price = offer.price() * scrollService.merchantDiscountMultiplier(player);
        if (!balanceService.withdraw(player, price)) {
            player.sendMessage("You do not have enough coins.");
            return;
        }
        ItemStack purchased = offer.spawner() ? named(Material.SPAWNER, offer.id() + " Spawner") : new ItemStack(offer.material());
        player.getInventory().addItem(purchased).values().forEach(leftover -> player.getWorld().dropItemNaturally(player.getLocation(), leftover));
        marketTooltipService.apply(player);
        player.updateInventory();
        player.sendMessage("Bought " + offer.id() + " for " + price + " coins.");
    }

    private ItemStack named(Material material, String name) {
        return named(material, name, new String[0]);
    }

    private ItemStack named(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Text.plain(name));
        if (lore.length > 0) {
            meta.lore(Text.lore(lore));
        }
        item.setItemMeta(meta);
        return item;
    }

    private String formatSeconds(long seconds) {
        long minutes = seconds / 60L;
        long remainder = seconds % 60L;
        return minutes + "m " + remainder + "s";
    }
}
