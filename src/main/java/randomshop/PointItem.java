package main.java.randomshop;

import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Collections;

public class PointItem {
    private final ItemStack item;
    private final int points;
    private final String name;
    private final List<String> commands;
    private final boolean useDefaultLore;

    public PointItem(ItemStack item, int points, String name, List<String> commands, boolean useDefaultLore) {
        this.item = item;
        this.points = points;
        this.name = name;
        // Create an unmodifiable list to ensure immutability of the commands list
        this.commands = commands != null ? Collections.unmodifiableList(new ArrayList<>(commands)) : Collections.emptyList();
        this.useDefaultLore = useDefaultLore;
    }

    // Public getter methods
    public ItemStack getItem() {
        return item.clone(); // Return a clone to prevent external modification
    }

    public int getPoints() {
        return points;
    }

    public String getName() {
        return name;
    }

    public List<String> getCommands() {
        return commands; // Already unmodifiable
    }

    public boolean UseDefaultLore() {
        return useDefaultLore;
    }
}