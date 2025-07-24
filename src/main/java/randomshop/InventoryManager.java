package main.java.randomshop;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import java.util.*;

public class InventoryManager {
    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final ShopManager shopManager;
    private final EconomyManager economyManager;
    private final Map<UUID, BukkitTask> updateTasks = new HashMap<>();
    private final Set<UUID> playersViewingShop = new HashSet<>();

    public InventoryManager(JavaPlugin plugin, ConfigManager configManager, ShopManager shopManager, EconomyManager economyManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.shopManager = shopManager;
        this.economyManager = economyManager;
    }

    public void updateShopInfo(Player player, Inventory inventory) {
        inventory.setItem(configManager.getInfoItemSlot(), createInfoItem(player.getUniqueId()));
    }

    public ItemStack createShopDisplayItem(ShopItem item) {
        ItemStack clone = item.getItem().clone();
        ItemMeta meta = clone.getItemMeta();
        if (meta != null) {
            String coloredName = getRarityColor(item.getRarity()) + item.getName() + "&r";
            meta.displayName(
                    configManager.getLegacySerializer().deserialize(coloredName)
                            .decoration(TextDecoration.ITALIC, false)
            );
            List<Component> lore = new ArrayList<>();
            if (meta.hasLore() && meta.lore() != null && !Objects.requireNonNull(meta.lore()).isEmpty()) {
                for (Component line : Objects.requireNonNull(meta.lore())) {
                    lore.add(line.decoration(TextDecoration.ITALIC, false));
                }
                if (item.UseDefaultLore()) {
                    lore.add(Component.empty());
                } else {
                    meta.lore(lore);
                    clone.setItemMeta(meta);
                    return clone;
                }
            }
            if (item.UseDefaultLore()) {
                for (String line : configManager.getDefaultItemLore()) {
                    String formatted = line
                            .replace("%price%", String.format("%,.1f", item.getPrice()))
                            .replace("%amount%", String.valueOf(item.getItem().getAmount()))
                            .replace("%rarity%", item.getRarity().getConfigKey())
                            .replace("%rarity_color%", getRarityColor(item.getRarity()))
                            .replace("%enchants%", getEnchantmentDisplay(meta.getEnchants()))
                            .replace("%flags%", getFlagsDisplay(meta.getItemFlags()));
                    lore.add(
                            configManager.getLegacySerializer().deserialize(formatted)
                                    .decoration(TextDecoration.ITALIC, false)
                    );
                }
            }
            meta.lore(lore);
            clone.setItemMeta(meta);
        }
        return clone;
    }

    public Inventory createPointShopInventory(UUID uuid) {
        Inventory inv = Bukkit.createInventory(null, configManager.getPointInventorySize(),
                configManager.getLegacySerializer().deserialize(configManager.getPointShopTitle()));
        for (int i = 0; i < Math.min(shopManager.getPointItems().size(), configManager.getPointShopItemSlots().size()); i++) {
            inv.setItem(configManager.getPointShopItemSlots().get(i), createPointDisplayItem(shopManager.getPointItems().get(i)));
        }
        if (configManager.getPointBackButtonSlot() >= 0 && configManager.getPointBackButtonSlot() < configManager.getPointInventorySize()) {
            ItemStack back = new ItemStack(configManager.getPointBackButtonMaterial() != null ? configManager.getPointBackButtonMaterial() : Material.BARRIER);
            ItemMeta meta = back.getItemMeta();
            if (meta != null) {
                meta.displayName(configManager.getLegacySerializer().deserialize(configManager.getPointBackButtonName())
                        .decoration(TextDecoration.ITALIC, false));
                back.setItemMeta(meta);
            }
            inv.setItem(configManager.getPointBackButtonSlot(), back);
        }
        return inv;
    }

    public ItemStack createPointDisplayItem(PointItem item) {
        ItemStack clone = item.getItem().clone();
        ItemMeta meta = clone.getItemMeta();
        if (meta != null) {
            String displayName = item.getName() == null || item.getName().isBlank()
                    ? item.getItem().getType().name()
                    : item.getName();
            meta.displayName(
                    configManager.getLegacySerializer().deserialize(displayName)
                            .decoration(TextDecoration.ITALIC, false)
            );
            List<Component> lore = new ArrayList<>();
            if (meta.hasLore() && meta.lore() != null && !Objects.requireNonNull(meta.lore()).isEmpty()) {
                for (Component line : Objects.requireNonNull(meta.lore())) {
                    lore.add(line.decoration(TextDecoration.ITALIC, false));
                }
                if (item.UseDefaultLore()) {
                    lore.add(Component.empty());
                } else {
                    meta.lore(lore);
                    clone.setItemMeta(meta);
                    return clone;
                }
            }
            if (item.UseDefaultLore()) {
                List<String> templateLore = configManager.getPointItemLore();
                if (templateLore.isEmpty()) {
                    templateLore = List.of("&7Không có mô tả.");
                }
                for (String line : templateLore) {
                    lore.add(
                            configManager.getLegacySerializer().deserialize(line
                                    .replace("%points%", String.valueOf(item.getPoints()))
                                    .replace("%amount%", String.valueOf(item.getItem().getAmount()))
                                    .replace("%enchants%", getEnchantmentDisplay(meta.getEnchants()))
                                    .replace("%flags%", getFlagsDisplay(meta.getItemFlags()))
                            ).decoration(TextDecoration.ITALIC, false)
                    );
                }
            }
            meta.lore(lore);
            clone.setItemMeta(meta);
        }
        return clone;
    }

