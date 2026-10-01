package dev.biomeislandsnether.command;

import dev.biomeislandsnether.BiomeIslandsNetherPlugin;
import dev.biomeislandsnether.config.NetherSettings;
import dev.biomeislandsnether.stats.GenerationStats;
import dev.biomeislandsnether.world.NetherLayout;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class BiomeIslandsNetherCommands {
    private final BiomeIslandsNetherPlugin plugin;

    public BiomeIslandsNetherCommands(BiomeIslandsNetherPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean execute(CommandSender sender, String[] args) {
        String sub = args.length == 0 ? "info" : args[0].toLowerCase(Locale.ROOT);
        return switch (sub) {
            case "info", "about" -> withPermission(sender, "biomeislandsnether.command.info", () -> sendInfo(sender));
            case "version" -> withPermission(sender, "biomeislandsnether.command.version", () -> sendVersion(sender));
            case "help", "?" -> withPermission(sender, "biomeislandsnether.command.help", () -> sendHelp(sender));
            case "status" -> withPermission(sender, "biomeislandsnether.command.status", () -> sendStatus(sender));
            case "here" -> withPermission(sender, "biomeislandsnether.command.here", () -> sendHere(sender));
            case "biomes" -> withPermission(sender, "biomeislandsnether.command.biomes", () -> sendBiomes(sender));
            case "stats" -> withPermission(sender, "biomeislandsnether.command.stats", () -> sendStats(sender));
            case "debug" -> withPermission(sender, "biomeislandsnether.command.debug", () -> sendDebug(sender));
            default -> {
                sender.sendMessage("[BiomeIslands-Nether] Unknown subcommand: " + sub + ". Use /bin help.");
                yield true;
            }
        };
    }

    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length != 1) return List.of();
        String prefix = args[0].toLowerCase(Locale.ROOT);
        List<String> result = new ArrayList<>();
        addIfAllowed(result, sender, "info", "biomeislandsnether.command.info", prefix);
        addIfAllowed(result, sender, "version", "biomeislandsnether.command.version", prefix);
        addIfAllowed(result, sender, "help", "biomeislandsnether.command.help", prefix);
        addIfAllowed(result, sender, "status", "biomeislandsnether.command.status", prefix);
        addIfAllowed(result, sender, "here", "biomeislandsnether.command.here", prefix);
        addIfAllowed(result, sender, "biomes", "biomeislandsnether.command.biomes", prefix);
        addIfAllowed(result, sender, "stats", "biomeislandsnether.command.stats", prefix);
        addIfAllowed(result, sender, "debug", "biomeislandsnether.command.debug", prefix);
        return result;
    }

    private void sendInfo(CommandSender sender) {
        sender.sendMessage("[BiomeIslands-Nether] BiomeIslands-Nether v" + BiomeIslandsNetherPlugin.VERSION);
        sender.sendMessage("Creator: " + BiomeIslandsNetherPlugin.CREATOR);
        sender.sendMessage("Sister project to BiomeIslands, inspired by IslandCraft's biome-island concept.");
        sender.sendMessage("Purpose: chaotic Nether biome landmasses, floating islands and lava-sea travel.");
        sender.sendMessage("Target: Paper/Spigot 26.3 | /bin help for admin tools.");
    }

    private void sendVersion(CommandSender sender) {
        sender.sendMessage("[BiomeIslands-Nether] v" + BiomeIslandsNetherPlugin.VERSION
                + " by " + BiomeIslandsNetherPlugin.CREATOR + " | Paper/Spigot 26.3");
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage("[BiomeIslands-Nether] Admin commands (only permitted entries are shown):");
        help(sender, "biomeislandsnether.command.info", "/bin", "plugin/creator information");
        help(sender, "biomeislandsnether.command.version", "/bin version", "version information");
        help(sender, "biomeislandsnether.command.status", "/bin status", "current generator configuration");
        help(sender, "biomeislandsnether.command.here", "/bin here", "landmass/lava-sea information at your position");
        help(sender, "biomeislandsnether.command.biomes", "/bin biomes", "enabled weighted Nether biomes");
        help(sender, "biomeislandsnether.command.stats", "/bin stats", "local generation counters since startup");
        help(sender, "biomeislandsnether.command.debug", "/bin debug", "detailed layout diagnostics at your position");
    }

    private void sendStatus(CommandSender sender) {
        NetherSettings s = plugin.currentSettings();
        sender.sendMessage("[BiomeIslands-Nether] Status - v" + BiomeIslandsNetherPlugin.VERSION);
        sender.sendMessage("Lava sea: top Y" + (s.lavaLevel() - 1) + " | floor Y" + s.lavaFloorY()
                + " +/- " + s.lavaFloorVariation() + " | ceiling Y" + s.ceilingY() + " +/- " + s.ceilingVariation());
        sender.sendMessage("Main landmasses: spacing ~" + s.averageSpacing() + ", density " + percent(s.density())
                + ", radius " + s.minRadius() + "-" + s.maxRadius() + ", floating " + percent(s.floatingChance()));
        sender.sendMessage("Clusters/travel: satellites " + percent(s.secondaryChance()) + ", stepping islands "
                + (s.steppingIslands() ? "on @ " + percent(s.steppingIslandChance()) : "off"));
        sender.sendMessage("Vanilla stages: caves " + onOff(s.caves()) + ", decorations " + onOff(s.decorations())
                + ", mobs " + onOff(s.mobs()) + ", structures " + onOff(s.structures()));
        sender.sendMessage("Biomes: " + s.enabledBiomeCount() + "/" + s.biomeKeys().size()
                + " landmass themes enabled | background: " + s.backgroundBiome());
        if (sender instanceof Player player) {
            String worldName = player.getWorld().getName();
            sender.sendMessage("World: " + worldName + " | generator observed this startup: "
                    + (plugin.isGeneratorWorld(worldName) ? "yes" : "no"));
        }
        int warnings = plugin.validationWarningCount();
        sender.sendMessage("Config validation: " + (warnings == 0 ? "no warnings" : warnings + " warning(s); see server log"));
    }

    private void sendHere(CommandSender sender) {
        Player player = requirePlayer(sender);
        if (player == null) return;
        NetherSettings s = plugin.currentSettings();
        World world = player.getWorld();
        Location loc = player.getLocation();
        int x = loc.getBlockX();
        int z = loc.getBlockZ();
        NetherLayout layout = new NetherLayout(s);
        NetherLayout.LandmassSample sample = layout.sample(world.getSeed(), x, z);

        sender.sendMessage("[BiomeIslands-Nether] Here: " + world.getName() + " @ " + x + ", " + loc.getBlockY() + ", " + z);
        if (!plugin.isGeneratorWorld(world.getName())) {
            sender.sendMessage("Note: this startup has not observed BiomeIslands-Nether as this world's generator; layout values are predictive only.");
        }
        if (!sample.inside()) {
            sender.sendMessage("Zone: open lava sea | biome: " + s.backgroundBiome());
            if (sample.edgeDepth() > -1.0e100) {
                sender.sendMessage("Nearest sampled landmass edge: ~" + one(Math.max(0.0, -sample.edgeDepth())) + " blocks away");
            }
            return;
        }

        String type = sample.stepping() ? "stepping island" : sample.floating() ? "floating island" : "grounded island";
        String biome = safeKey(s.biomeKeys(), sample.biomeIndex(), "minecraft:nether_wastes");
        sender.sendMessage("Zone: " + type + " | theme: " + biome);
        sender.sendMessage("Edge depth: " + one(sample.edgeDepth()) + " blocks | radius: " + one(sample.radius())
                + " | center: " + one(sample.centerX()) + ", " + one(sample.centerZ()));
    }

    private void sendBiomes(CommandSender sender) {
        NetherSettings s = plugin.currentSettings();
        double total = s.totalBiomeWeight();
        sender.sendMessage("[BiomeIslands-Nether] Enabled landmass biomes:");
        for (int i = 0; i < s.biomeKeys().size() && i < s.biomeWeights().size(); i++) {
            double weight = s.biomeWeights().get(i);
            if (weight <= 0.0) continue;
            sender.sendMessage("- " + s.biomeKeys().get(i) + " | weight " + two(weight)
                    + " (~" + percentOf(weight, total) + ")");
        }
        sender.sendMessage("Open lava/ceiling background biome: " + s.backgroundBiome());
    }

    private void sendStats(CommandSender sender) {
        if (sender instanceof Player player) {
            sendWorldStats(sender, player.getWorld().getName());
            return;
        }
        List<String> worlds = plugin.statWorldNames();
        if (worlds.isEmpty()) {
            sender.sendMessage("[BiomeIslands-Nether] No generation activity recorded since startup.");
            return;
        }
        sender.sendMessage("[BiomeIslands-Nether] Local generation stats since startup:");
        for (String world : worlds) {
            GenerationStats stats = plugin.statsForWorld(world);
            if (stats == null) continue;
            GenerationStats.Snapshot snap = stats.snapshot();
            sender.sendMessage("- " + world + ": " + snap.generatedChunks() + " chunks, "
                    + snap.uniqueLandmasses() + " unique landmasses");
        }
        sender.sendMessage("These counters are local only and reset on restart; nothing is transmitted.");
    }

    private void sendWorldStats(CommandSender sender, String worldName) {
        GenerationStats stats = plugin.statsForWorld(worldName);
        if (stats == null) {
            sender.sendMessage("[BiomeIslands-Nether] No generator activity recorded for world '" + worldName + "' since startup.");
            return;
        }
        GenerationStats.Snapshot snap = stats.snapshot();
        sender.sendMessage("[BiomeIslands-Nether] " + worldName + " stats since startup: " + snap.generatedChunks()
                + " chunks, " + snap.uniqueLandmasses() + " unique landmasses.");
        sender.sendMessage("Types: grounded " + snap.grounded() + ", floating " + snap.floating()
                + ", stepping " + snap.stepping());

        NetherSettings s = plugin.currentSettings();
        long[] counts = snap.landmassesByBiome();
        List<BiomeCount> top = new ArrayList<>();
        for (int i = 0; i < counts.length && i < s.biomeKeys().size(); i++) {
            if (counts[i] > 0) top.add(new BiomeCount(s.biomeKeys().get(i), counts[i]));
        }
        top.sort(Comparator.comparingLong(BiomeCount::count).reversed());
        for (BiomeCount entry : top) sender.sendMessage("- " + entry.key() + ": " + entry.count());
        sender.sendMessage("Local-only counters; reset on restart and never sent anywhere.");
    }

    private void sendDebug(CommandSender sender) {
        Player player = requirePlayer(sender);
        if (player == null) return;
        NetherSettings s = plugin.currentSettings();
        World world = player.getWorld();
        Location loc = player.getLocation();
        int x = loc.getBlockX();
        int z = loc.getBlockZ();
        NetherLayout layout = new NetherLayout(s);
        NetherLayout.LandmassSample sample = layout.sample(world.getSeed(), x, z);
        int floor = layout.lavaFloorY(world.getSeed(), x, z);
        int ceiling = layout.ceilingBottomY(world.getSeed(), x, z);

        sender.sendMessage("[BiomeIslands-Nether] Debug @ " + x + "," + loc.getBlockY() + "," + z);
        sender.sendMessage("World seed: " + world.getSeed() + " | tracked generator world: " + plugin.isGeneratorWorld(world.getName()));
        sender.sendMessage("Lava floor: Y" + floor + " | lava top: Y" + (s.lavaLevel() - 1) + " | ceiling bottom: Y" + ceiling);
        sender.sendMessage("Inside: " + sample.inside() + " | seed: 0x" + Long.toHexString(sample.seed())
                + " | edge depth: " + two(sample.edgeDepth()));
        if (sample.inside()) {
            sender.sendMessage("Type: " + (sample.stepping() ? "stepping" : sample.floating() ? "floating" : "grounded")
                    + " | theme: " + safeKey(s.biomeKeys(), sample.biomeIndex(), "minecraft:nether_wastes"));
            sender.sendMessage("Center: " + one(sample.centerX()) + ", " + one(sample.centerZ()) + " | radius: " + one(sample.radius())
                    + " | interior strength: " + two(layout.interiorStrength(sample)));
            if (sample.floating()) {
                NetherLayout.VerticalBand band = layout.floatingBand(world.getSeed(), x, z, sample, ceiling);
                sender.sendMessage("Floating band here: Y" + band.bottomY() + "..Y" + band.topY());
            } else {
                sender.sendMessage("Ground top here: Y" + layout.groundTopY(world.getSeed(), x, z, sample, ceiling));
            }
        }
    }

    private static Player requirePlayer(CommandSender sender) {
        if (sender instanceof Player player) return player;
        sender.sendMessage("[BiomeIslands-Nether] This subcommand must be used by a player in a world.");
        return null;
    }

    private static boolean withPermission(CommandSender sender, String permission, Runnable action) {
        if (!sender.hasPermission(permission) && !sender.hasPermission("biomeislandsnether.admin")) {
            sender.sendMessage("[BiomeIslands-Nether] You do not have permission: " + permission);
            return true;
        }
        action.run();
        return true;
    }

    private static void help(CommandSender sender, String permission, String syntax, String description) {
        if (sender.hasPermission(permission) || sender.hasPermission("biomeislandsnether.admin")) {
            sender.sendMessage(syntax + " - " + description);
        }
    }

    private static void addIfAllowed(List<String> output, CommandSender sender, String value, String permission, String prefix) {
        if ((sender.hasPermission(permission) || sender.hasPermission("biomeislandsnether.admin")) && value.startsWith(prefix)) {
            output.add(value);
        }
    }

    private static String safeKey(List<String> values, int index, String fallback) {
        return index >= 0 && index < values.size() ? values.get(index) : fallback;
    }

    private static String onOff(boolean value) { return value ? "on" : "off"; }
    private static String one(double value) { return String.format(Locale.ROOT, "%.1f", value); }
    private static String two(double value) { return String.format(Locale.ROOT, "%.2f", value); }
    private static String percent(double value) { return String.format(Locale.ROOT, "%.0f%%", value * 100.0); }
    private static String percentOf(double value, double total) {
        if (total <= 0.0) return "0.0%";
        return String.format(Locale.ROOT, "%.1f%%", (value / total) * 100.0);
    }

    private record BiomeCount(String key, long count) {}
}
