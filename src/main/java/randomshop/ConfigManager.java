package main.java.randomshop;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.configuration.ConfigurationSection;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.plugin.java.JavaPlugin;
import java.io.File;
import java.io.IOException;
import java.util.*;
import net.kyori.adventure.text.Component;


public class ConfigManager {
    private final JavaPlugin plugin;
    private FileConfiguration config;
    private FileConfiguration messages;
    private String prefix;
    private int inventorySize;
    private String pointShopTitle;
    private List<String> defaultItemLore;
    private List<String> pointItemLore;
    private int infoItemSlot;
    private Map<String, String> rarityColors;
    private Map<String, String> enchantNames;
    private Map<String, String> flagNames;
    private List<Integer> borderSlots;
    private Material borderMaterial;
    private Material infoMaterial;
    private List<String> infoLoreTemplate;
    private int openPointShopSlot;
    private String refreshMessage;
    private String pointsMessage;
    private int maxPurchases;
    private int resetsForPoint;
    private int refreshInterval;
    private int shopMinItems;
    private int shopMaxItems;
    private final Map<UUID, Integer> playerPurchases = new HashMap<>();
    private final Map<UUID, Integer> playerNoPurchaseResets = new HashMap<>();
    private final Map<UUID, Integer> playerPoints = new HashMap<>();
    private int pointInventorySize;
    private int pointBackButtonSlot;
    private Material pointBackButtonMaterial;
    private String pointBackButtonName;
    private List<Integer> pointShopItemSlots;
    private Sound openShopSound;
    private Sound closeShopSound;
    private Sound updateInfoSound;
    private Sound purchaseSound;
    private Sound noMoneySound;
    private Sound limitReachedSound;
    private float soundVolume;
    private float soundPitch;
    private float noMoneyVolume;
    private float noMoneyPitch;
    private float limitReachedVolume;
    private float limitReachedPitch;
    private List<Integer> shopItemSlots;
    private final LegacyComponentSerializer legacySerializer = LegacyComponentSerializer.builder()
            .character('&') // Giữ hỗ trợ mã màu &
            .hexColors()    // THÊM DÒNG NÀY để hỗ trợ mã màu HEX
            .build();

    public ConfigManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void reloadAll() {
        plugin.getLogger().info("Bắt đầu tải lại tất cả cấu hình và dữ liệu.");
        plugin.saveDefaultConfig();
        plugin.reloadConfig(); // Tải lại config từ file

        loadConfig();

        // Tải các file khác
        loadMessages();
        loadPlayerData();
        plugin.getLogger().info("Đã tải lại tất cả các file cấu hình và dữ liệu.");
    }

    public void loadConfig() {
        config = plugin.getConfig();
        validateConfig();
        loadUISettings();
        loadPointShopUI();
        loadSounds();
        loadMessagesAndSettings();
        loadInventory(); // Phương thức này hiện tại chỉ tải các map và lore, không liên quan đến slot layout
    }

    // Phương thức này có thể đổi tên thành loadItemMetadata hoặc tương tự
    // vì nó tải thông tin bổ trợ cho vật phẩm, không phải bố cục inventory
    public void loadInventory(){
        this.pointShopTitle = config.getString("point-shop.title", "&bCửa Hàng Điểm");
        this.defaultItemLore = config.getStringList("item-lore");
        this.pointItemLore = config.getStringList("point-item-lore");
        // this.infoItemSlot = config.getInt("ui-settings.slot-layout.info", 22); // Đã chuyển lên loadUISettings()

        // Tải các map để tối ưu việc truy cập
        this.rarityColors = new HashMap<>();
        ConfigurationSection raritySection = config.getConfigurationSection("rarity-colors");
        if (raritySection != null) {
            for (String key : raritySection.getKeys(false)) {
                rarityColors.put(key, raritySection.getString(key));
            }
        }

        this.enchantNames = new HashMap<>();
        ConfigurationSection enchantSection = config.getConfigurationSection("enchant-names");
        if (enchantSection != null) {
            for (String key : enchantSection.getKeys(false)) {
                enchantNames.put(key, enchantSection.getString(key));
            }
        }

        this.flagNames = new HashMap<>();
        ConfigurationSection flagSection = config.getConfigurationSection("flag-names");
        if (flagSection != null) {
            for (String key : flagSection.getKeys(false)) {
                flagNames.put(key, flagSection.getString(key));
            }
        }
    }


    public void loadMessages() {
        File messagesFile = new File(plugin.getDataFolder(), "messages.yml");
        if (!messagesFile.exists()) {
            plugin.saveResource("messages.yml", false);
        }
        messages = YamlConfiguration.loadConfiguration(messagesFile);
        prefix = messages.getString("prefix", "");
    }

