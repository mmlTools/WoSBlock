package com.wosblock.item;

import com.wosblock.WoSBlockPlugin;
import com.wosblock.generator.CobblestoneGeneratorManager;
import com.wosblock.island.IslandData;
import com.wosblock.island.IslandService;
import com.wosblock.util.Text;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Chunk;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Chest;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.block.Hopper;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public final class ScrollService {
    private final WoSBlockPlugin plugin;
    private final IslandService islandService;
    private final CobblestoneGeneratorManager generatorManager;
    private final CustomItemKeys keys;
    private final Map<UUID, Long> voidFallProtectionUntil = new ConcurrentHashMap<>();
    private final Map<UUID, Long> flyUntil = new ConcurrentHashMap<>();
    private final Map<UUID, Long> magnetUntil = new ConcurrentHashMap<>();
    private final Map<UUID, Long> expressoUntil = new ConcurrentHashMap<>();
    private final Map<UUID, Long> aquaticBaitUntil = new ConcurrentHashMap<>();
    private final Map<UUID, Long> merchantDiscountUntil = new ConcurrentHashMap<>();
    private final Map<UUID, Long> trophyBoosterUntil = new ConcurrentHashMap<>();
    private final Map<UUID, Long> platformBuilderUntil = new ConcurrentHashMap<>();
    private final Map<UUID, Long> hopperLinkUntil = new ConcurrentHashMap<>();
    private final Map<UUID, Block> platformFirstPoints = new ConcurrentHashMap<>();
    private final Map<UUID, Location> hopperLinkChests = new ConcurrentHashMap<>();
    private final Map<String, Long> fertilizerChunks = new ConcurrentHashMap<>();
    private final Map<Location, WirelessLink> hopperLinks = new ConcurrentHashMap<>();

    public ScrollService(WoSBlockPlugin plugin, IslandService islandService, CobblestoneGeneratorManager generatorManager, CustomItemKeys keys) {
        this.plugin = plugin;
        this.islandService = islandService;
        this.generatorManager = generatorManager;
        this.keys = keys;
    }

    public ItemStack createScroll(String id, int amount) {
        ItemStack item = new ItemStack(Material.PAPER, Math.max(1, amount));
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Text.legacy("&b" + readableName(id) + " Scroll"));
        meta.lore(Text.lore("&7Right-click to use."));
        meta.getPersistentDataContainer().set(keys.scrollKey(), PersistentDataType.STRING, id);
        item.setItemMeta(meta);
        return item;
    }

    public boolean use(Player player, ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        String id = item.getItemMeta().getPersistentDataContainer().get(keys.scrollKey(), PersistentDataType.STRING);
        if (id == null) {
            return false;
        }

        boolean consumed = switch (id) {
            case "platform-builder" -> activatePlatformBuilder(player);
            case "cobble-generator-xp" -> useGeneratorXp(player);
            case "fly" -> fly(player);
            case "repair" -> repairHeldItem(player);
            case "feed-heal" -> feedAndHeal(player);
            case "haste-speed" -> hasteSpeed(player);
            case "magnet" -> timed(player, magnetUntil, "magnet", "Magnet enabled");
            case "hopper-wireless-link" -> activateHopperLink(player);
            case "chunk-fertilizer" -> fertilizeChunk(player);
            case "mob-spawner-booster" -> boostSpawner(player);
            case "expresso-mining" -> timed(player, expressoUntil, "expresso-mining", "Expresso mining enabled");
            case "spawner-silencer" -> silenceSpawner(player);
            case "aquatic-bait" -> timed(player, aquaticBaitUntil, "aquatic-bait", "Aquatic bait enabled");
            case "merchant-discount" -> timed(player, merchantDiscountUntil, "merchant-discount", "Merchant discount enabled");
            case "trophy-booster" -> timed(player, trophyBoosterUntil, "trophy-booster", "Trophy booster enabled");
            case "void-fall-negation" -> voidFallNegation(player);
            case "clear-weather" -> clearWeather(player);
            case "minion-fuel" -> minionFuel(player);
            case "scroll-of-the-deep" -> scrollOfTheDeep(player);
            case "island-boundary-expander" -> expandBoundary(player);
            default -> {
                player.sendMessage("Unknown scroll: " + id);
                yield false;
            }
        };
        if (consumed) {
            item.setAmount(item.getAmount() - 1);
        }
        return true;
    }

    public boolean handlePlatformSelection(Player player, Block block, boolean firstPoint) {
        if (!active(platformBuilderUntil, player.getUniqueId()) || block == null) {
            return false;
        }
        IslandData island = islandService.islandAt(block.getLocation()).orElse(null);
        if (island == null || !islandService.isOwnerOrTrusted(player, island)) {
            player.sendMessage("Select blocks on your island.");
            return true;
        }
        if (firstPoint) {
            platformFirstPoints.put(player.getUniqueId(), block);
            player.sendMessage("Platform point A selected.");
            return true;
        }
        Block first = platformFirstPoints.remove(player.getUniqueId());
        if (first == null || !first.getWorld().equals(block.getWorld())) {
            player.sendMessage("Select point A first.");
            return true;
        }
        buildPlatform(player, first, block);
        platformBuilderUntil.remove(player.getUniqueId());
        return true;
    }

    public boolean handleHopperLinkSelection(Player player, Block block) {
        if (!active(hopperLinkUntil, player.getUniqueId()) || block == null) {
            return false;
        }
        IslandData island = islandService.islandAt(block.getLocation()).orElse(null);
        if (island == null || !islandService.isOwnerOrTrusted(player, island)) {
            player.sendMessage("Link containers on your island.");
            return true;
        }
        if (block.getState() instanceof Chest) {
            hopperLinkChests.put(player.getUniqueId(), block.getLocation());
            player.sendMessage("Linked chest selected. Right-click a hopper.");
            return true;
        }
        if (block.getState() instanceof Hopper) {
            Location chest = hopperLinkChests.remove(player.getUniqueId());
            if (chest == null) {
                player.sendMessage("Right-click the destination chest first.");
                return true;
            }
            int seconds = duration("hopper-wireless-link", 7200);
            hopperLinks.put(block.getLocation(), new WirelessLink(chest, System.currentTimeMillis() + seconds * 1000L));
            hopperLinkUntil.remove(player.getUniqueId());
            player.sendMessage("Hopper wirelessly linked for " + seconds + " seconds.");
            return true;
        }
        player.sendMessage("Right-click a chest, then a hopper.");
        return true;
    }

    public boolean hasVoidFallProtection(Player player) {
        return active(voidFallProtectionUntil, player.getUniqueId());
    }

    public boolean hasFly(Player player) {
        return active(flyUntil, player.getUniqueId());
    }

    public boolean hasMagnet(Player player) {
        return active(magnetUntil, player.getUniqueId());
    }

    public boolean hasExpresso(Player player) {
        return active(expressoUntil, player.getUniqueId());
    }

    public boolean hasAquaticBait(Player player) {
        return active(aquaticBaitUntil, player.getUniqueId());
    }

    public boolean hasMerchantDiscount(Player player) {
        return active(merchantDiscountUntil, player.getUniqueId());
    }

    public boolean hasTrophyBooster(Player player) {
        return active(trophyBoosterUntil, player.getUniqueId());
    }

    public boolean isFertilized(Chunk chunk) {
        return fertilizerChunks.getOrDefault(chunkKey(chunk), 0L) > System.currentTimeMillis();
    }

    public Location linkedChest(Location hopper) {
        WirelessLink link = hopperLinks.get(hopper);
        if (link == null || link.expiresAt() <= System.currentTimeMillis()) {
            hopperLinks.remove(hopper);
            return null;
        }
        return link.chest();
    }

    public double merchantDiscountMultiplier(Player player) {
        return hasMerchantDiscount(player) ? 1.0 - plugin.extraConfig("scrolls.yml").getDouble("scrolls.merchant-discount.discount", 0.15) : 1.0;
    }

    private boolean activatePlatformBuilder(Player player) {
        int seconds = duration("platform-builder", 300);
        platformBuilderUntil.put(player.getUniqueId(), System.currentTimeMillis() + seconds * 1000L);
        player.sendMessage("Platform builder enabled. Left-click point A, right-click point B.");
        return true;
    }

    private boolean activateHopperLink(Player player) {
        int seconds = duration("hopper-wireless-link", 7200);
        hopperLinkUntil.put(player.getUniqueId(), System.currentTimeMillis() + seconds * 1000L);
        player.sendMessage("Hopper link mode enabled for " + seconds + " seconds. Right-click a chest, then a hopper.");
        return true;
    }

    private boolean timed(Player player, Map<UUID, Long> map, String id, String message) {
        IslandData island = islandService.islandAt(player.getLocation()).orElse(null);
        if (island == null) {
            player.sendMessage("Use this on an island.");
            return false;
        }
        int seconds = duration(id, 900);
        map.put(player.getUniqueId(), System.currentTimeMillis() + seconds * 1000L);
        player.sendMessage(message + " for " + seconds + " seconds.");
        return true;
    }

    private boolean useGeneratorXp(Player player) {
        IslandData island = islandService.islandAt(player.getLocation()).orElse(null);
        if (island == null) {
            player.sendMessage("Use this on an island.");
            return false;
        }
        int xp = plugin.extraConfig("scrolls.yml").getInt("scrolls.cobble-generator-xp.xp", 1000);
        island.addGeneratorXp(xp);
        island.generatorLevel(generatorManager.levelForXp(island.generatorXp()));
        islandService.save(island);
        player.sendMessage("Added " + xp + " generator XP. Level: " + island.generatorLevel());
        return true;
    }

    private boolean repairHeldItem(Player player) {
        ItemStack held = player.getInventory().getItemInMainHand();
        if (held.getType() == Material.AIR || !(held.getItemMeta() instanceof org.bukkit.inventory.meta.Damageable damageable)) {
            player.sendMessage("Hold a damaged item first.");
            return false;
        }
        damageable.setDamage(0);
        held.setItemMeta(damageable);
        player.sendMessage("Item repaired.");
        return true;
    }

    private boolean feedAndHeal(Player player) {
        player.setHealth(maxHealth(player));
        player.setFoodLevel(20);
        player.setSaturation(20);
        player.getActivePotionEffects().forEach(effect -> {
            String key = effect.getType().getKey().getKey();
            if (key.contains("poison") || key.contains("wither") || key.contains("weakness")
                || key.contains("slowness") || key.contains("blindness") || key.contains("hunger")
                || key.contains("mining_fatigue") || key.contains("nausea")) {
                player.removePotionEffect(effect.getType());
            }
        });
        player.sendMessage("Restored health and hunger.");
        return true;
    }

    private boolean hasteSpeed(Player player) {
        int seconds = duration("haste-speed", 1200);
        int amplifier = plugin.extraConfig("scrolls.yml").getInt("scrolls.haste-speed.amplifier", 1);
        int duration = seconds * 20;
        player.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, duration, amplifier));
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, duration, amplifier));
        player.sendMessage("Haste and Speed applied.");
        return true;
    }

    private boolean fly(Player player) {
        IslandData island = islandService.islandAt(player.getLocation()).orElse(null);
        if (island == null) {
            player.sendMessage("Use this on an island.");
            return false;
        }
        int seconds = duration("fly", 1800);
        flyUntil.put(player.getUniqueId(), System.currentTimeMillis() + seconds * 1000L);
        player.setAllowFlight(true);
        player.sendMessage("Island flight enabled for " + seconds + " seconds.");
        return true;
    }

    private boolean voidFallNegation(Player player) {
        return timed(player, voidFallProtectionUntil, "void-fall-negation", "Void fall protection enabled");
    }

    private boolean fertilizeChunk(Player player) {
        IslandData island = islandService.islandAt(player.getLocation()).orElse(null);
        if (island == null) {
            player.sendMessage("Use this on an island.");
            return false;
        }
        int seconds = duration("chunk-fertilizer", 600);
        fertilizerChunks.put(chunkKey(player.getLocation().getChunk()), System.currentTimeMillis() + seconds * 1000L);
        player.sendMessage("This chunk is fertilized for " + seconds + " seconds.");
        return true;
    }

    private boolean boostSpawner(Player player) {
        Block target = player.getTargetBlockExact(6);
        if (target == null || !(target.getState() instanceof CreatureSpawner spawner) || islandService.islandAt(target.getLocation()).isEmpty()) {
            player.sendMessage("Look at a spawner on an island.");
            return false;
        }
        double reduction = plugin.extraConfig("scrolls.yml").getDouble("scrolls.mob-spawner-booster.delay-reduction", 0.25);
        spawner.setMinSpawnDelay(Math.max(20, (int) Math.round(spawner.getMinSpawnDelay() * (1.0 - reduction))));
        spawner.setMaxSpawnDelay(Math.max(spawner.getMinSpawnDelay(), (int) Math.round(spawner.getMaxSpawnDelay() * (1.0 - reduction))));
        spawner.update(true);
        player.sendMessage("Spawner delay reduced.");
        return true;
    }

    private boolean silenceSpawner(Player player) {
        Block target = player.getTargetBlockExact(6);
        if (target == null || !(target.getState() instanceof CreatureSpawner) || islandService.islandAt(target.getLocation()).isEmpty()) {
            player.sendMessage("Look at a spawner on an island.");
            return false;
        }
        target.getWorld().playSound(target.getLocation(), org.bukkit.Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.2f, 0.6f);
        player.sendMessage("Spawner marked as silenced.");
        return true;
    }

    private boolean clearWeather(Player player) {
        World world = player.getWorld();
        int seconds = duration("clear-weather", 7200);
        world.setStorm(false);
        world.setThundering(false);
        world.setWeatherDuration(seconds * 20);
        player.sendMessage("Weather cleared for " + seconds + " seconds.");
        return true;
    }

    private boolean minionFuel(Player player) {
        IslandData island = islandService.islandAt(player.getLocation()).orElse(null);
        if (island == null) {
            player.sendMessage("Use this on an island.");
            return false;
        }
        player.sendMessage("Minion fuel stored for future minion automation.");
        return true;
    }

    private boolean scrollOfTheDeep(Player player) {
        int seconds = duration("scroll-of-the-deep", 3600);
        player.addPotionEffect(new PotionEffect(PotionEffectType.WATER_BREATHING, seconds * 20, 0));
        player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, seconds * 20, 0));
        player.sendMessage("The deep empowers you for " + seconds + " seconds.");
        return true;
    }

    private boolean expandBoundary(Player player) {
        IslandData island = islandService.islandAt(player.getLocation()).orElse(null);
        if (island == null || !islandService.isOwnerOrTrusted(player, island)) {
            player.sendMessage("Use this on your island.");
            return false;
        }
        int increase = plugin.extraConfig("scrolls.yml").getInt("scrolls.island-boundary-expander.side-increase", 5);
        BlockFace direction = cardinalFace(player.getLocation().getYaw());
        if (!islandService.expandSide(island, direction, increase)) {
            player.sendMessage("That island side is already at its maximum expansion.");
            return false;
        }
        player.sendMessage("Expanded island " + direction.name().toLowerCase() + " boundary by " + increase + " blocks.");
        return true;
    }

    private void buildPlatform(Player player, Block a, Block b) {
        int minX = Math.min(a.getX(), b.getX());
        int maxX = Math.max(a.getX(), b.getX());
        int minY = Math.min(a.getY(), b.getY());
        int maxY = Math.max(a.getY(), b.getY());
        int minZ = Math.min(a.getZ(), b.getZ());
        int maxZ = Math.max(a.getZ(), b.getZ());
        int volume = (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);
        int maxVolume = plugin.extraConfig("scrolls.yml").getInt("scrolls.platform-builder.max-volume", 500);
        if (volume > maxVolume) {
            player.sendMessage("Selection is too large. Max volume: " + maxVolume);
            return;
        }
        ItemStack source = player.getInventory().getItemInMainHand();
        Material material = source.getType() == Material.AIR || !source.getType().isBlock() ? Material.COBBLESTONE : source.getType();
        if (player.getGameMode() != GameMode.CREATIVE && !removeItems(player.getInventory(), material, volume)) {
            player.sendMessage("You need " + volume + "x " + material.name() + ".");
            return;
        }
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    a.getWorld().getBlockAt(x, y, z).setType(material, false);
                }
            }
        }
        player.sendMessage("Platform built with " + volume + " blocks.");
    }

    private boolean removeItems(Inventory inventory, Material material, int amount) {
        if (!inventory.containsAtLeast(new ItemStack(material), amount)) {
            return false;
        }
        inventory.removeItem(new ItemStack(material, amount));
        return true;
    }

    private boolean active(Map<UUID, Long> map, UUID uuid) {
        return map.getOrDefault(uuid, 0L) > System.currentTimeMillis();
    }

    private int duration(String id, int fallback) {
        return plugin.extraConfig("scrolls.yml").getInt("scrolls." + id + ".duration-seconds", fallback);
    }

    private String chunkKey(Chunk chunk) {
        return chunk.getWorld().getName() + ";" + chunk.getX() + ";" + chunk.getZ();
    }

    private double maxHealth(Player player) {
        org.bukkit.attribute.AttributeInstance attribute = player.getAttribute(Attribute.MAX_HEALTH);
        return attribute == null ? 20.0 : attribute.getValue();
    }

    private BlockFace cardinalFace(float yaw) {
        float normalized = (yaw % 360 + 360) % 360;
        if (normalized >= 315 || normalized < 45) {
            return BlockFace.SOUTH;
        }
        if (normalized < 135) {
            return BlockFace.WEST;
        }
        if (normalized < 225) {
            return BlockFace.NORTH;
        }
        return BlockFace.EAST;
    }

    private String readableName(String id) {
        String[] parts = id.split("-");
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (!builder.isEmpty()) {
                builder.append(' ');
            }
            builder.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return builder.toString();
    }

    private record WirelessLink(Location chest, long expiresAt) {
    }
}
