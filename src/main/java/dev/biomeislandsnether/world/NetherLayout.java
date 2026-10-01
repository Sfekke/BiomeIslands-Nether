package dev.biomeislandsnether.world;

import dev.biomeislandsnether.config.NetherSettings;
import dev.biomeislandsnether.noise.ValueNoise;

public final class NetherLayout {
    private static final long CELL_SALT = 0x43F6B91D2A75C80EL;
    private static final long SECONDARY_SALT = 0x8D2B70C6F15A439EL;
    private static final long STEP_SALT = 0x5A9173C8E24DB60FL;
    private static final long TYPE_SALT = 0x14C7EA9B32D5F608L;
    private static final long RADIUS_SALT = 0x71A9E4D20BC35F86L;
    private static final long BIOME_SALT = 0xA42C79E135D6B80FL;
    private static final long FLOAT_Y_SALT = 0x53BE1F97C24AD608L;
    private static final long SHAPE_SALT = 0xE31D8A4B726F095CL;
    private static final long HEIGHT_SALT = 0x6C8FA1E239D47B05L;
    private static final long FLOOR_SALT = 0xB9037CE154A8D26FL;
    private static final long CEILING_SALT = 0x25A7E9D31C84F60BL;

    private final NetherSettings settings;

    public NetherLayout(NetherSettings settings) {
        this.settings = settings;
    }

    public LandmassSample sample(long worldSeed, int x, int z) {
        Candidate bestMain = null;
        double bestMainEdge = -Double.MAX_VALUE;
        int spacing = settings.averageSpacing();
        int baseCellX = floorDiv(x, spacing);
        int baseCellZ = floorDiv(z, spacing);

        // Main islands are stochastic rather than gridded. In 0.2.0 a secondary candidate is a
        // true satellite of its primary, producing loose clusters instead of two unrelated points
        // somewhere in the same lookup cell.
        for (int cellX = baseCellX - 3; cellX <= baseCellX + 3; cellX++) {
            for (int cellZ = baseCellZ - 3; cellZ <= baseCellZ + 3; cellZ++) {
                Candidate primary = primaryCandidate(worldSeed, cellX, cellZ);
                if (primary == null) continue;

                double primaryEdge = edgeDepth(primary, x, z);
                if (primaryEdge > bestMainEdge) {
                    bestMainEdge = primaryEdge;
                    bestMain = primary;
                }

                Candidate satellite = satelliteCandidate(primary);
                if (satellite != null) {
                    double satelliteEdge = edgeDepth(satellite, x, z);
                    if (satelliteEdge > bestMainEdge) {
                        bestMainEdge = satelliteEdge;
                        bestMain = satellite;
                    }
                }
            }
        }

        // Never let a travel stepping-stone override geometry inside a real biome island. This
        // keeps small travel helpers from carving a floating hole into a grounded main island when
        // their masks overlap. They are only considered in open lava gaps.
        if (bestMain != null && bestMainEdge > 0.0) {
            return toSample(bestMain, bestMainEdge, x, z);
        }

        Candidate bestStep = null;
        double bestStepEdge = -Double.MAX_VALUE;
        if (settings.steppingIslands() && settings.steppingIslandChance() > 0.0) {
            int stepSpacing = steppingSpacing();
            int baseStepX = floorDiv(x, stepSpacing);
            int baseStepZ = floorDiv(z, stepSpacing);
            for (int cellX = baseStepX - 2; cellX <= baseStepX + 2; cellX++) {
                for (int cellZ = baseStepZ - 2; cellZ <= baseStepZ + 2; cellZ++) {
                    Candidate step = steppingCandidate(worldSeed, cellX, cellZ);
                    if (step == null) continue;
                    double edge = edgeDepth(step, x, z);
                    if (edge > bestStepEdge) {
                        bestStepEdge = edge;
                        bestStep = step;
                    }
                }
            }
        }

        if (bestStep != null && bestStepEdge > 0.0) {
            return toSample(bestStep, bestStepEdge, x, z);
        }
        if (bestMain != null) return toSample(bestMain, bestMainEdge, x, z);
        if (bestStep != null) return toSample(bestStep, bestStepEdge, x, z);
        return LandmassSample.none();
    }

