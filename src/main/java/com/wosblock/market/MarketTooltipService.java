package com.wosblock.market;

import com.wosblock.WoSBlockPlugin;
import com.wosblock.market.MarketInfoService.AuctionLimit;
import com.wosblock.util.Text;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

public final class MarketTooltipService {
    private static final String MARKER_TEXT = "WoSBlock Market";
    private final MarketInfoService marketInfoService;
    private final NamespacedKey tooltipKey;

    public MarketTooltipService(WoSBlockPlugin plugin, MarketInfoService marketInfoService) {
        this.marketInfoService = marketInfoService;
        this.tooltipKey = new NamespacedKey(plugin, "market_tooltip");
    }

    public void apply(Player player) {
        PlayerInventory inventory = player.getInventory();
        for (ItemStack item : inventory.getContents()) {
            apply(item);
        }
        for (ItemStack item : inventory.getArmorContents()) {
            apply(item);
        }
        apply(inventory.getItemInOffHand());
    }

    public void remove(Player player) {
        PlayerInventory inventory = player.getInventory();
        for (ItemStack item : inventory.getContents()) {
            remove(item);
        }
        for (ItemStack item : inventory.getArmorContents()) {
            remove(item);
        }
        remove(inventory.getItemInOffHand());
    }

    private void apply(ItemStack item) {
        if (item == null || item.getType() == Material.AIR || !hasMarketInfo(item.getType())) {
            remove(item);
            return;
        }
        ItemMeta meta = item.getItemMeta();
        List<Component> lore = cleanedLore(meta);
        lore.add(Text.legacy("&8" + MARKER_TEXT));
        marketInfoService.blackMarketPrice(item.getType())
            .ifPresent(price -> lore.add(Text.legacy("&7Black Market: &f" + price + " coins")));
        marketInfoService.auctionLimit(item.getType())
            .ifPresent(limit -> lore.add(Text.legacy("&7Auction: &f" + limit.minPrice() + " - " + limit.maxPrice() + " coins")));
        meta.lore(lore);
        meta.getPersistentDataContainer().set(tooltipKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
    }

    private void remove(ItemStack item) {
        if (item == null || item.getType() == Material.AIR || !item.hasItemMeta()) {
            return;
        }
        ItemMeta meta = item.getItemMeta();
        if (!meta.getPersistentDataContainer().has(tooltipKey, PersistentDataType.BYTE)) {
            return;
        }
        meta.lore(cleanedLore(meta));
        meta.getPersistentDataContainer().remove(tooltipKey);
        item.setItemMeta(meta);
    }

    private boolean hasMarketInfo(Material material) {
        return marketInfoService.blackMarketPrice(material).isPresent() || marketInfoService.auctionLimit(material).isPresent();
    }

    private List<Component> cleanedLore(ItemMeta meta) {
        List<Component> source = meta.lore() == null ? List.of() : meta.lore();
        List<Component> cleaned = new ArrayList<>();
        for (Component line : source) {
            String plain = Text.plainText(line);
            if (plain.equals(MARKER_TEXT) || plain.startsWith("Black Market:") || plain.startsWith("Auction:")) {
                continue;
            }
            cleaned.add(line);
        }
        return cleaned;
    }
}
