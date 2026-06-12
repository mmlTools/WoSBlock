package com.wosblock.npc;

import com.wosblock.WoSBlockPlugin;
import com.wosblock.item.ItemFactory;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Villager;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

public final class NpcManager {
    private final WoSBlockPlugin plugin;
    private final ItemFactory itemFactory;
    private final NamespacedKey npcTypeKey;
    private final NamespacedKey npcOwnerKey;

    public NpcManager(WoSBlockPlugin plugin) {
        this.plugin = plugin;
        this.itemFactory = new ItemFactory(plugin);
        this.npcTypeKey = new NamespacedKey(plugin, "npc_type");
        this.npcOwnerKey = new NamespacedKey(plugin, "npc_owner");
    }

    public String readEggType(ItemStack item) {
        return itemFactory.readNpcEggType(item);
    }

    public LivingEntity spawnCoreNpc(String typeKey, UUID owner, Location location) {
        String normalized = typeKey.toLowerCase();
        LivingEntity entity = (LivingEntity) location.getWorld().spawnEntity(location, EntityType.VILLAGER);
        if (entity instanceof Villager villager) {
            villager.setProfession(Villager.Profession.LIBRARIAN);
            villager.setVillagerType(Villager.Type.PLAINS);
        }
        entity.customName(ItemFactory.color(plugin.getConfig().getString("npcs.eggs." + normalized + ".display-name", normalized)));
        entity.setCustomNameVisible(true);
        entity.setInvulnerable(true);
        entity.setPersistent(true);
        entity.setRemoveWhenFarAway(false);
        entity.setAI(false);
        entity.setCollidable(false);
        entity.getPersistentDataContainer().set(npcTypeKey, PersistentDataType.STRING, normalized);
        entity.getPersistentDataContainer().set(npcOwnerKey, PersistentDataType.STRING, owner.toString());
        return entity;
    }

    public boolean isManagedNpc(Entity entity) {
        return entity.getPersistentDataContainer().has(npcTypeKey, PersistentDataType.STRING);
    }

    public String npcType(Entity entity) {
        return entity.getPersistentDataContainer().get(npcTypeKey, PersistentDataType.STRING);
    }

    public UUID npcOwner(Entity entity) {
        String value = entity.getPersistentDataContainer().get(npcOwnerKey, PersistentDataType.STRING);
        return value == null ? null : UUID.fromString(value);
    }

    public ItemStack eggFor(String npcType) {
        String path = "npcs.eggs." + npcType.toLowerCase();
        Material material = Material.matchMaterial(plugin.getConfig().getString(path + ".material", "VILLAGER_SPAWN_EGG"));
        if (material == null) {
            material = Material.VILLAGER_SPAWN_EGG;
        }
        return itemFactory.npcEgg(material, npcType.toLowerCase(), plugin.getConfig().getString(path + ".display-name", npcType));
    }
}
