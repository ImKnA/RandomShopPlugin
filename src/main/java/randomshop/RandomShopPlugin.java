package main.java.randomshop;

import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

public class RandomShopPlugin extends JavaPlugin {
    private ConfigManager configManager;
    private InventoryManager inventoryManager;
    private ShopManager shopManager;


    @Override
    public void onEnable() {
        configManager = new ConfigManager(this);
        configManager.reloadAll();

        EconomyManager economyManager = new EconomyManager(this);
        ShopManager shopManager = new ShopManager(this, configManager, economyManager, null);
        inventoryManager = new InventoryManager(this, configManager, shopManager, economyManager);
        shopManager.setInventoryManager(inventoryManager); // Resolve circular dependency
        CommandHandler commandHandler = new CommandHandler(this, configManager, shopManager, inventoryManager);
        EventListener eventListener = new EventListener(this, configManager, shopManager, inventoryManager, economyManager);

        // Setup economy
        if (!economyManager.setupEconomy()) {
            getLogger().severe("Vault plugin not found or economy provider not registered! Disabling plugin.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Register command
        PluginCommand command = getCommand("randomshop");
        if (command == null) {
            getLogger().severe("Command 'randomshop' not found in plugin.yml! Disabling plugin.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        command.setExecutor(commandHandler);

        // Register events
        getServer().getPluginManager().registerEvents(eventListener, this);

        // Initialize shop
        shopManager.refreshShop();

        long refreshIntervalTicks = configManager.getRefreshInterval() * 20L; // Chuyển giây sang ticks (20 ticks = 1 giây)
        if (refreshIntervalTicks > 0) { // Đảm bảo khoảng thời gian hợp lệ
            new BukkitRunnable() {
                @Override
                public void run() {
                    getLogger().info("Tự động làm mới cửa hàng...");
                    shopManager.refreshShop();
                }
            }.runTaskTimer(this, refreshIntervalTicks, refreshIntervalTicks);
            getLogger().info("Tác vụ làm mới cửa hàng tự động đã được lên lịch mỗi " + configManager.getRefreshInterval() + " giây.");
        } else {
            getLogger().warning("Khoảng thời gian làm mới cửa hàng (refresh-interval) không hợp lệ hoặc bằng 0. Tự động làm mới sẽ không hoạt động.");
        }
    }

    @Override
    public void onDisable() {
        // Close all open inventories and cancel tasks
        if (inventoryManager != null) {
            inventoryManager.closeAllInventories();
        }

        // Save player data synchronously
        if (configManager != null) {
            configManager.savePlayerDataSync();
        }
        Bukkit.getScheduler().cancelTasks(this);
    }
}