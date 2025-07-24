package main.java.randomshop;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

public class CommandHandler implements CommandExecutor {
    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final ShopManager shopManager;
    private final InventoryManager inventoryManager;

    public CommandHandler(JavaPlugin plugin, ConfigManager configManager, ShopManager shopManager, InventoryManager inventoryManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.shopManager = shopManager;
        this.inventoryManager = inventoryManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(configManager.getMessage("player-only"));
                return true;
            }
            plugin.reloadConfig();
            configManager.loadConfig();
            configManager.loadMessages();
            shopManager.loadShopItems();
            shopManager.loadPointItems();
            configManager.getPlayerPurchases().clear();
            configManager.getPlayerNoPurchaseResets().clear();
            configManager.getPlayerPoints().clear();
            configManager.loadPlayerData();
            shopManager.refreshShop();
            sender.sendMessage(configManager.getMessage("command-reload"));
            return true;
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("points")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(configManager.getMessage("player-only"));
                return true;
            }
            int points = configManager.getPlayerPoints().getOrDefault(player.getUniqueId(), 0);
            player.sendMessage(configManager.getMessage("points-balance", "points", String.valueOf(points)));
            return true;
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("reset")) {
            if (!sender.hasPermission("randomshop.reset")) {
                sender.sendMessage(configManager.getMessage("no-permission"));
                return true;
            }
            shopManager.refreshShop();
            sender.sendMessage(configManager.getMessage("command-reset"));
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(configManager.getMessage("player-only"));
            return true;
        }
        inventoryManager.openMainShopInventory(player); // Call the new method
        return true;
    }
}