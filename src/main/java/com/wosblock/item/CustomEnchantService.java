package com.wosblock.item;

import com.wosblock.util.Text;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

public final class CustomEnchantService {
    private final CustomItemKeys keys;

    public CustomEnchantService(CustomItemKeys keys) {
        this.keys = keys;
    }

    public ItemStack createBook(String id, int amount) {
        ItemStack item = new ItemStack(Material.ENCHANTED_BOOK, Math.max(1, amount));
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Text.legacy("&d" + readableName(id)));
        meta.lore(Text.lore("&7Drop onto a compatible item to apply."));
        meta.getPersistentDataContainer().set(keys.enchantKey(), PersistentDataType.STRING, id);
        item.setItemMeta(meta);
        return item;
    }

    public boolean hasEnchant(ItemStack item, String id) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        return enchantIds(item).contains(id);
    }

    public Set<String> enchantIds(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return Set.of();
        }
        String stored = item.getItemMeta().getPersistentDataContainer().get(keys.enchantKey(), PersistentDataType.STRING);
        if (stored == null || stored.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(stored.split(","))
            .map(String::trim)
            .filter(value -> !value.isEmpty())
            .collect(Collectors.toSet());
    }

    public boolean apply(ItemStack target, ItemStack book) {
        if (target == null || book == null || target.getType() == Material.AIR || !book.hasItemMeta()) {
            return false;
        }
        String id = book.getItemMeta().getPersistentDataContainer().get(keys.enchantKey(), PersistentDataType.STRING);
        if (id == null || !compatible(target, id)) {
            return false;
        }
        Set<String> enchantIds = enchantIds(target);
        if (enchantIds.contains(id)) {
            return false;
        }
        ItemMeta meta = target.getItemMeta();
        List<String> updatedIds = new ArrayList<>(enchantIds);
        updatedIds.add(id);
        meta.getPersistentDataContainer().set(keys.enchantKey(), PersistentDataType.STRING, String.join(",", updatedIds));
        List<Component> lore = meta.hasLore() && meta.lore() != null ? new ArrayList<>(meta.lore()) : new ArrayList<>();
        lore.add(Text.legacy("&d" + readableName(id)));
        meta.lore(lore);
        target.setItemMeta(meta);
        book.setAmount(book.getAmount() - 1);
        return true;
    }

    private boolean compatible(ItemStack target, String id) {
        String type = target.getType().name();
        return switch (id) {
            case "blast-mining", "auto-smelt", "experience-boost" -> type.endsWith("_PICKAXE");
            case "lumberjack", "titanium-silk-touch" -> type.endsWith("_AXE");
            case "replant", "harvester-fortune", "hydro-soil" -> type.endsWith("_HOE");
            case "mob-swarm", "loot-multiplier", "beheading", "molten-touch" -> type.endsWith("_SWORD");
            case "gorgon-eye" -> type.endsWith("_BOW") || type.equals("CROSSBOW");
            case "void-insurance" -> type.endsWith("_CHESTPLATE");
            case "photosynthesis" -> type.endsWith("_HELMET");
            case "spring-step" -> type.endsWith("_BOOTS");
            case "sea-collector", "abyssal-hook" -> type.equals("FISHING_ROD");
            case "unbreakable-core" -> type.endsWith("_HELMET")
                || type.endsWith("_CHESTPLATE")
                || type.endsWith("_LEGGINGS")
                || type.endsWith("_BOOTS");
            case "experience-vampire" -> type.endsWith("_SWORD")
                || type.endsWith("_PICKAXE")
                || type.endsWith("_AXE")
                || type.endsWith("_SHOVEL")
                || type.endsWith("_HOE");
            case "telekinesis" -> type.endsWith("_PICKAXE")
                || type.endsWith("_AXE")
                || type.endsWith("_SHOVEL")
                || type.endsWith("_HOE");
            default -> true;
        };
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
