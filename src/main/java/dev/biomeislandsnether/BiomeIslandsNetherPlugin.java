package dev.biomeislandsnether;

import dev.biomeislandsnether.command.BiomeIslandsNetherCommands;
import dev.biomeislandsnether.config.NetherSettings;
import dev.biomeislandsnether.stats.GenerationStats;
import dev.biomeislandsnether.world.NetherBiomeProvider;
import dev.biomeislandsnether.world.NetherChunkGenerator;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class BiomeIslandsNetherPlugin extends JavaPlugin {
    public static final String VERSION = "0.3.1";
    public static final String CREATOR = "Sfekke";

    private volatile NetherSettings settings;
    private volatile BiomeIslandsNetherCommands commands;
    private volatile int validationWarningCount;
    private final ConcurrentHashMap<String, GenerationStats> statsByWorld = new ConcurrentHashMap<>();
    private final Set<String> generatorWorlds = ConcurrentHashMap.newKeySet();

    @Override
    public void onLoad() {
        saveDefaultConfig();
        settings = NetherSettings.from(getConfig());
    }

    @Override
    public void onEnable() {
        commands = new BiomeIslandsNetherCommands(this);
        NetherSettings current = currentSettings();
        validationWarningCount = validateConfiguration(current);

        getLogger().info("BiomeIslands-Nether v" + VERSION + " by " + CREATOR
                + " enabled. Sister project to BiomeIslands, inspired by IslandCraft. Lava sea Y"
                + (current.lavaLevel() - 1) + ", " + current.enabledBiomeCount() + "/" + current.biomeKeys().size()
                + " Nether biome themes enabled, floating chance "
                + Math.round(current.floatingChance() * 100.0) + "%, stepping islands "
                + (current.steppingIslands() ? "enabled" : "disabled") + ".");
        getLogger().info("Admin QoL: /biomeislandsnether (/bin) with info, status, here, biomes, stats and debug tools. "
                + "All command permissions default to operators.");
        if (validationWarningCount == 0) {
            getLogger().info("Configuration validation completed with no warnings.");
        } else {
            getLogger().warning("Configuration validation completed with " + validationWarningCount + " warning(s).");
        }
    }

    @Override
    public ChunkGenerator getDefaultWorldGenerator(String worldName, String id) {
        NetherSettings current = currentSettings();
        String key = normalizeWorldName(worldName);
        generatorWorlds.add(key);
        GenerationStats stats = statsByWorld.computeIfAbsent(key,
                ignored -> new GenerationStats(current.biomeKeys().size()));
        NetherBiomeProvider provider = new NetherBiomeProvider(current, getLogger());
        return new NetherChunkGenerator(current, provider, stats);
    }

    @Override
    public BiomeProvider getDefaultBiomeProvider(String worldName, String id) {
        if (worldName != null && !worldName.isBlank()) generatorWorlds.add(worldName);
        return new NetherBiomeProvider(currentSettings(), getLogger());
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!"biomeislandsnether".equalsIgnoreCase(command.getName())) return false;
        BiomeIslandsNetherCommands current = commands;
        if (current == null) {
            current = new BiomeIslandsNetherCommands(this);
            commands = current;
        }
        return current.execute(sender, args);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!"biomeislandsnether".equalsIgnoreCase(command.getName())) return List.of();
        BiomeIslandsNetherCommands current = commands;
        if (current == null) {
            current = new BiomeIslandsNetherCommands(this);
            commands = current;
        }
        return current.tabComplete(sender, args);
    }

    public NetherSettings currentSettings() {
        NetherSettings current = settings;
        if (current == null) {
            synchronized (this) {
                current = settings;
                if (current == null) {
                    saveDefaultConfig();
                    current = NetherSettings.from(getConfig());
                    settings = current;
                }
            }
        }
        return current;
    }

    public GenerationStats statsForWorld(String worldName) {
        return statsByWorld.get(normalizeWorldName(worldName));
    }

    public List<String> statWorldNames() {
        List<String> names = new ArrayList<>(statsByWorld.keySet());
        names.sort(String.CASE_INSENSITIVE_ORDER);
        return List.copyOf(names);
    }

    public boolean isGeneratorWorld(String worldName) {
        return generatorWorlds.contains(normalizeWorldName(worldName));
    }

    public int validationWarningCount() {
        return validationWarningCount;
    }

    private int validateConfiguration(NetherSettings current) {
        int warnings = 0;
        if (current.enabledBiomeCount() < 2) {
            warnings += warn("Only " + current.enabledBiomeCount()
                    + " selectable Nether biome theme remains. This is valid, but variety will be very low.");
        }
        if (current.lavaFloorY() >= current.lavaLevel() - 3) {
            warnings += warn("lava-sea.floor-y is too close to lava-level; the lava sea may become extremely shallow.");
        }
        if (current.floatingMaxY() + Math.max(4, current.floatingThickness() / 2) >= current.ceilingY()) {
            warnings += warn("The floating-island band reaches close to the configured ceiling and may be heavily clipped.");
        }
        if (current.minRadius() > current.maxRadius()) {
            warnings += warn("islands.min-radius exceeds max-radius; runtime normalization swaps them.");
        }
        if (current.steppingIslands() && current.steppingMaxRadius() >= current.minRadius()) {
            warnings += warn("travel.stepping-max-radius is as large as a main-island minimum radius; stepping islands may stop reading as small travel helpers.");
        }
        if (getConfig().getInt("islands.average-spacing", current.averageSpacing()) < 96) {
            warnings += warn("islands.average-spacing is below the supported minimum and is being clamped to " + current.averageSpacing() + ".");
        }
        if (getConfig().getDouble("islands.density", current.density()) > 1.0
                || getConfig().getDouble("islands.density", current.density()) < 0.10) {
            warnings += warn("islands.density is outside the supported 0.10..1.00 range and is being clamped.");
        }
        return warnings;
    }

    private int warn(String message) {
        getLogger().warning("Config: " + message);
        return 1;
    }

    private static String normalizeWorldName(String worldName) {
        return worldName == null || worldName.isBlank() ? "<unknown>" : worldName;
    }
}
