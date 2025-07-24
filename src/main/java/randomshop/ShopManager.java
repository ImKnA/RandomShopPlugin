package main.java.randomshop;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.meta.ItemMeta;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

public class ShopManager {
    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final EconomyManager economyManager;
    private final List<ShopItem> shopItems = new ArrayList<>();
    private final List<PointItem> pointItems = new ArrayList<>();
    private InventoryManager inventoryManager;
    private Inventory shopInventory;
    private long nextRefreshTime;
    private final Random random = new Random();

    public ShopManager(JavaPlugin plugin, ConfigManager configManager, EconomyManager economyManager, InventoryManager inventoryManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.economyManager = economyManager;
        this.inventoryManager = inventoryManager;
        loadShopItems();
        loadPointItems();

        int size = configManager.getInventorySize();
        if (size <= 0 || size > 54 || size % 9 != 0) {
            size = 27; // Mặc định 3 hàng nếu không hợp lệ
            plugin.getLogger().warning("Kích thước kho đồ không hợp lệ trong config, sử dụng mặc định 27 ô");
        }

        // Tạo kho đồ với kích thước đã validate
        shopInventory = Bukkit.createInventory(
                null,
                size,
                configManager.getLegacySerializer().deserialize(
                        plugin.getConfig().getString("ui-settings.title", "&5Cửa Hàng Ngẫu Nhiên")
                )
        );
    }

    public void loadShopItems() {
        shopItems.clear();
        for (Rarity rarity : Rarity.values()) {
            loadRarityItems(rarity);
        }
        shopItems.sort(Comparator.comparingInt(item -> item.getRarity().getWeight()));
        plugin.getLogger().info("Đã tải " + shopItems.size() + " vật phẩm cửa hàng từ config.yml.");
    }

    public void setInventoryManager(InventoryManager inventoryManager) {
        this.inventoryManager = inventoryManager;
    }