    public int groundTopY(long worldSeed, int x, int z, LandmassSample sample, int ceilingBottomY) {
        double strength = interiorStrength(sample);
        double detail = ValueNoise.fbm(worldSeed ^ sample.seed() ^ HEIGHT_SALT, x * 0.025, z * 0.025, 3);
        double broad = ValueNoise.fbm(worldSeed ^ sample.seed() ^ (HEIGHT_SALT * 3L), x * 0.009, z * 0.009, 2);
        int shoreline = settings.lavaLevel() - 1;

        if (sample.stepping()) {
            double maxRise = 9.0 + sample.radius() * 0.38;
            int rise = (int) Math.round(maxRise * (0.30 + 0.70 * smootherStep(strength)));
            int variation = (int) Math.round((detail * 0.75 + broad * 0.25) * 2.5 * (0.25 + strength));
            return Math.min(ceilingBottomY - 12, shoreline + Math.max(2, rise + variation));
        }

        int rise = (int) Math.round(settings.groundHeight() * (0.16 + 0.84 * smootherStep(strength)));
        int variation = (int) Math.round((detail * 0.65 + broad * 0.35) * settings.heightVariation() * (0.35 + strength));
        return Math.min(ceilingBottomY - 10, shoreline + rise + variation);
    }

    public VerticalBand floatingBand(long worldSeed, int x, int z, LandmassSample sample, int ceilingBottomY) {
        double strength = interiorStrength(sample);
        double detail = ValueNoise.fbm(worldSeed ^ sample.seed() ^ HEIGHT_SALT, x * 0.024, z * 0.024, 3);
        double core = Math.sqrt(clamp(strength, 0.0, 1.0));

        double half;
        if (sample.stepping()) {
            half = Math.max(3.0, 4.0 + sample.radius() * (0.08 + 0.12 * core));
        } else {
            half = Math.max(2.5, settings.floatingThickness() * (0.12 + 0.48 * core));
        }

        int top = (int) Math.round(sample.centerY() + (half * 0.55) + (detail * settings.heightVariation() * (sample.stepping() ? 0.12 : 0.35)));
        int bottom = (int) Math.round(sample.centerY() - (half * 1.20) + (detail * settings.heightVariation() * (sample.stepping() ? 0.08 : 0.20)));
        top = Math.min(top, ceilingBottomY - 8);
        bottom = Math.max(settings.lavaLevel() + 3, bottom);
        if (bottom >= top) bottom = top - 2;
        return new VerticalBand(bottom, top);
    }

    public int lavaFloorY(long worldSeed, int x, int z) {
        double noise = ValueNoise.fbm(worldSeed ^ FLOOR_SALT, x * 0.006, z * 0.006, 3);
        return settings.lavaFloorY() + (int) Math.round(noise * settings.lavaFloorVariation());
    }

    public int ceilingBottomY(long worldSeed, int x, int z) {
        double noise = ValueNoise.fbm(worldSeed ^ CEILING_SALT, x * 0.0055, z * 0.0055, 3);
        return settings.ceilingY() + (int) Math.round(noise * settings.ceilingVariation());
    }

    public int biomeIndex(long candidateSeed) {
        double target = unit(candidateSeed ^ BIOME_SALT) * settings.totalBiomeWeight();
        double cumulative = 0.0;
        for (int i = 0; i < settings.biomeWeights().size(); i++) {
            cumulative += Math.max(0.0, settings.biomeWeights().get(i));
            if (target < cumulative) return i;
        }
        return Math.max(0, settings.biomeWeights().size() - 1);
    }

    public double interiorStrength(LandmassSample sample) {
        if (!sample.inside() || sample.radius() <= 0.0) return 0.0;
        double denominator = sample.stepping() ? 0.58 : 0.44;
        return clamp(sample.edgeDepth() / Math.max(1.0, sample.radius() * denominator), 0.0, 1.0);
    }

