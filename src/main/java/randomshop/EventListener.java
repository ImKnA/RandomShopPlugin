package main.java.randomshop;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Arrays;
import java.util.UUID;

public class EventListener implements Listener {
    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final ShopManager shopManager;
    private final InventoryManager inventoryManager;
    private final EconomyManager economyManager;

    public EventListener(JavaPlugin plugin, ConfigManager configManager, ShopManager shopManager, InventoryManager inventoryManager, EconomyManager economyManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.shopManager = shopManager;
        this.inventoryManager = inventoryManager;
        this.economyManager = economyManager;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        int slot = e.getSlot();
        Inventory topInventory = e.getView().getTopInventory();
        Component title = e.getView().title();
        if (title.equals(configManager.getLegacySerializer().deserialize(plugin.getConfig().getString("point-shop.title", "&bCửa Hàng Điểm")))) {
            e.setCancelled(true);
            if (slot == configManager.getPointBackButtonSlot()) {
                inventoryManager.openMainShopInventory(p); // Call the new method
                return;
            }
            int index = configManager.getPointShopItemSlots().indexOf(slot);
            if (index >= 0 && index < shopManager.getPointItems().size()) {
                PointItem pointItem = shopManager.getPointItems().get(index);
                int points = configManager.getPlayerPoints().getOrDefault(p.getUniqueId(), 0);
                if (points >= pointItem.getPoints()) {
                    configManager.getPlayerPoints().put(p.getUniqueId(), points - pointItem.getPoints());
                    p.getInventory().addItem(pointItem.getItem().clone());
                    p.sendMessage(configManager.getMessage("point-purchase-success", "item", pointItem.getName(), "points", String.valueOf(pointItem.getPoints())));
                    p.playSound(p.getLocation(), configManager.getPurchaseSound(), configManager.getSoundVolume(), configManager.getSoundPitch());
                    if (pointItem.getCommands() != null) {
                        for (String cmd : pointItem.getCommands()) {
                            String command = cmd.replace("%player%", p.getName());
                            if (command.startsWith("console: ")) {
                                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command.substring(9));
                            } else if (command.startsWith("player: ")) {
                                p.performCommand(command.substring(8));
                            } else {
                                Bukkit.dispatchCommand(p, command);
                            }
                        }
                    }
                } else {
                    p.sendMessage(configManager.getMessage("not-enough-points", "points", String.valueOf(pointItem.getPoints()), "balance", String.valueOf(points)));
                    p.playSound(p.getLocation(), configManager.getNoMoneySound(), configManager.getNoMoneyVolume(), configManager.getNoMoneyPitch());
                }
            }
            return;
        }
        if (!isShopInventory(topInventory)) return;
        e.setCancelled(true);
        if (slot == configManager.getOpenPointShopSlot()) {
            Inventory pointShop = inventoryManager.createPointShopInventory(p.getUniqueId());
            p.openInventory(pointShop);
            p.playSound(p.getLocation(), Sound.BLOCK_CHEST_OPEN, 1.0f, 1.2f);
            return;
        }
        Integer[] validSlots = {10, 11, 12, 13, 14};
        if (Arrays.asList(validSlots).contains(slot)) {
            ItemStack item = e.getInventory().getItem(slot);
            if (item == null) return;
            for (ShopItem shopItem : shopManager.getShopItems()) {
                if (shopItem.getItem().getType() == item.getType()) {
                    int purchases = configManager.getPlayerPurchases().getOrDefault(p.getUniqueId(), 0);
                    if (configManager.getMaxPurchases() > 0 && purchases >= configManager.getMaxPurchases()) {
                        p.sendMessage(configManager.getMessage("purchase-limit-reached", "limit", String.valueOf(configManager.getMaxPurchases())));
                        p.playSound(p.getLocation(), configManager.getLimitReachedSound(), configManager.getLimitReachedVolume(), configManager.getLimitReachedPitch());
                        return;
                    }
                    if (economyManager.getEconomy().getBalance(p) >= shopItem.getPrice()) {
                        economyManager.getEconomy().withdrawPlayer(p, shopItem.getPrice());
                        p.getInventory().addItem(shopItem.getItem().clone());
                        p.sendMessage(configManager.getMessage("purchase-success", "item", shopItem.getName(), "price", String.valueOf(shopItem.getPrice())));
                        p.playSound(p.getLocation(), configManager.getPurchaseSound(), configManager.getSoundVolume(), configManager.getSoundPitch());
                        configManager.getPlayerPurchases().put(p.getUniqueId(), purchases + 1);
                        configManager.getPlayerNoPurchaseResets().put(p.getUniqueId(), 0);
                        inventoryManager.updateShopInfo(p, e.getInventory());
                    } else {
                        p.sendMessage(configManager.getMessage("not-enough-money", "price", String.valueOf(shopItem.getPrice()), "balance", String.valueOf(economyManager.getEconomy().getBalance(p))));
                        p.playSound(p.getLocation(), configManager.getNoMoneySound(), configManager.getNoMoneyVolume(), configManager.getNoMoneyPitch());
                    }
                    break;
                }
            }
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent e) {
        if (isShopInventory(e.getInventory())) {
            UUID uuid = e.getPlayer().getUniqueId();
            inventoryManager.getPlayersViewingShop().remove(uuid);
            if (e.getPlayer() instanceof Player) {
                ((Player) e.getPlayer()).playSound(e.getPlayer().getLocation(), configManager.getCloseShopSound(), 1.0f, 1.0f);
            }
        }
    }

    private boolean isShopInventory(Inventory inventory) {
        return inventory.getHolder() == null && inventory.getSize() == configManager.getInventorySize();
    }
}