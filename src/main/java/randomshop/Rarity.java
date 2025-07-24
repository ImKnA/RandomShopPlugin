package main.java.randomshop;

public enum Rarity {
    COMMON(1, "common"),
    RARE(2, "rare"),
    LEGENDARY(3, "legendary");

    private final int weight;
    private final String configKey;

    Rarity(int weight, String configKey) {
        this.weight = weight;
        this.configKey = configKey;
    }

    public int getWeight() {
        return weight;
    }

    public String getConfigKey() {
        return configKey;
    }
}