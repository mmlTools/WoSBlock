package com.wosblock.listener;

import com.wosblock.WoSBlockPlugin;
import com.wosblock.item.ItemFactory;
import com.wosblock.island.IslandService;
import com.wosblock.menu.MenuService;
import com.wosblock.npc.NpcManager;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Location;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Egg;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerEggThrowEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

public final class NpcEggListener implements Listener {
    private final WoSBlockPlugin plugin;
    private final NpcManager npcManager;
    private final IslandService islandService;
    private final MenuService menuService;
    private final ItemFactory itemFactory;
    private final NamespacedKey enderWorldProjectileKey;

    public NpcEggListener(WoSBlockPlugin plugin, NpcManager npcManager, IslandService islandService, MenuService menuService) {
        this.plugin = plugin;
        this.npcManager = npcManager;
        this.islandService = islandService;
        this.menuService = menuService;
        this.itemFactory = new ItemFactory(plugin);
        this.enderWorldProjectileKey = new NamespacedKey(plugin, "ender_world_projectile");
    }

    @EventHandler(ignoreCancelled = true)
    public void onCustomEggUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        ItemStack item = event.getItem();
        if (itemFactory.isEnderWorldEgg(item)) {
            if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
                return;
            }
            Egg egg = event.getPlayer().launchProjectile(Egg.class);
            egg.getPersistentDataContainer().set(enderWorldProjectileKey, PersistentDataType.BYTE, (byte) 1);
            consumeOne(event.getPlayer(), item);
            event.setCancelled(true);
            return;
        }

        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) {
            return;
        }

        String npcType = npcManager.readEggType(item);
        if (npcType == null) {
            return;
        }
        if (!islandService.canManageNpc(event.getPlayer(), event.getClickedBlock().getLocation())) {
            event.getPlayer().sendMessage("Only the island owner or trusted members can place this NPC.");
            event.setCancelled(true);
            return;
        }

        Location spawnLocation = event.getClickedBlock().getRelative(event.getBlockFace()).getLocation().add(0.5, 0, 0.5);
        npcManager.spawnCoreNpc(npcType, event.getPlayer().getUniqueId(), spawnLocation);
        consumeOne(event.getPlayer(), item);
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onNpcDamage(EntityDamageEvent event) {
        if (npcManager.isManagedNpc(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onEggDamageNpc(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Egg egg) || !(egg.getShooter() instanceof Player player)) {
            return;
        }
        if (!egg.getPersistentDataContainer().has(enderWorldProjectileKey, PersistentDataType.BYTE)) {
            return;
        }
        if (!npcManager.isManagedNpc(event.getEntity())) {
            return;
        }
        event.setCancelled(true);
        handleManagedNpcCatch(player, event.getEntity());
        egg.remove();
    }

    @EventHandler(ignoreCancelled = true)
    public void onNpcTarget(EntityTargetLivingEntityEvent event) {
        if (npcManager.isManagedNpc(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onNpcClick(PlayerInteractAtEntityEvent event) {
        if (!npcManager.isManagedNpc(event.getRightClicked())) {
            return;
        }
        String type = npcManager.npcType(event.getRightClicked());
        menuService.openNpcMenu(event.getPlayer(), type);
        event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onEggHitNpc(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof Egg egg) || !(egg.getShooter() instanceof Player player)) {
            return;
        }
        boolean enderWorldEgg = egg.getPersistentDataContainer().has(enderWorldProjectileKey, PersistentDataType.BYTE);
        Entity hit = event.getHitEntity();
        if (hit == null && enderWorldEgg) {
            hit = egg.getNearbyEntities(1.25, 1.25, 1.25).stream()
                .filter(npcManager::isManagedNpc)
                .findFirst()
                .orElse(null);
        }
        if (!(hit instanceof LivingEntity) || hit instanceof Player) {
            return;
        }

        if (npcManager.isManagedNpc(hit)) {
            if (!enderWorldEgg) {
                player.sendMessage("Use an EnderWorld Egg to move island NPCs.");
                return;
            }
            handleManagedNpcCatch(player, hit);
            egg.remove();
            return;
        }

        if (!enderWorldEgg) {
            return;
        }

        double chance = plugin.getConfig().getDouble("npcs.catch-chance", 0.25);
        if (ThreadLocalRandom.current().nextDouble() <= chance) {
            hit.getWorld().dropItemNaturally(hit.getLocation(), vanillaSpawnEgg(hit));
            player.sendMessage("You caught the entity.");
        } else {
            player.sendMessage("The catch failed and the entity vanished.");
        }
        hit.remove();
    }

    @EventHandler(ignoreCancelled = true)
    public void onEggLaunch(ProjectileLaunchEvent event) {
        if (!(event.getEntity() instanceof Egg egg) || !(egg.getShooter() instanceof Player player)) {
            return;
        }
        ItemStack mainHand = player.getInventory().getItemInMainHand();
        ItemStack offHand = player.getInventory().getItemInOffHand();
        if (itemFactory.isEnderWorldEgg(mainHand) || itemFactory.isEnderWorldEgg(offHand)) {
            egg.getPersistentDataContainer().set(enderWorldProjectileKey, PersistentDataType.BYTE, (byte) 1);
        }
    }

    @EventHandler
    public void onEnderWorldEggHatch(PlayerEggThrowEvent event) {
        if (event.getEgg().getPersistentDataContainer().has(enderWorldProjectileKey, PersistentDataType.BYTE)) {
            event.setHatching(false);
            event.setNumHatches((byte) 0);
        }
    }

    private void handleManagedNpcCatch(Player player, Entity hit) {
        UUID owner = npcManager.npcOwner(hit);
        String type = npcManager.npcType(hit);
        boolean canManage = owner != null && (owner.equals(player.getUniqueId()) || islandService.canManageNpc(player, hit.getLocation()));

        if (canManage) {
            hit.getWorld().dropItemNaturally(hit.getLocation(), npcManager.eggFor(type));
            player.sendMessage("NPC packed into its spawn egg.");
        } else {
            player.sendMessage("Only the island owner or trusted members can move this NPC.");
            return;
        }
        hit.remove();
    }

    private ItemStack vanillaSpawnEgg(Entity entity) {
        String materialName = entity.getType().name().toUpperCase(Locale.ROOT) + "_SPAWN_EGG";
        Material material = Material.matchMaterial(materialName);
        return new ItemStack(material == null ? Material.EGG : material);
    }

    private void consumeOne(Player player, ItemStack item) {
        if (player.getGameMode() == GameMode.CREATIVE) {
            return;
        }
        item.setAmount(item.getAmount() - 1);
    }
}
