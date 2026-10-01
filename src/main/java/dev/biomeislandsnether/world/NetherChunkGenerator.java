package dev.biomeislandsnether.world;

import dev.biomeislandsnether.config.NetherSettings;
import dev.biomeislandsnether.stats.GenerationStats;
import org.bukkit.Material;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * Lava-archipelago Nether generator.
 *
 * <p>Vanilla Nether noise is generated first. This generator opens a broad lava cavern while
 * keeping the upper vanilla ceiling, then builds stochastic grounded or floating netherrack
 * landmasses. Vanilla surface rules, caves, decorations, mobs and structures can still run after
 * the noise step, which lets each biome theme receive its normal Nether dressing.</p>
 */
public final class NetherChunkGenerator extends ChunkGenerator {
    private final NetherSettings settings;
    private final NetherLayout layout;
    private final BiomeProvider biomeProvider;
    private final GenerationStats stats;

    public NetherChunkGenerator(NetherSettings settings, BiomeProvider biomeProvider, GenerationStats stats) {
        this.settings = settings;
        this.layout = new NetherLayout(settings);
        this.biomeProvider = biomeProvider;
        this.stats = stats;
    }

    @Override
    public void generateNoise(WorldInfo worldInfo, Random random, int chunkX, int chunkZ, ChunkData data) {
        int minY = data.getMinHeight();
        int maxY = data.getMaxHeight();
        int lavaY = clamp(settings.lavaLevel(), minY + 4, maxY - 16);
        long seed = worldInfo.getSeed();
        int startX = chunkX << 4;
        int startZ = chunkZ << 4;
        Map<Long, Observation> observedLandmasses = stats == null ? null : new HashMap<>();

        for (int localX = 0; localX < 16; localX++) {
            int worldX = startX + localX;
            for (int localZ = 0; localZ < 16; localZ++) {
                int worldZ = startZ + localZ;
                int floorY = clamp(layout.lavaFloorY(seed, worldX, worldZ), minY + 2, lavaY - 3);
                int ceilingBottom = clamp(layout.ceilingBottomY(seed, worldX, worldZ), lavaY + 18, maxY - 4);

                // Open the central Nether cavern while preserving the original high ceiling.
                data.setRegion(localX, floorY + 1, localZ, localX + 1, ceilingBottom, localZ + 1, Material.AIR);
                data.setBlock(localX, floorY, localZ, Material.NETHERRACK);
                data.setRegion(localX, floorY + 1, localZ, localX + 1, lavaY, localZ + 1, Material.LAVA);

                NetherLayout.LandmassSample sample = layout.sample(seed, worldX, worldZ);
                if (!sample.inside()) continue;

                if (observedLandmasses != null) {
                    observedLandmasses.putIfAbsent(sample.seed(),
                            new Observation(sample.biomeIndex(), sample.floating(), sample.stepping()));
                }

                if (sample.floating()) {
                    NetherLayout.VerticalBand band = layout.floatingBand(seed, worldX, worldZ, sample, ceilingBottom);
                    int bottom = clamp(band.bottomY(), lavaY + 3, ceilingBottom - 4);
                    int top = clamp(band.topY(), bottom + 1, ceilingBottom - 3);
                    if (bottom < top) {
                        data.setRegion(localX, bottom, localZ, localX + 1, top + 1, localZ + 1, Material.NETHERRACK);
                    }
                } else {
                    int top = clamp(layout.groundTopY(seed, worldX, worldZ, sample, ceilingBottom), floorY + 2, ceilingBottom - 6);
                    data.setRegion(localX, floorY + 1, localZ, localX + 1, top + 1, localZ + 1, Material.NETHERRACK);
                }
            }
        }

        if (stats != null) {
            stats.observeChunk();
            for (Map.Entry<Long, Observation> entry : observedLandmasses.entrySet()) {
                Observation observation = entry.getValue();
                stats.observeLandmass(entry.getKey(), observation.biomeIndex(),
                        observation.floating(), observation.stepping());
            }
        }
    }

    @Override public boolean shouldGenerateNoise() { return true; }
    @Override public boolean shouldGenerateSurface() { return true; }
    @Override public boolean shouldGenerateCaves() { return settings.caves(); }
    @Override public boolean shouldGenerateDecorations() { return settings.decorations(); }
    @Override public boolean shouldGenerateMobs() { return settings.mobs(); }
    @Override public boolean shouldGenerateStructures() { return settings.structures(); }
    @Override public BiomeProvider getDefaultBiomeProvider(WorldInfo worldInfo) { return biomeProvider; }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private record Observation(int biomeIndex, boolean floating, boolean stepping) {}
}
