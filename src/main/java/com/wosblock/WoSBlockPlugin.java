package com.wosblock;

import com.wosblock.auction.AuctionHouseService;
import com.wosblock.automation.CompactorService;
import com.wosblock.automation.VoidRecoveryService;
import com.wosblock.economy.BalanceService;
import com.wosblock.fishing.FishingLootManager;
import com.wosblock.generator.CobblestoneGeneratorManager;
import com.wosblock.hud.HudService;
import com.wosblock.inventory.InventoryContextService;
import com.wosblock.island.BoundaryService;
import com.wosblock.island.IslandData;
import com.wosblock.island.IslandService;
import com.wosblock.listener.CobblestoneGeneratorListener;
import com.wosblock.listener.AutomationListener;
import com.wosblock.listener.CustomEnchantEffectListener;
import com.wosblock.listener.CustomFishingListener;
import com.wosblock.listener.GuestBookListener;
import com.wosblock.listener.GeneratorMiningXpListener;
import com.wosblock.listener.IslandProtectionListener;
import com.wosblock.listener.IslandRespawnListener;
import com.wosblock.listener.IslandContextListener;
import com.wosblock.listener.InventoryContextListener;
import com.wosblock.listener.MarketTooltipListener;
import com.wosblock.listener.MenuClickListener;
import com.wosblock.listener.NpcEggListener;
import com.wosblock.listener.PlayerStatsListener;
import com.wosblock.listener.QuestProgressListener;
import com.wosblock.listener.ScrollEffectListener;
import com.wosblock.listener.ScrollUseListener;
import com.wosblock.listener.CustomEnchantListener;
import com.wosblock.listener.TrophyListener;
import com.wosblock.listener.VoidFallListener;
import com.wosblock.item.CustomEnchantService;
import com.wosblock.item.CustomItemKeys;
import com.wosblock.item.CraftingRecipeService;
import com.wosblock.item.ItemFactory;
import com.wosblock.item.ScrollService;
import com.wosblock.market.MarketInfoService;
import com.wosblock.market.MarketTooltipService;
import com.wosblock.menu.MenuService;
import com.wosblock.npc.NpcManager;
import com.wosblock.quest.QuestService;
import com.wosblock.trophy.TrophyService;
import com.wosblock.storage.FlatFileStorage;
import com.wosblock.storage.MySqlStorage;
import com.wosblock.storage.Storage;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.HandlerList;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class WoSBlockPlugin extends JavaPlugin {
    private Storage storage;
    private CobblestoneGeneratorManager generatorManager;
    private FishingLootManager fishingLootManager;
    private NpcManager npcManager;
    private IslandService islandService;
    private BalanceService balanceService;
    private AuctionHouseService auctionHouseService;
    private QuestService questService;
    private MenuService menuService;
    private CustomItemKeys customItemKeys;
    private ScrollService scrollService;
    private CustomEnchantService customEnchantService;
    private CraftingRecipeService craftingRecipeService;
    private ItemFactory itemFactory;
    private TrophyService trophyService;
    private CompactorService compactorService;
    private VoidRecoveryService voidRecoveryService;
    private HudService hudService;
    private BoundaryService boundaryService;
    private InventoryContextService inventoryContextService;
    private MarketInfoService marketInfoService;
    private MarketTooltipService marketTooltipService;
    private AutomationListener automationListener;
    private final Map<String, FileConfiguration> extraConfigs = new ConcurrentHashMap<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveExtraConfigDefaults();
        loadExtraConfigs();
        getConfig().options().copyDefaults(true);
        saveConfig();
        loadServices();
        registerListeners();
        getLogger().info("WoSBlock enabled with " + getConfig().getString("storage-type", "flat-file") + " storage.");
    }

    @Override
    public void onDisable() {
        HandlerList.unregisterAll(this);
        if (voidRecoveryService != null) {
            voidRecoveryService.stop();
        }
        if (hudService != null) {
            hudService.stop();
        }
        if (boundaryService != null) {
            boundaryService.stop();
        }
        if (automationListener != null) {
            automationListener.stop();
        }
        if (storage != null) {
            storage.close();
        }
    }

    private void loadServices() {
        if ("mysql".equalsIgnoreCase(getConfig().getString("storage-type", "flat-file"))) {
            storage = new MySqlStorage(this);
        } else {
            storage = new FlatFileStorage(this);
        }

        generatorManager = new CobblestoneGeneratorManager(this);
        fishingLootManager = new FishingLootManager(this);
        npcManager = new NpcManager(this);
        islandService = new IslandService(this, storage);
        balanceService = new BalanceService(this, storage);
        auctionHouseService = new AuctionHouseService(this, balanceService, storage);
        questService = new QuestService(this, balanceService, islandService, storage);
        marketInfoService = new MarketInfoService(this);
        marketTooltipService = new MarketTooltipService(this, marketInfoService);
        customItemKeys = new CustomItemKeys(this);
        itemFactory = new ItemFactory(this);
        scrollService = new ScrollService(this, islandService, generatorManager, customItemKeys);
        menuService = new MenuService(this, balanceService, auctionHouseService, questService, marketInfoService, marketTooltipService, scrollService);
        customEnchantService = new CustomEnchantService(customItemKeys);
        craftingRecipeService = new CraftingRecipeService(this, scrollService, customEnchantService);
        craftingRecipeService.registerRecipes();
        trophyService = new TrophyService(this);
        compactorService = new CompactorService();
        voidRecoveryService = new VoidRecoveryService(this, islandService);
        voidRecoveryService.start();
        hudService = new HudService(this, islandService, balanceService, questService);
        hudService.start();
        boundaryService = new BoundaryService(this, islandService);
        boundaryService.start();
        inventoryContextService = new InventoryContextService(this);
        islandService.setIslandDataCleanup(this::deleteIslandBoundPlayerData);
    }

    private CompletableFuture<Void> deleteIslandBoundPlayerData(Player player, IslandData island) {
        UUID playerId = player.getUniqueId();
        String worldName = island.worldName();
        balanceService.clear(playerId, worldName);
        questService.clear(playerId);
        voidRecoveryService.clear(playerId);
        return CompletableFuture.allOf(
            storage.deleteIslandPlayerData(playerId, worldName),
            inventoryContextService.deleteContext(playerId, worldName),
            auctionHouseService.deleteSellerListings(playerId)
        );
    }

    private void registerListeners() {
        getServer().getPluginManager().registerEvents(new CobblestoneGeneratorListener(generatorManager, islandService), this);
        getServer().getPluginManager().registerEvents(new GeneratorMiningXpListener(generatorManager, islandService), this);
        getServer().getPluginManager().registerEvents(new NpcEggListener(this, npcManager, islandService, menuService), this);
        getServer().getPluginManager().registerEvents(new CustomFishingListener(fishingLootManager, islandService, scrollService), this);
        getServer().getPluginManager().registerEvents(new IslandProtectionListener(islandService), this);
        getServer().getPluginManager().registerEvents(new IslandRespawnListener(islandService), this);
        getServer().getPluginManager().registerEvents(new GuestBookListener(islandService, voidRecoveryService), this);
        getServer().getPluginManager().registerEvents(new MenuClickListener(menuService), this);
        getServer().getPluginManager().registerEvents(new QuestProgressListener(questService, islandService), this);
        getServer().getPluginManager().registerEvents(new ScrollUseListener(scrollService), this);
        getServer().getPluginManager().registerEvents(new ScrollEffectListener(scrollService, islandService), this);
        getServer().getPluginManager().registerEvents(new CustomEnchantListener(this, customEnchantService), this);
        getServer().getPluginManager().registerEvents(new CustomEnchantEffectListener(this, customEnchantService, islandService), this);
        getServer().getPluginManager().registerEvents(new TrophyListener(trophyService, islandService), this);
        automationListener = new AutomationListener(this, compactorService, islandService);
        automationListener.start();
        getServer().getPluginManager().registerEvents(automationListener, this);
        getServer().getPluginManager().registerEvents(new VoidFallListener(islandService, scrollService), this);
        getServer().getPluginManager().registerEvents(new MarketTooltipListener(this, islandService, marketTooltipService), this);
        getServer().getPluginManager().registerEvents(new InventoryContextListener(inventoryContextService), this);
        getServer().getPluginManager().registerEvents(new IslandContextListener(islandService), this);
        getServer().getPluginManager().registerEvents(new PlayerStatsListener(balanceService, questService), this);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("is")) {
            return handleIslandCommand(sender, label, args);
        }

        if (command.getName().equalsIgnoreCase("ah")) {
            return handleAuctionCommand(sender, label, args);
        }

        if (command.getName().equalsIgnoreCase("hud")) {
            return handleHudCommand(sender, label, args);
        }

        if (command.getName().equalsIgnoreCase("questie")) {
            return handleQuestieCommand(sender, label, args);
        }

        if (!command.getName().equalsIgnoreCase("wosblock") && !command.getName().equalsIgnoreCase("wos")) {
            return false;
        }

        if (!sender.hasPermission("wosblock.admin")) {
            sender.sendMessage("You do not have permission to use WoSBlock admin commands.");
            return true;
        }

        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sender.sendMessage("WoSBlock admin commands:");
            sender.sendMessage("/" + label + " reload");
            sender.sendMessage("/" + label + " listcustom");
            sender.sendMessage("/" + label + " givescroll <id> [amount]");
            sender.sendMessage("/" + label + " giveenchant <id> [amount]");
            sender.sendMessage("/" + label + " givetrophy <tier>");
            sender.sendMessage("/" + label + " giveenderworldegg [amount]");
            return true;
        }

        if (args.length == 1 && args[0].equalsIgnoreCase("listcustom")) {
            return listCustomItems(sender, label);
        }

        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            reloadConfig();
            saveExtraConfigDefaults();
            loadExtraConfigs();
            HandlerList.unregisterAll(this);
            if (voidRecoveryService != null) {
                voidRecoveryService.stop();
            }
            if (hudService != null) {
                hudService.stop();
            }
            if (boundaryService != null) {
                boundaryService.stop();
            }
            if (automationListener != null) {
                automationListener.stop();
            }
            if (storage != null) {
                storage.close();
            }
            loadServices();
            registerListeners();
            sender.sendMessage("WoSBlock configuration reloaded.");
            return true;
        }
        if (sender instanceof Player player && args.length >= 2 && args[0].equalsIgnoreCase("givescroll")) {
            int amount = args.length >= 3 ? parseAmount(args[2]) : 1;
            player.getInventory().addItem(scrollService.createScroll(args[1], amount));
            player.sendMessage("Given scroll: " + args[1]);
            return true;
        }
        if (sender instanceof Player player && args.length >= 2 && args[0].equalsIgnoreCase("giveenchant")) {
            int amount = args.length >= 3 ? parseAmount(args[2]) : 1;
            player.getInventory().addItem(customEnchantService.createBook(args[1], amount));
            player.sendMessage("Given custom enchant book: " + args[1]);
            return true;
        }
        if (sender instanceof Player player && args.length >= 2 && args[0].equalsIgnoreCase("givetrophy")) {
            player.getInventory().addItem(trophyService.createTrophy(args[1]));
            player.sendMessage("Given trophy: " + args[1]);
            return true;
        }
        if (sender instanceof Player player && args.length >= 1 && args[0].equalsIgnoreCase("giveenderworldegg")) {
            int amount = args.length >= 2 ? parseAmount(args[1]) : 1;
            player.getInventory().addItem(itemFactory.enderWorldEgg(amount));
            player.sendMessage("Given EnderWorld Egg x" + amount + ".");
            return true;
        }
        sender.sendMessage("Usage: /" + label + " reload|listcustom|givescroll <id> [amount]|giveenchant <id> [amount]|givetrophy <tier>|giveenderworldegg [amount]");
        return true;
    }

    private boolean listCustomItems(CommandSender sender, String label) {
        List<String> scrollIds = sectionKeys("scrolls.yml", "scrolls");
        List<String> enchantIds = sectionKeys("enchants.yml", "custom-enchants");
        sender.sendMessage("WoSBlock custom item IDs:");
        sender.sendMessage("Scrolls: " + (scrollIds.isEmpty() ? "(none)" : String.join(", ", scrollIds)));
        sender.sendMessage("Give scroll: /" + label + " givescroll <id> [amount]");
        sender.sendMessage("Custom enchants: " + (enchantIds.isEmpty() ? "(none)" : String.join(", ", enchantIds)));
        sender.sendMessage("Give enchant: /" + label + " giveenchant <id> [amount]");
        return true;
    }

    private List<String> sectionKeys(String fileName, String path) {
        ConfigurationSection section = extraConfig(fileName).getConfigurationSection(path);
        if (section == null) {
            return List.of();
        }
        List<String> keys = new ArrayList<>(section.getKeys(false));
        Collections.sort(keys);
        return keys;
    }

    private boolean handleIslandCommand(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use island commands.");
            return true;
        }

        if (args.length == 1 && (args[0].equalsIgnoreCase("start") || args[0].equalsIgnoreCase("create"))) {
            islandService.startIsland(player);
            return true;
        }

        if (args.length == 1 && args[0].equalsIgnoreCase("home")) {
            islandService.cachedIsland(player.getUniqueId()).ifPresentOrElse(
                island -> player.teleport(island.spawnLocation()),
                () -> player.sendMessage("You do not have a loaded island yet. Use /" + label + " start.")
            );
            return true;
        }

        if (args.length == 1 && args[0].equalsIgnoreCase("leave")) {
            islandService.leaveIsland(player);
            return true;
        }

        if (args.length == 1 && args[0].equalsIgnoreCase("clear")) {
            islandService.clearIsland(player);
            return true;
        }

        if (args.length == 1 && args[0].equalsIgnoreCase("rebuild")) {
            islandService.rebuildIsland(player);
            return true;
        }

        if (args.length == 1 && args[0].equalsIgnoreCase("balance")) {
            if (islandService.islandAt(player.getLocation()).isEmpty()) {
                player.sendMessage("Island balance is only available while you are on an island.");
                return true;
            }
            player.sendMessage("Balance: " + balanceService.balance(player) + " coins.");
            return true;
        }

        if (args.length == 1 && args[0].equalsIgnoreCase("compact")) {
            boolean enabled = compactorService.toggle(player);
            player.sendMessage("Auto-compactor " + (enabled ? "enabled." : "disabled."));
            return true;
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("setwaypoint")) {
            int slot = parseWaypointSlot(args[1]);
            if (slot == -1) {
                player.sendMessage("Waypoint must be 1, 2, or 3.");
                return true;
            }
            islandService.islandAt(player.getLocation()).ifPresentOrElse(island -> {
                if (!islandService.isOwnerOrTrusted(player, island)) {
                    player.sendMessage("Only the island owner or trusted members can set waypoints.");
                    return;
                }
                island.setWaypoint(slot, player.getLocation());
                islandService.save(island);
                player.sendMessage("Waypoint " + slot + " saved.");
            }, () -> player.sendMessage("You must be on your island to set a waypoint."));
            return true;
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("waypoint")) {
            int slot = parseWaypointSlot(args[1]);
            if (slot == -1) {
                player.sendMessage("Waypoint must be 1, 2, or 3.");
                return true;
            }
            islandService.cachedIsland(player.getUniqueId()).ifPresentOrElse(island -> {
                org.bukkit.Location waypoint = island.waypointLocation(slot);
                if (waypoint == null) {
                    player.sendMessage("Waypoint " + slot + " is not set.");
                    return;
                }
                player.teleport(waypoint);
            }, () -> player.sendMessage("Start an island first with /" + label + " start."));
            return true;
        }

        if (args.length == 1 && args[0].equalsIgnoreCase("settings")) {
            islandService.cachedIsland(player.getUniqueId()).ifPresentOrElse(
                island -> player.sendMessage("Visit mode set to " + islandService.cycleVisitMode(island).name() + "."),
                () -> player.sendMessage("Start an island first with /" + label + " start.")
            );
            return true;
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("trust")) {
            islandService.cachedIsland(player.getUniqueId()).ifPresentOrElse(island -> {
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) {
                    player.sendMessage("That player must be online to trust them.");
                    return;
                }
                islandService.trust(island, target.getUniqueId());
                player.sendMessage("Trusted " + target.getName() + " on your island.");
            }, () -> player.sendMessage("Start an island first with /" + label + " start."));
            return true;
        }

        player.sendMessage("Usage: /" + label + " start|home|leave|clear|rebuild|settings|trust <player>|balance|compact|setwaypoint <1-3>|waypoint <1-3>");
        return true;
    }

    private boolean handleAuctionCommand(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use auction commands.");
            return true;
        }
        if (islandService.islandAt(player.getLocation()).isEmpty()) {
            player.sendMessage("Auction House commands are only available while you are on an island.");
            return true;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("sell")) {
            try {
                double price = Double.parseDouble(args[1]);
                player.sendMessage(auctionHouseService.list(player, player.getInventory().getItemInMainHand(), price));
            } catch (NumberFormatException ex) {
                player.sendMessage("Price must be a number.");
            }
            return true;
        }
        player.sendMessage("Usage: /" + label + " sell <price>");
        return true;
    }

    private boolean handleHudCommand(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use HUD commands.");
            return true;
        }
        if (args.length == 1 && args[0].equalsIgnoreCase("toggle")) {
            boolean shown = hudService.toggleHud(player);
            player.sendMessage(shown && !hudService.canDisplay(player)
                ? "HUD enabled. It will show when you are on an island."
                : "HUD " + (shown ? "shown." : "hidden."));
            return true;
        }
        player.sendMessage("Usage: /" + label + " toggle");
        return true;
    }

    private boolean handleQuestieCommand(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use Questie commands.");
            return true;
        }
        if (args.length == 1 && args[0].equalsIgnoreCase("toggle")) {
            boolean shown = hudService.toggleQuestie(player);
            player.sendMessage(shown && !hudService.canDisplay(player)
                ? "Questie enabled. It will show when you are on an island."
                : "Questie " + (shown ? "shown." : "hidden."));
            return true;
        }
        player.sendMessage("Usage: /" + label + " toggle");
        return true;
    }

    private int parseAmount(String value) {
        try {
            return Math.max(1, Integer.parseInt(value));
        } catch (NumberFormatException ex) {
            return 1;
        }
    }

    private int parseWaypointSlot(String value) {
        try {
            int slot = Integer.parseInt(value);
            return slot >= 1 && slot <= 3 ? slot : -1;
        } catch (NumberFormatException ex) {
            return -1;
        }
    }

    public FileConfiguration extraConfig(String name) {
        return extraConfigs.getOrDefault(name, getConfig());
    }

    private void saveExtraConfigDefaults() {
        for (String fileName : java.util.List.of("quests.yml", "fishing.yml", "scrolls.yml", "enchants.yml", "generator.yml", "black-market.yml")) {
            File file = new File(getDataFolder(), fileName);
            if (!file.exists()) {
                saveResource(fileName, false);
            }
        }
    }

    private void loadExtraConfigs() {
        extraConfigs.clear();
        for (String fileName : java.util.List.of("quests.yml", "fishing.yml", "scrolls.yml", "enchants.yml", "generator.yml", "black-market.yml")) {
            extraConfigs.put(fileName, YamlConfiguration.loadConfiguration(new File(getDataFolder(), fileName)));
        }
    }
}
