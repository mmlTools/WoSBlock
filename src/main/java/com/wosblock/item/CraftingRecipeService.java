package com.wosblock.item;

import com.wosblock.WoSBlockPlugin;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapelessRecipe;

public final class CraftingRecipeService {
    private final WoSBlockPlugin plugin;
    private final ScrollService scrollService;
    private final CustomEnchantService enchantService;

    public CraftingRecipeService(WoSBlockPlugin plugin, ScrollService scrollService, CustomEnchantService enchantService) {
        this.plugin = plugin;
        this.scrollService = scrollService;
        this.enchantService = enchantService;
    }

    public void registerRecipes() {
        registerScrollRecipes();
        registerEnchantRecipes();
    }

    private void registerScrollRecipes() {
        ConfigurationSection scrolls = plugin.extraConfig("scrolls.yml").getConfigurationSection("scrolls");
        if (scrolls == null) {
            return;
        }
        for (String id : scrolls.getKeys(false)) {
            ConfigurationSection recipeSection = scrolls.getConfigurationSection(id + ".recipe");
            if (recipeSection == null) {
                continue;
            }
            register("scroll_" + id, scrollService.createScroll(id, 1), recipeSection);
        }
    }

    private void registerEnchantRecipes() {
        ConfigurationSection enchants = plugin.extraConfig("enchants.yml").getConfigurationSection("custom-enchants");
        if (enchants == null) {
            return;
        }
        for (String id : enchants.getKeys(false)) {
            if (!enchants.getBoolean(id + ".enabled", true)) {
                continue;
            }
            ConfigurationSection recipeSection = enchants.getConfigurationSection(id + ".recipe");
            if (recipeSection == null) {
                continue;
            }
            register("enchant_" + id, enchantService.createBook(id, 1), recipeSection);
        }
    }

    private void register(String key, ItemStack result, ConfigurationSection ingredients) {
        NamespacedKey namespacedKey = new NamespacedKey(plugin, key.toLowerCase().replace('-', '_'));
        ShapelessRecipe recipe = new ShapelessRecipe(namespacedKey, result);
        int remainingSlots = 9;
        boolean hasIngredients = false;
        for (String materialName : ingredients.getKeys(false)) {
            Material material = Material.matchMaterial(materialName);
            if (material == null) {
                plugin.getLogger().warning("Unknown recipe material: " + materialName);
                continue;
            }
            int amount = Math.min(remainingSlots, Math.max(1, ingredients.getInt(materialName)));
            for (int i = 0; i < amount; i++) {
                recipe.addIngredient(material);
                hasIngredients = true;
            }
            remainingSlots -= amount;
            if (remainingSlots <= 0) {
                break;
            }
        }
        if (hasIngredients) {
            plugin.getServer().removeRecipe(namespacedKey);
            plugin.getServer().addRecipe(recipe);
        }
    }
}