    private Candidate primaryCandidate(long worldSeed, int cellX, int cellZ) {
        long seed = candidateSeed(worldSeed, cellX, cellZ, 0);
        if (unit(seed ^ CELL_SALT) >= settings.density()) return null;

        int spacing = settings.averageSpacing();
        double xUnit = unit(seed ^ 0x39A4E718C2D65B0FL);
        double zUnit = unit(seed ^ 0xD7C153A904BE268FL);
        double centerX = (cellX + 0.08 + xUnit * 0.84) * spacing;
        double centerZ = (cellZ + 0.08 + zUnit * 0.84) * spacing;
        double radius = lerp(settings.minRadius(), settings.maxRadius(), unit(seed ^ RADIUS_SALT));
        boolean floating = unit(seed ^ TYPE_SALT) < settings.floatingChance();
        int centerY = (int) Math.round(lerp(settings.floatingMinY(), settings.floatingMaxY(), unit(seed ^ FLOAT_Y_SALT)));
        return new Candidate(seed, centerX, centerZ, radius, floating, centerY, biomeIndex(seed), false);
    }

    private Candidate satelliteCandidate(Candidate primary) {
        long seed = ValueNoise.mix64(primary.seed ^ SECONDARY_SALT);
        if (unit(seed ^ CELL_SALT) >= settings.secondaryChance()) return null;

        double radius = lerp(settings.minRadius() * 0.62, settings.maxRadius() * 0.84, unit(seed ^ RADIUS_SALT));
        double angle = unit(seed ^ 0xB32E67D91A4CF508L) * Math.PI * 2.0;
        double gap = 8.0 + unit(seed ^ 0x61F4A92DC83BE507L) * 24.0;
        double distance = primary.radius * 1.05 + radius * 1.05 + gap;
        double centerX = primary.centerX + Math.cos(angle) * distance;
        double centerZ = primary.centerZ + Math.sin(angle) * distance;
        boolean floating = unit(seed ^ TYPE_SALT) < settings.floatingChance();
        int centerY = (int) Math.round(lerp(settings.floatingMinY(), settings.floatingMaxY(), unit(seed ^ FLOAT_Y_SALT)));
        return new Candidate(seed, centerX, centerZ, radius, floating, centerY, biomeIndex(seed), false);
    }

    private Candidate steppingCandidate(long worldSeed, int cellX, int cellZ) {
        long seed = steppingSeed(worldSeed, cellX, cellZ);
        if (unit(seed ^ CELL_SALT) >= settings.steppingIslandChance()) return null;

        int spacing = steppingSpacing();
        double centerX = (cellX + 0.10 + unit(seed ^ 0x2AC97E31D640B85FL) * 0.80) * spacing;
        double centerZ = (cellZ + 0.10 + unit(seed ^ 0xF14B8639A20D75CEL) * 0.80) * spacing;
        double radius = lerp(settings.steppingMinRadius(), settings.steppingMaxRadius(), unit(seed ^ RADIUS_SALT));

        // Travel stones stay lower than the main floating-island band so they are useful from the
        // lava sea and from low island ledges. Most are grounded; some float to create vertical
        // stepping routes without becoming a guaranteed bridge network.
        boolean floating = unit(seed ^ TYPE_SALT) < 0.38;
        int centerY = settings.lavaLevel() + 10
                + (int) Math.round(unit(seed ^ FLOAT_Y_SALT) * 20.0);
        return new Candidate(seed, centerX, centerZ, radius, floating, centerY, biomeIndex(seed), true);
    }

    private int steppingSpacing() {
        return Math.max(48, settings.averageSpacing() / 2);
    }