    public ItemStack createInfoItem(UUID playerUUID) {
        ItemStack infoItem = new ItemStack(configManager.getInfoMaterial());
        ItemMeta infoMeta = infoItem.getItemMeta();
        if (infoMeta != null) {
            int purchases = playerUUID != null ? configManager.getPlayerPurchases().getOrDefault(playerUUID, 0) : 0;
            int points = playerUUID != null ? configManager.getPlayerPoints().getOrDefault(playerUUID, 0) : 0;
            infoMeta.displayName(configManager.getLegacySerializer().deserialize(
                    plugin.getConfig().getString("ui-settings.info-item.name", "&bThông Tin Cửa Hàng")
            ).decoration(TextDecoration.ITALIC, false));
            List<Component> lore = new ArrayList<>();
            for (String line : configManager.getInfoLoreTemplate()) {
                String formatted = line
                        .replace("%time%", getTimeUntilRefresh())
                        .replace("%remaining_purchases%", configManager.getMaxPurchases() > 0 ?
                                String.valueOf(configManager.getMaxPurchases() - purchases) : "Không giới hạn")
                        .replace("%points%", String.valueOf(points));
                lore.add(configManager.getLegacySerializer().deserialize(formatted)
                        .decoration(TextDecoration.ITALIC, false));
            }
            infoMeta.lore(lore);
            infoItem.setItemMeta(infoMeta);
        }
        return infoItem;
    }

    private String getTimeUntilRefresh() {
        long secondsLeft = (shopManager.getNextRefreshTime() - System.currentTimeMillis()) / 1000;
        if (secondsLeft < 0) secondsLeft = 0;
        long minutes = secondsLeft / 60;
        long seconds = secondsLeft % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }

    private String getRarityColor(Rarity rarity) {
        String color = configManager.getRarityColor(rarity.getConfigKey(), null);
        if (color == null) {
            return switch (rarity) {
                case COMMON -> "&f";
                case RARE -> "&9";
                case LEGENDARY -> "&6";
            };
        }
        return color.startsWith("&") ? color : "&" + color;
    }

    private String getEnchantmentDisplay(Map<Enchantment, Integer> enchants) {
        if (enchants == null || enchants.isEmpty()) return "Không có";
        List<String> enchantList = new ArrayList<>();
        for (Map.Entry<Enchantment, Integer> entry : enchants.entrySet()) {
            String key = entry.getKey().getKey().getKey().toLowerCase();
            String displayName = configManager.getEnchantName(key);
            enchantList.add(displayName + " " + entry.getValue());
        }
        return String.join(", ", enchantList);
    }

    private String getFlagsDisplay(Set<ItemFlag> flags) {
        if (flags == null || flags.isEmpty()) return "Không có";
        List<String> flagList = new ArrayList<>();
        for (ItemFlag flag : flags) {
            String key = flag.name();
            String displayName = configManager.getFlagName(key);
            flagList.add(displayName);
        }
        return String.join(", ", flagList);
    }

    public void startUpdateTask(Player player, Inventory inventory) {
        // ...
        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline() || !playersViewingShop.contains(player.getUniqueId())) {
                    cancel();
                    updateTasks.remove(player.getUniqueId());
                    return;
                }
                // Chỉ cần setItem là đủ, Bukkit sẽ tự động cập nhật cho người chơi
                inventory.setItem(configManager.getInfoItemSlot(), createInfoItem(player.getUniqueId()));
                player.playSound(player.getLocation(), configManager.getUpdateInfoSound(), 0.3f, 1.5f);

            }
        }.runTaskTimer(plugin, 0L, 20L);
        updateTasks.put(player.getUniqueId(), task);
    }

    public void closeAllInventories() {
        for (UUID uuid : new HashSet<>(playersViewingShop)) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.getOpenInventory().getTopInventory().equals(shopManager.getShopInventory())) {
                player.closeInventory();
            }
        }
        updateTasks.values().forEach(BukkitTask::cancel);
        updateTasks.clear();
        playersViewingShop.clear();
    }

    public void openMainShopInventory(Player player) {
        Inventory playerShopInventory = Bukkit.createInventory(null, configManager.getInventorySize(),
                configManager.getLegacySerializer().deserialize(plugin.getConfig().getString("ui-settings.title", "&5Cửa Hàng Ngẫu Nhiên")));

        // Clone items from the main shop inventory to the player's temporary inventory
        for (int i = 0; i < Math.min(shopManager.getShopInventory().getSize(), playerShopInventory.getSize()); i++) {
            ItemStack item = shopManager.getShopInventory().getItem(i);
            if (item != null) {
                playerShopInventory.setItem(i, item.clone());
            }
        }

        // Set the info item
        playerShopInventory.setItem(configManager.getInfoItemSlot(), createInfoItem(player.getUniqueId()));

        // Open the inventory for the player
        player.openInventory(playerShopInventory);

        // Track the player viewing the shop
        playersViewingShop.add(player.getUniqueId());

        // Play sound
        player.playSound(player.getLocation(), configManager.getOpenShopSound(), 1.0f, 1.0f);

        // Start the update task for the info item
        startUpdateTask(player, playerShopInventory);
    }

    public Set<UUID> getPlayersViewingShop() {
        return playersViewingShop;
    }
}