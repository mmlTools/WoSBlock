package com.wosblock.market;

import com.wosblock.WoSBlockPlugin;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

public final class MarketInfoService {
    private final WoSBlockPlugin plugin;
    private BlackMarketOffer currentOffer;
    private long rotateAt;

    public MarketInfoService(WoSBlockPlugin plugin) {
        this.plugin = plugin;
    }

    public Optional<BlackMarketOffer> currentBlackMarketOffer() {
        if (currentOffer == null || System.currentTimeMillis() >= rotateAt) {
            rotateOffer();
        }
        return Optional.ofNullable(currentOffer);
    }

    public Optional<Double> blackMarketPrice(Material material) {
        ConfigurationSection section = plugin.extraConfig("black-market.yml")
            .getConfigurationSection("black-market.items." + material.name());
        return section == null ? Optional.empty() : Optional.of(section.getDouble("price"));
    }

    public Optional<AuctionLimit> auctionLimit(Material material) {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("auction-house.price-limits." + material.name());
        if (section == null) {
            return Optional.empty();
        }
        return Optional.of(new AuctionLimit(section.getDouble("min-price", 0), section.getDouble("max-price", Double.MAX_VALUE)));
    }

    public long secondsUntilRotation() {
        currentBlackMarketOffer();
        return Math.max(0, (rotateAt - System.currentTimeMillis()) / 1000L);
    }

    private void rotateOffer() {
        List<BlackMarketOffer> offers = readOffers();
        if (offers.isEmpty()) {
            currentOffer = null;
        } else {
            currentOffer = offers.get(ThreadLocalRandom.current().nextInt(offers.size()));
        }
        int minutes = plugin.extraConfig("black-market.yml").getInt("black-market.rotation-minutes", 30);
        rotateAt = System.currentTimeMillis() + Math.max(1, minutes) * 60_000L;
    }

    private List<BlackMarketOffer> readOffers() {
        List<BlackMarketOffer> offers = new ArrayList<>();
        ConfigurationSection items = plugin.extraConfig("black-market.yml").getConfigurationSection("black-market.items");
        if (items != null) {
            for (String materialName : items.getKeys(false)) {
                Material material = Material.matchMaterial(materialName);
                if (material != null) {
                    offers.add(new BlackMarketOffer(materialName, material, items.getDouble(materialName + ".price"), false));
                }
            }
        }

        ConfigurationSection spawners = plugin.extraConfig("black-market.yml").getConfigurationSection("black-market.spawners");
        if (spawners != null) {
            for (String entityType : spawners.getKeys(false)) {
                double chance = spawners.getDouble(entityType + ".appearance-chance", 0.01);
                if (ThreadLocalRandom.current().nextDouble() <= chance) {
                    offers.add(new BlackMarketOffer(entityType + " Spawner", Material.SPAWNER, spawners.getDouble(entityType + ".price"), true));
                }
            }
        }
        return offers;
    }

    public record BlackMarketOffer(String id, Material material, double price, boolean spawner) {
    }

    public record AuctionLimit(double minPrice, double maxPrice) {
    }
}
