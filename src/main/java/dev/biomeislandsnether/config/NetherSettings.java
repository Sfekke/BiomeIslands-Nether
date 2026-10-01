package dev.biomeislandsnether.config;

import org.bukkit.configuration.file.FileConfiguration;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record NetherSettings(
        int lavaLevel,
        int ceilingY,
        int ceilingVariation,
        int lavaFloorY,
        int lavaFloorVariation,
        int averageSpacing,
        double density,
        double secondaryChance,
        int minRadius,
        int maxRadius,
        double floatingChance,
        int groundHeight,
        int floatingMinY,
        int floatingMaxY,
        int floatingThickness,
        double horizontalChaos,
        int heightVariation,
        boolean steppingIslands,
        double steppingIslandChance,
        int steppingMinRadius,
        int steppingMaxRadius,
        boolean caves,
        boolean decorations,
        boolean mobs,
        boolean structures,
        String backgroundBiome,
        List<String> biomeKeys,
        List<Double> biomeWeights
) {
    private static final LinkedHashMap<String, Double> DEFAULT_BIOMES = new LinkedHashMap<>();

    static {
        DEFAULT_BIOMES.put("minecraft:nether_wastes", 1.30);
        DEFAULT_BIOMES.put("minecraft:crimson_forest", 1.00);
        DEFAULT_BIOMES.put("minecraft:warped_forest", 0.85);
        DEFAULT_BIOMES.put("minecraft:soul_sand_valley", 0.70);
        DEFAULT_BIOMES.put("minecraft:basalt_deltas", 0.65);
    }

    public static NetherSettings from(FileConfiguration config) {
        BiomeConfig biomeConfig = parseBiomes(config.getList("biomes"));

        // 0.2.0 intentionally tightens the default layout. If a 0.1.0 config still contains the
        // untouched 0.1.0 defaults, migrate those three values automatically so simply replacing
        // the JAR produces the denser layout. Any non-default/custom value is preserved.
        String configVersion = config.getString("config-version", "0.1.0");
        int spacing = config.getInt("islands.average-spacing", 145);
        double density = config.getDouble("islands.density", 0.82);
        double secondaryChance = config.getDouble("islands.secondary-chance", 0.32);
        if ("0.1.0".equals(configVersion)) {
            if (spacing == 176) spacing = 145;
            if (Math.abs(density - 0.74) < 1.0e-9) density = 0.82;
            if (Math.abs(secondaryChance - 0.24) < 1.0e-9) secondaryChance = 0.32;
        }

        return new NetherSettings(
                clamp(config.getInt("lava-level", 32), 8, 120),
                clamp(config.getInt("cavern.ceiling-y", 98), 48, 124),
                clamp(config.getInt("cavern.ceiling-variation", 10), 0, 24),
                clamp(config.getInt("lava-sea.floor-y", 18), 3, 100),
                clamp(config.getInt("lava-sea.floor-variation", 5), 0, 16),
                clamp(spacing, 96, 384),
                clamp(density, 0.10, 1.0),
                clamp(secondaryChance, 0.0, 0.85),
                clamp(config.getInt("islands.min-radius", 42), 20, 128),
                clamp(config.getInt("islands.max-radius", 82), 24, 160),
                clamp(config.getDouble("islands.floating-chance", 0.34), 0.0, 1.0),
                clamp(config.getInt("islands.ground-height", 42), 12, 80),
                clamp(config.getInt("islands.floating-min-y", 52), 36, 104),
                clamp(config.getInt("islands.floating-max-y", 78), 40, 112),
                clamp(config.getInt("islands.floating-thickness", 25), 8, 48),
                clamp(config.getDouble("islands.horizontal-chaos", 0.19), 0.0, 0.40),
                clamp(config.getInt("islands.height-variation", 7), 0, 18),
                config.getBoolean("travel.stepping-islands", true),
                clamp(config.getDouble("travel.stepping-island-chance", 0.30), 0.0, 0.90),
                clamp(config.getInt("travel.stepping-min-radius", 8), 4, 32),
                clamp(config.getInt("travel.stepping-max-radius", 20), 6, 40),
                config.getBoolean("vanilla-features.caves", true),
                config.getBoolean("vanilla-features.decorations", true),
                config.getBoolean("vanilla-features.mobs", true),
                config.getBoolean("vanilla-features.structures", true),
                config.getString("background-biome", "minecraft:nether_wastes"),
                biomeConfig.keys(),
                biomeConfig.weights()
        ).normalized();
    }

    private NetherSettings normalized() {
        int minR = Math.min(minRadius, maxRadius);
        int maxR = Math.max(minRadius, maxRadius);
        int stepMinR = Math.min(steppingMinRadius, steppingMaxRadius);
        int stepMaxR = Math.max(steppingMinRadius, steppingMaxRadius);
        int floatMin = Math.min(floatingMinY, floatingMaxY);
        int floatMax = Math.max(floatingMinY, floatingMaxY);
        int floor = Math.min(lavaFloorY, lavaLevel - 4);
        int ceiling = Math.max(lavaLevel + 20, ceilingY);
        return new NetherSettings(lavaLevel, ceiling, ceilingVariation, floor, lavaFloorVariation,
                averageSpacing, density, secondaryChance, minR, maxR, floatingChance, groundHeight,
                floatMin, floatMax, floatingThickness, horizontalChaos, heightVariation,
                steppingIslands, steppingIslandChance, stepMinR, stepMaxR,
                caves, decorations, mobs, structures, backgroundBiome, biomeKeys, biomeWeights);
    }

    public int enabledBiomeCount() {
        int count = 0;
        for (double weight : biomeWeights) {
            if (weight > 0.0) count++;
        }
        return count;
    }

    public double totalBiomeWeight() {
        double total = 0.0;
        for (double weight : biomeWeights) total += Math.max(0.0, weight);
        return total <= 0.0 ? 1.0 : total;
    }

    private static BiomeConfig parseBiomes(List<?> rawList) {
        LinkedHashMap<String, Double> weights = new LinkedHashMap<>();
        if (rawList != null) {
            for (Object raw : rawList) {
                String key = null;
                double weight = 1.0;
                if (raw instanceof String text) {
                    key = text.trim();
                    weight = DEFAULT_BIOMES.getOrDefault(key, 1.0);
                } else if (raw instanceof Map<?, ?> map) {
                    Object keyValue = first(map, "biome", "key", "name");
                    if (keyValue != null) key = String.valueOf(keyValue).trim();
                    Object weightValue = map.get("weight");
                    if (weightValue instanceof Number number) {
                        weight = number.doubleValue();
                    } else if (weightValue != null) {
                        try {
                            weight = Double.parseDouble(String.valueOf(weightValue));
                        } catch (NumberFormatException ignored) {
                            weight = 1.0;
                        }
                    }
                }
                if (key == null || key.isBlank()) continue;
                weight = clamp(weight, 0.0, 10000.0);
                weights.merge(key, weight, Double::sum);
            }
        }
        if (weights.isEmpty()) weights.putAll(DEFAULT_BIOMES);
        boolean any = weights.values().stream().anyMatch(v -> v > 0.0);
        if (!any) weights.put("minecraft:nether_wastes", 1.0);
        return new BiomeConfig(List.copyOf(weights.keySet()), List.copyOf(weights.values()));
    }

    private static Object first(Map<?, ?> map, String... keys) {
        for (String key : keys) {
            if (map.containsKey(key)) return map.get(key);
        }
        return null;
    }

    private static int clamp(int v, int min, int max) { return Math.max(min, Math.min(max, v)); }
    private static double clamp(double v, double min, double max) { return Math.max(min, Math.min(max, v)); }

    private record BiomeConfig(List<String> keys, List<Double> weights) {}
}
