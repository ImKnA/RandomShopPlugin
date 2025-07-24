package main.java.randomshop;

import org.bukkit.inventory.ItemStack;

public class ShopItem {
    private final ItemStack item;
    private final double price;
    private final String name;
    private final Rarity rarity;
    private final double probability;
    private final double catProbability;
    private final boolean useDefaultLore;

    public ShopItem(ItemStack item, double price, String name, Rarity rarity,
                    double probability, double catProbability, boolean useDefaultLore) {
        this.item = item;
        this.price = price;
        this.name = name;
        this.rarity = rarity;
        this.probability = probability;
        this.catProbability = catProbability;
        this.useDefaultLore = useDefaultLore;
    }

    public ItemStack getItem() {
        return item.clone();
    }
    public double getPrice() {
        return price;
    }
    public String getName() {
        return name;
    }
    public Rarity getRarity() {
        return rarity;
    }
    public double getProbability() {
        return probability;
    }
    public double getCatProbability() {
        return catProbability;
    }
    public boolean UseDefaultLore() {
        return useDefaultLore;
    }

}