    private double edgeDepth(Candidate candidate, int x, int z) {
        double dx = x - candidate.centerX;
        double dz = z - candidate.centerZ;
        double distance = Math.hypot(dx, dz);
        double angle = Math.atan2(dz, dx);
        long seed = candidate.seed;

        double phase2 = unit(seed ^ 0xD04AE1975C63B28FL) * Math.PI * 2.0;
        double phase3 = unit(seed ^ 0x7B23D965F140AE8CL) * Math.PI * 2.0;
        double phase5 = unit(seed ^ 0x1FE6A4C9723DB805L) * Math.PI * 2.0;
        double harmonicScale = candidate.stepping ? 0.62 : 1.0;
        double harmonics = harmonicScale * (0.095 * Math.sin(angle * 2.0 + phase2)
                + 0.060 * Math.sin(angle * 3.0 + phase3)
                + 0.035 * Math.sin(angle * 5.0 + phase5));
        double broad = ValueNoise.fbm(seed ^ SHAPE_SALT, x * (candidate.stepping ? 0.018 : 0.011), z * (candidate.stepping ? 0.018 : 0.011), 2);
        double detail = ValueNoise.fbm(seed ^ (SHAPE_SALT * 5L), x * (candidate.stepping ? 0.045 : 0.028), z * (candidate.stepping ? 0.045 : 0.028), 2);
        double chaos = settings.horizontalChaos() * (candidate.stepping ? 0.65 : 1.0);
        double factor = 0.80 + harmonics + broad * chaos + detail * chaos * 0.28;
        factor = clamp(factor, candidate.stepping ? 0.62 : 0.52, candidate.stepping ? 1.10 : 1.20);
        return candidate.radius * factor - distance;
    }

    private long candidateSeed(long worldSeed, int cellX, int cellZ, int slot) {
        long h = worldSeed ^ CELL_SALT;
        h ^= (long) cellX * 0x632BE59BD9B4E019L;
        h ^= (long) cellZ * 0x9E3779B97F4A7C15L;
        if (slot != 0) h ^= SECONDARY_SALT;
        return ValueNoise.mix64(h);
    }

    private long steppingSeed(long worldSeed, int cellX, int cellZ) {
        long h = worldSeed ^ STEP_SALT;
        h ^= (long) cellX * 0x94D049BB133111EBL;
        h ^= (long) cellZ * 0xBF58476D1CE4E5B9L;
        return ValueNoise.mix64(h);
    }

    private LandmassSample toSample(Candidate candidate, double edge, int x, int z) {
        double dx = x - candidate.centerX;
        double dz = z - candidate.centerZ;
        return new LandmassSample(edge > 0.0, candidate.floating, candidate.stepping, candidate.seed,
                candidate.centerX, candidate.centerZ, dx, dz, candidate.radius, candidate.centerY,
                candidate.biomeIndex, edge);
    }

    private static int floorDiv(int value, int divisor) {
        int q = value / divisor;
        int r = value % divisor;
        return r != 0 && ((value ^ divisor) < 0) ? q - 1 : q;
    }

    private static double unit(long value) {
        return (ValueNoise.mix64(value) >>> 11) * 0x1.0p-53;
    }

    private static double smootherStep(double t) {
        t = clamp(t, 0.0, 1.0);
        return t * t * t * (t * (t * 6.0 - 15.0) + 10.0);
    }

    private static double clamp(double v, double min, double max) { return Math.max(min, Math.min(max, v)); }
    private static double lerp(double a, double b, double t) { return a + (b - a) * t; }

    private record Candidate(long seed, double centerX, double centerZ, double radius,
                             boolean floating, int centerY, int biomeIndex, boolean stepping) {}

    public record VerticalBand(int bottomY, int topY) {}

    public record LandmassSample(boolean inside, boolean floating, boolean stepping, long seed,
                                 double centerX, double centerZ, double dx, double dz, double radius,
                                 int centerY, int biomeIndex, double edgeDepth) {
        static LandmassSample none() {
            return new LandmassSample(false, false, false, 0L, 0, 0, 0, 0, 1, 64, 0, -Double.MAX_VALUE);
        }
    }
}
