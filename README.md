# Strata

A Minecraft mod for NeoForge 1.21.1 that lets you configure mobs, items, blocks, loot and spawns in game, with no file editing needed.

- **Mobs:** variants, stats, abilities, AI goals, equipment, models and textures, sounds, special mobs, and mob replacements
- **Spawns:** rules by tier, biome and dimension, layered on top of the game's own spawns
- **World level:** stages the world climbs as players progress, locking recipes, blocks, items and regions until reached, and scaling mobs with the players
- **Items and blocks:** stats, variants, crafting and machine recipes
- **Loot and trades:** chest loot, mob loot and villager trades
- **Profiles:** save, switch and share whole configs, and upload one to a server
- **Custom assets:** textures and sounds, sent from the server to clients

Open the editor with the **Strata** button on the title screen or the pause menu, with `/strata ui`, or from **Config** on Strata in the Mods list. You can also set a key (K, P or O) as `editorKey` in `strata-common.toml`. Each screen has a **?** button that opens its help page.

## Downloads

- [CurseForge]([https://www.curseforge.com/minecraft/mc-mods/strata-mod])
- The newest build of each branch is on the [Releases](https://github.com/Lancerj27/strata/releases) page, as a `dev-<branch>` prerelease with the jar and a source zip.

## Requirements

- Minecraft 1.21.1
- NeoForge 21.1.113 or newer
- Optional: [Configured](https://modrinth.com/mod/configured) 2.0 or newer (a raw view of the settings file)
- Optional: [Jade](https://modrinth.com/mod/jade) (shows a mob's level)

## Building

You need JDK 21.

```
./gradlew build
```

The jar is written to `build/libs/`. To load Configured and Jade in the dev runs, put their jars in `libs/` (named as `configured_jar` and `jade_jar` in `gradle.properties`). To run the headless self-tests:

```
./gradlew runHeadlessSelfTest
```

## License

MIT. See [LICENSE](LICENSE).
