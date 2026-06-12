package com.wosblock.item;

import org.bukkit.NamespacedKey;
import org.bukkit.plugin.java.JavaPlugin;

public final class CustomItemKeys {
    private final NamespacedKey scrollKey;
    private final NamespacedKey enchantKey;

    public CustomItemKeys(JavaPlugin plugin) {
        this.scrollKey = new NamespacedKey(plugin, "scroll_id");
        this.enchantKey = new NamespacedKey(plugin, "custom_enchant");
    }

    public NamespacedKey scrollKey() {
        return scrollKey;
    }

    public NamespacedKey enchantKey() {
        return enchantKey;
    }
}
