package com.wosblock.item;

import com.wosblock.util.Text;
import java.util.ArrayList;
import java.util.List;
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
        return id.equals(item.getItemMeta().getPersistentDataContainer().get(keys.enchantKey(), PersistentDataType.STRING));
    }

    public boolean apply(ItemStack target, ItemStack book) {
        if (target == null || book == null || target.getType() == Material.AIR || !book.hasItemMeta()) {
            return false;
        }
        String id = book.getItemMeta().getPersistentDataContainer().get(keys.enchantKey(), PersistentDataType.STRING);
        if (id == null || !compatible(target, id)) {
            return false;
        }
        ItemMeta meta = target.getItemMeta();
        meta.getPersistentDataContainer().set(keys.enchantKey(), PersistentDataType.STRING, id);
        List<Component> lore = meta.hasLore() && meta.lore() != null ? new ArrayList<>(meta.lore()) : new ArrayList<>();
        lore.add(Text.legacy("&d" + readableName(id)));
        meta.lore(lore);
        target.setItemMeta(meta);
        book.setAmount(book.getAmount() - 1);
        return true;
    }

    private boolean compatible(ItemStack target, String id) {
        if (id.equals("blast-mining")) {
            return target.getType().name().endsWith("_PICKAXE");
        }
        if (id.equals("auto-smelt")) {
            return target.getType().name().endsWith("_PICKAXE");
        }
        if (id.equals("telekinesis")) {
            return target.getType().name().endsWith("_PICKAXE")
                || target.getType().name().endsWith("_AXE")
                || target.getType().name().endsWith("_SHOVEL")
                || target.getType().name().endsWith("_HOE");
        }
        return true;
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
