package dev.biomeislandsnether.stats;

import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/** Local, in-memory counters. Nothing is transmitted or persisted. */
public final class GenerationStats {
    private final AtomicLong generatedChunks = new AtomicLong();
    private final Set<Long> uniqueLandmassSeeds = ConcurrentHashMap.newKeySet();
    private final AtomicLong[] landmassesByBiome;
    private final AtomicLong grounded = new AtomicLong();
    private final AtomicLong floating = new AtomicLong();
    private final AtomicLong stepping = new AtomicLong();

    public GenerationStats(int biomeCount) {
        this.landmassesByBiome = new AtomicLong[Math.max(1, biomeCount)];
        for (int i = 0; i < this.landmassesByBiome.length; i++) {
            this.landmassesByBiome[i] = new AtomicLong();
        }
    }

    public void observeChunk() {
        generatedChunks.incrementAndGet();
    }

    public void observeLandmass(long seed, int biomeIndex, boolean isFloating, boolean isStepping) {
        if (!uniqueLandmassSeeds.add(seed)) return;
        if (biomeIndex >= 0 && biomeIndex < landmassesByBiome.length) {
            landmassesByBiome[biomeIndex].incrementAndGet();
        }
        if (isStepping) stepping.incrementAndGet();
        else if (isFloating) floating.incrementAndGet();
        else grounded.incrementAndGet();
    }

    public Snapshot snapshot() {
        long[] byBiome = new long[landmassesByBiome.length];
        for (int i = 0; i < landmassesByBiome.length; i++) byBiome[i] = landmassesByBiome[i].get();
        return new Snapshot(generatedChunks.get(), uniqueLandmassSeeds.size(), grounded.get(), floating.get(), stepping.get(), byBiome);
    }

    public record Snapshot(long generatedChunks, long uniqueLandmasses, long grounded, long floating,
                           long stepping, long[] landmassesByBiome) {
        public Snapshot {
            landmassesByBiome = Arrays.copyOf(landmassesByBiome, landmassesByBiome.length);
        }

        @Override
        public long[] landmassesByBiome() {
            return Arrays.copyOf(landmassesByBiome, landmassesByBiome.length);
        }
    }
}