    public void loadPlayerData() {
        File file = new File(plugin.getDataFolder(), "purchases.yml");
        if (!file.exists()) {
            plugin.getLogger().warning("RandomShop: Tệp purchases.yml không tồn tại, tạo mới.");
            try {
                if (!file.getParentFile().mkdirs() || !file.createNewFile()) {
                    plugin.getLogger().warning("RandomShop: Không thể tạo tệp purchases.yml");
                    return;
                }
            } catch (IOException e) {
                plugin.getLogger().warning("RandomShop: Không thể tạo tệp purchases.yml: " + e.getMessage());
                return;
            }
        } else {
            plugin.getLogger().info("RandomShop: Tải dữ liệu từ purchases.yml.");
        }

        FileConfiguration config = YamlConfiguration.loadConfiguration(file);
        // Xóa các map trước khi tải lại để tránh dữ liệu cũ chồng chéo
        playerPurchases.clear();
        playerNoPurchaseResets.clear();
        playerPoints.clear();

        loadSection(config, "purchases", playerPurchases);
        loadSection(config, "no-purchase-resets", playerNoPurchaseResets);
        loadSection(config, "points", playerPoints);

        plugin.getLogger().info("RandomShop: Đã tải " + playerPurchases.size() + " lượt mua.");
        plugin.getLogger().info("RandomShop: Đã tải " + playerNoPurchaseResets.size() + " lượt reset không mua.");
        plugin.getLogger().info("RandomShop: Đã tải " + playerPoints.size() + " điểm người chơi.");
    }

    private void loadSection(FileConfiguration config, String section, Map<UUID, Integer> map) {
        if (config.contains(section)) {
            ConfigurationSection sectionData = config.getConfigurationSection(section);
            if (sectionData != null) {
                for (String key : sectionData.getKeys(false)) {
                    try {
                        UUID uuid = UUID.fromString(key);
                        int value = config.getInt(section + "." + key, 0);
                        map.put(uuid, value);
                    } catch (IllegalArgumentException e) {
                        plugin.getLogger().warning("UUID không hợp lệ trong " + section + ": " + key);
                    }
                }
            }
        }
    }

    public void savePlayerDataSync() {
        File file = new File(plugin.getDataFolder(), "purchases.yml");
        if (!file.getParentFile().exists() && !file.getParentFile().mkdirs()) {
            plugin.getLogger().warning("RandomShop: Không thể tạo thư mục cho purchases.yml khi lưu.");
            return;
        }
        FileConfiguration configToSave = new YamlConfiguration(); // Tạo một đối tượng config mới để lưu

        // Ghi dữ liệu từ các map vào đối tượng configToSave
        for (Map.Entry<UUID, Integer> entry : playerPurchases.entrySet()) {
            configToSave.set("purchases." + entry.getKey().toString(), entry.getValue());
        }
        for (Map.Entry<UUID, Integer> entry : playerNoPurchaseResets.entrySet()) {
            configToSave.set("no-purchase-resets." + entry.getKey().toString(), entry.getValue());
        }
        for (Map.Entry<UUID, Integer> entry : playerPoints.entrySet()) {
            configToSave.set("points." + entry.getKey().toString(), entry.getValue());
        }

        try {
            configToSave.save(file); // Lưu đối tượng config mới vào tệp
            plugin.getLogger().info("RandomShop: Đã lưu dữ liệu người chơi vào purchases.yml. Điểm đã lưu: " + playerPoints.size());
        } catch (IOException e) {
            plugin.getLogger().warning("RandomShop: Không thể lưu dữ liệu người chơi vào purchases.yml: " + e.getMessage());
        }
    }

    private void validateConfig() {
        if (!config.contains("items")) {
            plugin.getLogger().severe("Thiếu mục 'items' trong config.yml!");
            config.set("items.common.probability", 60);
            config.set("items.common.items", new ArrayList<>());
            config.set("items.rare.probability", 30);
            config.set("items.rare.items", new ArrayList<>());
            config.set("items.legendary.probability", 10);
            config.set("items.legendary.items", new ArrayList<>());
        }
        if (!config.contains("point-items")) {
            plugin.getLogger().severe("Thiếu mục 'point-items' trong config.yml!");
            config.set("point-items", new ArrayList<>());
        }
    }

