package com.wosblock.listener;

import com.wosblock.WoSBlockPlugin;
import com.wosblock.item.CustomEnchantService;
import com.wosblock.island.IslandData;
import com.wosblock.island.IslandService;
import java.util.Collection;
import java.util.List;
import java.util.Random;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.block.data.type.Farmland;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public final class CustomEnchantEffectListener implements Listener {
    private final WoSBlockPlugin plugin;
    private final CustomEnchantService enchantService;
    private final IslandService islandService;
    private final Random random = new Random();

    public CustomEnchantEffectListener(WoSBlockPlugin plugin, CustomEnchantService enchantService, IslandService islandService) {
        this.plugin = plugin;
        this.enchantService = enchantService;
        this.islandService = islandService;
    }

    @EventHandler(ignoreCancelled = true)
    public void onCombat(EntityDamageByEntityEvent event) {
        Player attacker = attacker(event.getDamager());
        if (attacker == null || !(event.getEntity() instanceof LivingEntity target)) {
            return;
        }
        ItemStack weapon = attacker.getInventory().getItemInMainHand();
        if (enchantService.hasEnchant(weapon, "mob-swarm")) {
            double chance = config("mob-swarm.chance", 0.20);
            if (random.nextDouble() <= chance) {
                double mirrored = event.getFinalDamage() * config("mob-swarm.damage-share", 0.50);
                for (Entity nearby : target.getNearbyEntities(4, 3, 4)) {
                    if (nearby instanceof LivingEntity living && nearby.getType() == target.getType() && !nearby.equals(target)) {
                        living.damage(mirrored, attacker);
                    }
                }
            }
        }
        if (enchantService.hasEnchant(weapon, "molten-touch") && random.nextDouble() <= config("molten-touch.fire-chance", 0.25)) {
            target.setFireTicks(Math.max(target.getFireTicks(), 100));
        }
        if (enchantService.hasEnchant(weapon, "gorgon-eye") && event.getDamager() instanceof AbstractArrow) {
            if (random.nextDouble() <= config("gorgon-eye.freeze-chance", 0.05)) {
                target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, (int) config("gorgon-eye.duration-seconds", 3) * 20, 4));
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDeath(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer == null) {
            return;
        }
        ItemStack weapon = killer.getInventory().getItemInMainHand();
        if (enchantService.hasEnchant(weapon, "loot-multiplier") && random.nextDouble() <= config("loot-multiplier.chance", 0.20)) {
            List<ItemStack> extra = event.getDrops().stream().map(ItemStack::clone).toList();
            event.getDrops().addAll(extra);
        }
        if (enchantService.hasEnchant(weapon, "beheading") && random.nextDouble() <= config("beheading.chance", 0.05)) {
            event.getDrops().add(new ItemStack(Material.PLAYER_HEAD));
        }
        if (enchantService.hasEnchant(weapon, "molten-touch")) {
            cookDrops(event.getDrops());
        }
        if (enchantService.hasEnchant(weapon, "experience-vampire")) {
            event.setDroppedExp((int) Math.ceil(event.getDroppedExp() * config("experience-vampire.multiplier", 1.5)));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        ItemStack tool = event.getPlayer().getInventory().getItemInMainHand();
        if (enchantService.hasEnchant(tool, "experience-vampire")) {
            event.setExpToDrop((int) Math.ceil(event.getExpToDrop() * config("experience-vampire.multiplier", 1.5)));
        }
        if (enchantService.hasEnchant(tool, "titanium-silk-touch") && event.getBlock().getState() instanceof CreatureSpawner) {
            event.setDropItems(false);
            event.getBlock().getWorld().dropItemNaturally(event.getBlock().getLocation(), new ItemStack(Material.SPAWNER));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onHydroTill(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) {
            return;
        }
        ItemStack tool = event.getPlayer().getInventory().getItemInMainHand();
        if (!enchantService.hasEnchant(tool, "hydro-soil")) {
            return;
        }
        org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> {
            if (event.getClickedBlock().getBlockData() instanceof Farmland farmland) {
                farmland.setMoisture(farmland.getMaximumMoisture());
                event.getClickedBlock().setBlockData(farmland, false);
            }
        });
    }

    @EventHandler(ignoreCancelled = true)
    public void onArmorMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        IslandData island = islandService.islandAt(player.getLocation()).orElse(null);
        if (island == null || !islandService.isOwnerOrTrusted(player, island)) {
            return;
        }
        if (hasArmorEnchant(player, "spring-step")) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 60, 0, true, false));
            player.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, 60, 0, true, false));
        }
        if (hasArmorEnchant(player, "photosynthesis") && player.getWorld().getTime() < 12300 && player.getLocation().getBlock().getLightFromSky() >= 14) {
            repairArmor(player, 1);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onVoidInsurance(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player) || event.getCause() != EntityDamageEvent.DamageCause.VOID) {
            return;
        }
        ItemStack chestplate = player.getInventory().getChestplate();
        if (!enchantService.hasEnchant(chestplate, "void-insurance")) {
            return;
        }
        IslandData island = islandService.cachedIsland(player.getUniqueId()).orElse(null);
        if (island == null) {
            return;
        }
        damageItem(chestplate, Math.max(1, chestplate.getType().getMaxDurability() / 4));
        player.teleport(island.spawnLocation());
        player.damage(Math.max(1.0, maxHealth(player) / 2.0));
        event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onItemDamage(PlayerItemDamageEvent event) {
        if (hasArmorEnchant(event.getPlayer(), "unbreakable-core") && random.nextDouble() <= config("unbreakable-core.prevent-chance", 0.15)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onFishing(PlayerFishEvent event) {
        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH || islandService.islandAt(event.getPlayer().getLocation()).isEmpty()) {
            return;
        }
        ItemStack rod = event.getPlayer().getInventory().getItemInMainHand();
        if (enchantService.hasEnchant(rod, "sea-collector") && event.getCaught() instanceof org.bukkit.entity.Item item) {
            if (random.nextDouble() <= config("sea-collector.double-chance", 0.15)) {
                ItemStack stack = item.getItemStack();
                stack.setAmount(Math.min(stack.getMaxStackSize(), stack.getAmount() * 2));
                item.setItemStack(stack);
            }
        }
        if (enchantService.hasEnchant(rod, "abyssal-hook") && event.getCaught() instanceof org.bukkit.entity.Item item) {
            if (random.nextDouble() <= config("abyssal-hook.rare-upgrade-chance", 0.25)) {
                item.setItemStack(new ItemStack(Material.HEART_OF_THE_SEA));
            }
        }
    }

    private Player attacker(Entity damager) {
        if (damager instanceof Player player) {
            return player;
        }
        if (damager instanceof AbstractArrow arrow && arrow.getShooter() instanceof Player player) {
            return player;
        }
        return null;
    }

    private void cookDrops(Collection<ItemStack> drops) {
        for (ItemStack drop : drops) {
            Material cooked = switch (drop.getType()) {
                case BEEF -> Material.COOKED_BEEF;
                case PORKCHOP -> Material.COOKED_PORKCHOP;
                case CHICKEN -> Material.COOKED_CHICKEN;
                case RABBIT -> Material.COOKED_RABBIT;
                case MUTTON -> Material.COOKED_MUTTON;
                case COD -> Material.COOKED_COD;
                case SALMON -> Material.COOKED_SALMON;
                default -> null;
            };
            if (cooked != null) {
                drop.setType(cooked);
            }
        }
    }

    private boolean hasArmorEnchant(Player player, String id) {
        for (ItemStack item : player.getInventory().getArmorContents()) {
            if (enchantService.hasEnchant(item, id)) {
                return true;
            }
        }
        return false;
    }

    private void repairArmor(Player player, int amount) {
        for (ItemStack item : player.getInventory().getArmorContents()) {
            if (item != null) {
                damageItem(item, -amount);
            }
        }
    }

    private void damageItem(ItemStack item, int amount) {
        if (item == null || !(item.getItemMeta() instanceof Damageable damageable)) {
            return;
        }
        damageable.setDamage(Math.max(0, damageable.getDamage() + amount));
        item.setItemMeta(damageable);
    }

    private double maxHealth(Player player) {
        org.bukkit.attribute.AttributeInstance attribute = player.getAttribute(Attribute.MAX_HEALTH);
        return attribute == null ? 20.0 : attribute.getValue();
    }

    private double config(String path, double fallback) {
        return plugin.extraConfig("enchants.yml").getDouble("custom-enchants." + path, fallback);
    }
}
