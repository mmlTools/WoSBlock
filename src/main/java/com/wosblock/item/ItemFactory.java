package com.wosblock.item;

import com.wosblock.util.Text;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

public final class ItemFactory {
    private final NamespacedKey npcEggKey;
    private final NamespacedKey enderWorldEggKey;

    public ItemFactory(JavaPlugin plugin) {
        this.npcEggKey = new NamespacedKey(plugin, "npc_egg_type");
        this.enderWorldEggKey = new NamespacedKey(plugin, "ender_world_egg");
    }

    public ItemStack npcEgg(Material material, String npcType, String displayName) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Text.legacy(displayName));
        meta.lore(Text.lore("&7Summons the " + npcType + " for this island."));
        meta.getPersistentDataContainer().set(npcEggKey, PersistentDataType.STRING, npcType);
        item.setItemMeta(meta);
        return item;
    }

    public String readNpcEggType(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }
        return item.getItemMeta().getPersistentDataContainer().get(npcEggKey, PersistentDataType.STRING);
    }

    public ItemStack enderWorldEgg(int amount) {
        ItemStack item = new ItemStack(Material.EGG, Math.max(1, amount));
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Text.legacy("&5EnderWorld Egg"));
        meta.lore(Text.lore(
            "&7Throw at an island NPC to pack it into its spawn egg.",
            "&7Core island NPC relocation is guaranteed for trusted players.",
            "&cCapturing other NPCs can fail and consumes the egg."
        ));
        meta.getPersistentDataContainer().set(enderWorldEggKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    public boolean isEnderWorldEgg(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        return item.getItemMeta().getPersistentDataContainer().has(enderWorldEggKey, PersistentDataType.BYTE);
    }

    public static net.kyori.adventure.text.Component color(String value) {
        return Text.legacy(value);
    }
}
