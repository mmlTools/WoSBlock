package com.wosblock.item;

import com.wosblock.WoSBlockPlugin;
import com.wosblock.generator.CobblestoneGeneratorManager;
import com.wosblock.island.IslandData;
import com.wosblock.island.IslandService;
import com.wosblock.util.Text;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
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
            case "cobble-generator-xp" -> useGeneratorXp(player);
            case "fly" -> fly(player);
            case "repair" -> repairHeldItem(player);
            case "feed-heal" -> feedAndHeal(player);
            case "haste-speed" -> hasteSpeed(player);
            case "void-fall-negation" -> voidFallNegation(player);
            case "island-boundary-expander" -> expandBoundary(player);
            default -> {
                player.sendMessage("This scroll is configured but not implemented yet: " + id);
                yield false;
            }
        };
        if (consumed) {
            item.setAmount(item.getAmount() - 1);
        }
        return true;
    }

    public boolean hasVoidFallProtection(Player player) {
        return voidFallProtectionUntil.getOrDefault(player.getUniqueId(), 0L) > System.currentTimeMillis();
    }

    public boolean hasFly(Player player) {
        return flyUntil.getOrDefault(player.getUniqueId(), 0L) > System.currentTimeMillis();
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
            if (effect.getType().isInstant() || effect.getType().getKey().getKey().contains("poison") || effect.getType().getKey().getKey().contains("wither")) {
                player.removePotionEffect(effect.getType());
            }
        });
        player.sendMessage("Restored health and hunger.");
        return true;
    }

    private boolean hasteSpeed(Player player) {
        int duration = 20 * 60 * 20;
        player.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, duration, 1));
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, duration, 1));
        player.sendMessage("Haste and Speed applied.");
        return true;
    }

    private double maxHealth(Player player) {
        org.bukkit.attribute.AttributeInstance attribute = player.getAttribute(Attribute.MAX_HEALTH);
        return attribute == null ? 20.0 : attribute.getValue();
    }

    private boolean fly(Player player) {
        IslandData island = islandService.islandAt(player.getLocation()).orElse(null);
        if (island == null) {
            player.sendMessage("Use this on an island.");
            return false;
        }
        int seconds = plugin.extraConfig("scrolls.yml").getInt("scrolls.fly.duration-seconds", 1800);
        flyUntil.put(player.getUniqueId(), System.currentTimeMillis() + (seconds * 1000L));
        player.setAllowFlight(true);
        player.sendMessage("Island flight enabled for " + seconds + " seconds.");
        return true;
    }

    private boolean voidFallNegation(Player player) {
        IslandData island = islandService.islandAt(player.getLocation()).orElse(null);
        if (island == null) {
            player.sendMessage("Use this on an island.");
            return false;
        }
        int seconds = plugin.extraConfig("scrolls.yml").getInt("scrolls.void-fall-negation.duration-seconds", 3600);
        voidFallProtectionUntil.put(player.getUniqueId(), System.currentTimeMillis() + (seconds * 1000L));
        player.sendMessage("Void fall protection enabled for " + seconds + " seconds.");
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
}
