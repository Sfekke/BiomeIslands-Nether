package dev.biomeislandsnether.world;

import dev.biomeislandsnether.config.NetherSettings;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Biome;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.WorldInfo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

public final class NetherBiomeProvider extends BiomeProvider {
    private final NetherSettings settings;
    private final NetherLayout layout;
    private final Biome background;
    private final List<Biome> islandBiomes;
    private final List<Biome> advertised;

    public NetherBiomeProvider(NetherSettings settings, Logger logger) {
        this.settings = settings;
        this.layout = new NetherLayout(settings);
        Biome wastes = resolve("minecraft:nether_wastes", logger, true);
        Biome configuredBackground = resolve(settings.backgroundBiome(), logger, false);
        this.background = configuredBackground == null ? wastes : configuredBackground;

        List<Biome> resolved = new ArrayList<>();
        for (int i = 0; i < settings.biomeKeys().size(); i++) {
            String key = settings.biomeKeys().get(i);
            Biome biome = settings.biomeWeights().get(i) <= 0.0 ? wastes : resolve(key, logger, false);
            if (biome == null) {
                logger.warning("Replacing unknown Nether biome '" + key + "' with minecraft:nether_wastes.");
                biome = wastes;
            }
            resolved.add(biome);
        }
        this.islandBiomes = List.copyOf(resolved);

        Set<Biome> all = new LinkedHashSet<>();
        all.add(background);
        for (int i = 0; i < islandBiomes.size(); i++) {
            if (settings.biomeWeights().get(i) > 0.0) all.add(islandBiomes.get(i));
        }
        this.advertised = Collections.unmodifiableList(new ArrayList<>(all));
    }

    @Override
    public Biome getBiome(WorldInfo worldInfo, int x, int y, int z) {
        NetherLayout.LandmassSample sample = layout.sample(worldInfo.getSeed(), x, z);
        if (!sample.inside()) return background;

        int ceilingBottom = layout.ceilingBottomY(worldInfo.getSeed(), x, z);
        if (sample.floating()) {
            NetherLayout.VerticalBand band = layout.floatingBand(worldInfo.getSeed(), x, z, sample, ceilingBottom);
            if (y >= band.bottomY() - 8 && y <= band.topY() + 10) {
                return islandBiomes.get(sample.biomeIndex());
            }
            return background;
        }

        int top = layout.groundTopY(worldInfo.getSeed(), x, z, sample, ceilingBottom);
        return y <= top + 12 ? islandBiomes.get(sample.biomeIndex()) : background;
    }

    @Override
    public List<Biome> getBiomes(WorldInfo worldInfo) {
        return advertised;
    }

    private static Biome resolve(String rawKey, Logger logger, boolean required) {
        NamespacedKey key = NamespacedKey.fromString(rawKey);
        Biome biome = key == null ? null : findBiome(key);
        if (biome == null && required) {
            NamespacedKey fallback = NamespacedKey.minecraft("nether_wastes");
            biome = findBiome(fallback);
            if (biome == null) {
                throw new IllegalStateException("Required biome minecraft:nether_wastes is not present in Registry.BIOME");
            }
        }
        return biome;
    }

    /** Preserve the Registry ABI workaround learned from BiomeIslands. */
    private static Biome findBiome(NamespacedKey key) {
        try {
            Class<?> registryType = Class.forName("org.bukkit.Registry", false,
                    NetherBiomeProvider.class.getClassLoader());
            Object biomeRegistry = registryType.getField("BIOME").get(null);
            if (biomeRegistry == null) return null;
            Object value = registryType.getMethod("get", NamespacedKey.class).invoke(biomeRegistry, key);
            if (value instanceof Biome biome) return biome;
            if (biomeRegistry instanceof Iterable<?> iterable) {
                for (Object candidate : iterable) {
                    if (candidate instanceof Biome biome && key.equals(biome.getKey())) return biome;
                }
            }
            return null;
        } catch (ReflectiveOperationException | LinkageError ex) {
            throw new IllegalStateException("Unable to access Bukkit biome registry", ex);
        }
    }
}