    private <T extends Enum<T>> T getEnumValue(String path, T defaultValue, Class<T> enumClass) {
        String valueStr = config.getString(path);
        if (valueStr == null) {
            return defaultValue;
        }
        try {
            return Enum.valueOf(enumClass, valueStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Giá trị không hợp lệ cho '" + path + "': " + valueStr + ". Sử dụng giá trị mặc định.");
            return defaultValue;
        }
    }

    private void loadUISettings() {
        openPointShopSlot = config.getInt("ui-settings.slot-layout.open-point-shop", 26);
        borderMaterial = getEnumValue("ui-settings.border-item.material", Material.PURPLE_STAINED_GLASS_PANE, Material.class);
        infoMaterial = getEnumValue("ui-settings.info-item.material", Material.CLOCK, Material.class);
        inventorySize = config.getInt("ui-settings.inventory-size", 27);
        if (inventorySize % 9 != 0 || inventorySize < 9 || inventorySize > 54) {
            plugin.getLogger().warning("Kích thước inventory không hợp lệ!");
            inventorySize = 27;
        }
        borderSlots = config.getIntegerList("ui-settings.slot-layout.border");
        infoItemSlot = config.getInt("ui-settings.slot-layout.info", 22); // Đã chuyển từ loadInventory()
        infoLoreTemplate = config.getStringList("ui-settings.info-item.lore");
        openShopSound = getEnumValue("ui-settings.sounds.open-shop", Sound.BLOCK_CHEST_OPEN, Sound.class);
        closeShopSound = getEnumValue("ui-settings.sounds.close-shop", Sound.BLOCK_CHEST_CLOSE, Sound.class);
        updateInfoSound = getEnumValue("ui-settings.sounds.update-info", Sound.BLOCK_NOTE_BLOCK_HARP, Sound.class);

        // THÊM DÒNG NÀY: Tải các slot vật phẩm chính
        this.shopItemSlots = config.getIntegerList("ui-settings.slot-layout.items");
        plugin.getLogger().info("Đã tải " + shopItemSlots.size() + " slot vật phẩm chính từ config.yml.");
        if (this.shopItemSlots.isEmpty()) {
            plugin.getLogger().warning("Không tìm thấy slot vật phẩm chính trong 'ui-settings.slot-layout.items' hoặc danh sách rỗng. Vật phẩm có thể không hiển thị!");
        }
    }

    private void loadPointShopUI() {
        ConfigurationSection section = config.getConfigurationSection("point-shop");
        if (section == null) return;
        pointInventorySize = section.getInt("inventory-size", 27);
        pointBackButtonMaterial = Material.matchMaterial(section.getString("back-button.material", "BARRIER"));
        pointBackButtonName = section.getString("back-button.name", "&cQuay Lại");
        pointBackButtonSlot = section.getInt("back-button.slot", 26);
        pointShopItemSlots = section.getIntegerList("slot-layout.point-items");
    }

    private void loadSounds() {
        try {
            purchaseSound = Sound.valueOf(config.getString("purchase-sound.sound", "ENTITY_EXPERIENCE_ORB_PICKUP"));
            soundVolume = (float) config.getDouble("purchase-sound.volume", 1.0);
            soundPitch = (float) config.getDouble("purchase-sound.pitch", 1.0);
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Âm thanh purchase-sound không hợp lệ");
            purchaseSound = Sound.ENTITY_EXPERIENCE_ORB_PICKUP;
            soundVolume = 1.0f;
            soundPitch = 1.0f;
        }
        try {
            noMoneySound = Sound.valueOf(config.getString("no-money-sound.sound", "ENTITY_VILLAGER_NO"));
            noMoneyVolume = (float) config.getDouble("no-money-sound.volume", 1.0);
            noMoneyPitch = (float) config.getDouble("no-money-sound.pitch", 1.0);
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Âm thanh no-money-sound không hợp lệ");
            noMoneySound = Sound.ENTITY_VILLAGER_NO;
            noMoneyVolume = 1.0f;
            noMoneyPitch = 1.0f;
        }
        try {
            limitReachedSound = Sound.valueOf(config.getString("limit-reached-sound.sound", "BLOCK_ANVIL_LAND"));
            limitReachedVolume = (float) config.getDouble("limit-reached-sound.volume", 1.0);
            limitReachedPitch = (float) config.getDouble("limit-reached-sound.pitch", 1.0);
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Âm thanh limit-reached-sound không hợp lệ");
            limitReachedSound = Sound.BLOCK_ANVIL_LAND;
            limitReachedVolume = 1.0f;
            limitReachedPitch = 1.0f;
        }
    }

    private void loadMessagesAndSettings() {
        refreshMessage = config.getString("refresh-broadcast.message", "&eCửa hàng ngẫu nhiên đã được làm mới!");
        pointsMessage = config.getString("points-message", "&aBạn nhận được 1 điểm tích lũy!");
        maxPurchases = config.getInt("max-purchases", 3);
        resetsForPoint = config.getInt("resets-for-point", 2);
        refreshInterval = config.getInt("refresh-interval", 300);
        shopMinItems = config.getInt("shop-size.min", 2);
        shopMaxItems = config.getInt("shop-size.max", 5);
    }

    public LegacyComponentSerializer getLegacySerializer() {
        return legacySerializer;
    }

    public String getPrefix() {
        return prefix;
    }

    public Component getMessage(String key, String... replacements) {
        String msg = messages.getString(key, "");
        for (int i = 0; i < replacements.length - 1; i += 2) {
            msg = msg.replace("%" + replacements[i] + "%", replacements[i + 1]);
        }
        // Trả về Component đã được deserialize, áp dụng mã màu cho cả prefix và nội dung tin nhắn
        return legacySerializer.deserialize(prefix + msg);
    }

    public int getInventorySize() {
        return inventorySize;
    }

    public List<Integer> getBorderSlots() {
        return borderSlots;
    }

    public Material getBorderMaterial() {
        return borderMaterial;
    }

    public Material getInfoMaterial() {
        return infoMaterial;
    }

    public List<String> getInfoLoreTemplate() {
        return infoLoreTemplate;
    }

    public int getOpenPointShopSlot() {
        return openPointShopSlot;
    }

    public String getRefreshMessage() {
        return refreshMessage;
    }

    public String getPointsMessage() {
        return pointsMessage;
    }

    public int getMaxPurchases() {
        return maxPurchases;
    }

    public int getResetsForPoint() {
        return resetsForPoint;
    }

    public int getRefreshInterval() {
        return refreshInterval;
    }

    public int getShopMinItems() {
        return shopMinItems;
    }

    public int getShopMaxItems() {
        return shopMaxItems;
    }

    public Map<UUID, Integer> getPlayerPurchases() {
        return playerPurchases;
    }

    public Map<UUID, Integer> getPlayerNoPurchaseResets() {
        return playerNoPurchaseResets;
    }

    public Map<UUID, Integer> getPlayerPoints() {
        return playerPoints;
    }

    public int getPointInventorySize() {
        return pointInventorySize;
    }

    public int getPointBackButtonSlot() {
        return pointBackButtonSlot;
    }

    public Material getPointBackButtonMaterial() {
        return pointBackButtonMaterial;
    }

    public String getPointBackButtonName() {
        return pointBackButtonName;
    }

    public List<Integer> getPointShopItemSlots() {
        return pointShopItemSlots;
    }

    // THÊM PHƯƠNG THỨC GETTER MỚI NÀY
    public List<Integer> getShopItemSlots() {
        return shopItemSlots;
    }

    public Sound getOpenShopSound() {
        return openShopSound;
    }

    public Sound getCloseShopSound() {
        return closeShopSound;
    }

    public Sound getUpdateInfoSound() {
        return updateInfoSound;
    }

    public Sound getPurchaseSound() {
        return purchaseSound;
    }

    public Sound getNoMoneySound() {
        return noMoneySound;
    }

    public Sound getLimitReachedSound() {
        return limitReachedSound;
    }

    public float getSoundVolume() {
        return soundVolume;
    }

    public float getSoundPitch() {
        return soundPitch;
    }

    public float getNoMoneyVolume() {
        return noMoneyVolume;
    }

    public float getNoMoneyPitch() {
        return noMoneyPitch;
    }

    public float getLimitReachedVolume() {
        return limitReachedVolume;
    }

    public float getLimitReachedPitch() {
        return limitReachedPitch;
    }

    public String getPointShopTitle() { return pointShopTitle; }
    public List<String> getDefaultItemLore() { return defaultItemLore; }
    public List<String> getPointItemLore() { return pointItemLore; }
    public int getInfoItemSlot() { return infoItemSlot; }

    public String getRarityColor(String key, String defaultValue) {
        return rarityColors.getOrDefault(key, defaultValue);
    }

    public String getEnchantName(String key) {
        return enchantNames.getOrDefault(key, key);
    }

    public String getFlagName(String key) {
        return flagNames.getOrDefault(key, key);
    }

    public boolean checkVersion(int requiredMinor, int requiredPatch) {
        try {
            String[] versionParts = Bukkit.getBukkitVersion().split("-")[0].split("\\.");
            int minor = Integer.parseInt(versionParts[1]);
            int patch = versionParts.length > 2 ? Integer.parseInt(versionParts[2]) : 0;
            return (minor > requiredMinor) || (minor == requiredMinor && patch >= requiredPatch);
        } catch (Exception e) {
            return false;
        }
    }
}