    private void loadRarityItems(Rarity rarity) {
        String configKey = rarity.getConfigKey();
        double catProb = plugin.getConfig().getDouble("items." + configKey + ".probability", 0.0);
        List<?> items = plugin.getConfig().getList("items." + configKey + ".items", new ArrayList<>());

        for (Object obj : items) {
            if (!(obj instanceof Map<?, ?> itemMap)) continue;
            try {
                double price = ((Number) itemMap.get("price")).doubleValue();
                double prob = ((Number) itemMap.get("probability")).doubleValue();
                ItemStack itemStack = parseItem(itemMap, configKey);
                if (itemStack != null && itemStack.getItemMeta() != null) {
                    ItemMeta meta = itemStack.getItemMeta();
                    String displayName = meta.hasDisplayName()
                            ? configManager.getLegacySerializer().serialize(Objects.requireNonNull(meta.displayName()))
                            : itemStack.getType().name();
                    boolean useDefaultLore = !itemMap.containsKey("use-default-lore") || Boolean.parseBoolean(String.valueOf(itemMap.get("use-default-lore")));
                    shopItems.add(new ShopItem(itemStack, price, displayName, rarity, prob, catProb, useDefaultLore));
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Lỗi vật phẩm " + configKey + ": " + e.getMessage());
            }
        }
    }

    public void loadPointItems() {
        pointItems.clear();
        List<?> pointItemsConfig = plugin.getConfig().getList("point-items", new ArrayList<>());
        for (Object obj : pointItemsConfig) {
            if (!(obj instanceof Map<?, ?> i)) continue;
            try {
                int points = ((Number) i.get("points")).intValue();
                ItemStack itemStack = parseItem(i, "point-item");
                if (itemStack != null && itemStack.getItemMeta() != null) {
                    ItemMeta meta = itemStack.getItemMeta();
                    String displayName = meta.hasDisplayName()
                            ? configManager.getLegacySerializer().serialize(Objects.requireNonNull(meta.displayName()))
                            : itemStack.getType().name();
                    List<String> commands = new ArrayList<>();
                    Object rawCommands = i.get("commands");
                    if (rawCommands instanceof List<?>) {
                        for (Object o : (List<?>) rawCommands) {
                            if (o != null) commands.add(String.valueOf(o));
                        }
                    }
                    boolean useDefaultLore = !i.containsKey("use-default-lore") || Boolean.parseBoolean(String.valueOf(i.get("use-default-lore")));
                    pointItems.add(new PointItem(itemStack, points, displayName, commands, useDefaultLore));
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Lỗi vật phẩm điểm: " + i.get("material") + ": " + e.getMessage());
            }
        }
        plugin.getLogger().info("Đã tải " + pointItems.size() + " vật phẩm điểm từ config.yml.");
    }

    private ItemStack parseItem(Map<?, ?> itemData, String context) {
        try {
            Material material = Material.matchMaterial(String.valueOf(itemData.get("material")));
            if (material == null) {
                plugin.getLogger().warning("Material không tồn tại: " + itemData.get("material"));
                return null;
            }
            int amount = ((Number) itemData.get("amount")).intValue();
            ItemStack itemStack = new ItemStack(material, amount);
            ItemMeta meta = itemStack.getItemMeta();
            if (meta != null) {
                setupItemMeta(meta, itemData);
                if (itemData.containsKey("enchants")) {
                    for (Object ench : (List<?>) itemData.get("enchants")) {
                        String[] parts = String.valueOf(ench).split(":");
                        Enchantment enchant = Registry.ENCHANTMENT.get(NamespacedKey.minecraft(parts[0].toLowerCase()));
                        if (enchant == null) {
                            plugin.getLogger().warning("Enchant không hợp lệ " + parts[0]);
                            continue;
                        }
                        try {
                            meta.addEnchant(enchant, Integer.parseInt(parts[1]), true);
                        } catch (IllegalArgumentException e) {
                            plugin.getLogger().warning("Cấp độ enchant không hợp lệ " + parts[1]);
                        }
                    }
                }
                if (itemData.containsKey("flags")) {
                    for (Object f : (List<?>) itemData.get("flags")) {
                        try {
                            ItemFlag flag = ItemFlag.valueOf(String.valueOf(f).toUpperCase());
                            meta.addItemFlags(flag);
                        } catch (IllegalArgumentException e) {
                            plugin.getLogger().warning("Invalid item flag " + f);
                        }
                    }
                }
                itemStack.setItemMeta(meta);
            }
            return itemStack;
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to parse item " + itemData.get("material") + " in " + context);
            return null;
        }
    }

    private void setupItemMeta(ItemMeta meta, Map<?, ?> itemData) {
        try {
            if (itemData.containsKey("name")) {
                String name = String.valueOf(itemData.get("name"));
                if (!name.isEmpty()) {
                    meta.displayName(
                            configManager.getLegacySerializer().deserialize(name)
                                    .decoration(TextDecoration.ITALIC, false) // THÊM DÒNG NÀY
                    );
                }
            }
            if (itemData.containsKey("lore")) {
                List<Component> lore = new ArrayList<>();
                for (Object line : (List<?>) itemData.get("lore")) {
                    lore.add(
                            configManager.getLegacySerializer().deserialize(String.valueOf(line))
                                    .decoration(TextDecoration.ITALIC, false) // THÊM DÒNG NÀY
                    );
                }
                meta.lore(lore);
            }
            if (itemData.containsKey("flags")) {
                for (Object flag : (List<?>) itemData.get("flags")) {
                    try {
                        ItemFlag itemFlag = ItemFlag.valueOf(String.valueOf(flag).toUpperCase());
                        meta.addItemFlags(itemFlag);
                    } catch (IllegalArgumentException e) {
                        plugin.getLogger().warning("Item flag không hợp lệ: " + flag);
                    }
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Lỗi khi thiết lập ItemMeta: " + e.getMessage());
        }
    }

    public void refreshShop() {
        shopInventory = Bukkit.createInventory(null, configManager.getInventorySize(), configManager.getLegacySerializer().deserialize(
                plugin.getConfig().getString("ui-settings.title", "&5Cửa Hàng Ngẫu Nhiên")
        ));
        shopInventory.clear();
        for (UUID uuid : new HashSet<>(inventoryManager.getPlayersViewingShop())) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                player.closeInventory();
                player.sendMessage(configManager.getMessage("shop-refreshed"));
            }
        }
        inventoryManager.getPlayersViewingShop().clear();
        for (Player p : Bukkit.getOnlinePlayers()) {
            UUID uuid = p.getUniqueId();
            if (!configManager.getPlayerPurchases().containsKey(uuid) || configManager.getPlayerPurchases().getOrDefault(uuid, 0) == 0) {
                int resets = configManager.getPlayerNoPurchaseResets().getOrDefault(uuid, 0) + 1;
                configManager.getPlayerNoPurchaseResets().put(uuid, resets);
                if (resets >= configManager.getResetsForPoint()) {
                    configManager.getPlayerPoints().put(uuid, configManager.getPlayerPoints().getOrDefault(uuid, 0) + 1);
                    configManager.getPlayerNoPurchaseResets().put(uuid, 0);
                    p.sendMessage(configManager.getMessage("points-received",
                            "resets", String.valueOf(configManager.getResetsForPoint())));
                }
            } else {
                configManager.getPlayerNoPurchaseResets().put(uuid, 0);
            }
        }
        configManager.getPlayerPurchases().clear();
        nextRefreshTime = System.currentTimeMillis() + (configManager.getRefreshInterval() * 1000L);
        ItemStack border = new ItemStack(configManager.getBorderMaterial());
        ItemMeta borderMeta = border.getItemMeta();
        if (borderMeta != null) {
            borderMeta.displayName(configManager.getLegacySerializer().deserialize(
                    plugin.getConfig().getString("ui-settings.border-item.name", " ")
            ));
            border.setItemMeta(borderMeta);
        }
        for (int slot : configManager.getBorderSlots()) {
            if (slot >= 0 && slot < configManager.getInventorySize()) {
                shopInventory.setItem(slot, border);
            }
        }
        ItemStack pointShopButton = new ItemStack(Material.NETHER_STAR);
        ItemMeta meta = pointShopButton.getItemMeta();
        if (meta != null) {
            meta.displayName(configManager.getLegacySerializer().deserialize("&dMở Cửa Hàng Điểm")
                    .decoration(TextDecoration.ITALIC, false));
            pointShopButton.setItemMeta(meta);
        }
        if (configManager.getOpenPointShopSlot() >= 0 && configManager.getOpenPointShopSlot() < configManager.getInventorySize()) {
            shopInventory.setItem(configManager.getOpenPointShopSlot(), pointShopButton);
        }
        int itemCount = configManager.getShopMinItems() + random.nextInt(configManager.getShopMaxItems() - configManager.getShopMinItems() + 1);
        itemCount = Math.min(itemCount, 5);
        List<ShopItem> selectedItems = new ArrayList<>();
        for (int i = 0; i < itemCount; i++) {
            ShopItem item = selectRandomItem();
            if (item != null) {
                selectedItems.add(item);
            }
        }
        List<Integer> itemSlots = configManager.getShopItemSlots();
        for (int i = 0; i < Math.min(selectedItems.size(), itemSlots.size()); i++) {
            int slot = itemSlots.get(i);
            if (slot >= 0 && slot < configManager.getInventorySize()) {
                shopInventory.setItem(slot, inventoryManager.createShopDisplayItem(selectedItems.get(i)));
            }
        }
        int infoSlot = plugin.getConfig().getInt("ui-settings.slot-layout.info", 22);
        if (infoSlot >= 0 && infoSlot < configManager.getInventorySize()) {
            shopInventory.setItem(infoSlot, inventoryManager.createInfoItem(null));
        }
    }

    private ShopItem selectRandomItem() {
        if (shopItems.isEmpty()) return null;
        List<ShopItem> sortedItems = new ArrayList<>(shopItems);
        sortedItems.sort(Comparator.comparingInt(item -> item.getRarity().getWeight()));
        double totalProb = sortedItems.stream()
                .mapToDouble(item -> item.getCatProbability() * item.getProbability())
                .sum();
        double roll = random.nextDouble() * totalProb;
        double cumulative = 0;
        for (ShopItem item : sortedItems) {
            cumulative += item.getCatProbability() * item.getProbability();
            if (roll <= cumulative) {
                return item;
            }
        }
        return null;
    }

    public Inventory getShopInventory() {
        return shopInventory;
    }

    public List<ShopItem> getShopItems() {
        return shopItems;
    }

    public List<PointItem> getPointItems() {
        return pointItems;
    }

    public long getNextRefreshTime() {
        return nextRefreshTime;
    }
}