# BiomeIslands-Nether 0.3.1

Created by **Sfekke**. A separate sister project to BiomeIslands, carrying the biome-island idea
inspired by the  **IslandCraft** plugin.

BiomeIslands-Nether targets Paper/Spigot 26.3 and builds a connected lava sea with clustered
Nether-biome landmasses, floating islands, low travel stepping stones and a retained Nether ceiling.


Because BiomeIslands and BiomeIslands-Nether can be installed together, the sister plugin uses its
own non-conflicting command alias:

```text
/biomeislandsnether
/bin
```

Subcommands:

```text
/bin                 Plugin information
/bin version         Plugin version and target
/bin help            Permission-aware command help
/bin status          Active lava/cavern/island settings and validation status
/bin here            Landmass type/theme or open-lava information at your location
/bin biomes          Enabled weighted Nether biome themes
/bin stats           Local generation counters since server startup
/bin debug           Detailed landmass/lava/ceiling diagnostics at your location
```

`/bin stats` tracks generated chunks and unique observed grounded/floating/stepping landmasses in
memory only. Nothing is transmitted or persisted, and counters reset on restart.

### Permissions

All command permissions default to `op`.

```text
biomeislandsnether.admin
biomeislandsnether.command.info
biomeislandsnether.command.version
biomeislandsnether.command.help
biomeislandsnether.command.status
biomeislandsnether.command.here
biomeislandsnether.command.biomes
biomeislandsnether.command.stats
biomeislandsnether.command.debug
```

`biomeislandsnether.admin` grants every command node.

### Startup validation

The plugin prints a concise startup summary and warns about obviously risky/clamped settings such as
a lava floor too close to the lava surface, floating islands colliding heavily with the ceiling,
or stepping islands configured as large as main islands. Validation never rewrites the config.

## Biome weight recipes

No biome-category toggles are used. Keep control explicit with per-biome weights and `weight: 0`.

Examples:

- **Balanced:** use the bundled defaults.
- **Forest-heavy:** raise crimson/warped forest weights and lower wastes/basalt.
- **Hostile volcanic:** raise basalt deltas and soul sand valley, lower forest weights.

## Multiverse

```text
/mv create NetherIslands nether --generator BiomeIslands-Nether
```

Create a new world whenever generation settings change.
Do not add --biome BiomeIslands; the generator supplies its own biome provider.
