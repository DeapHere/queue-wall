# Queue Wall

A SeedQueue-style wall for Minecraft 1.0 random seed runs. One instance generates worlds in the background and shows them on an in-game wall with a preview of each spawn. Play the one you like and reset the rest, without running several instances or an AutoHotkey wall.

Inspired by SeedQueue for 1.16+, but written from scratch for 1.0. It shares no code or assets with SeedQueue.

Fabric mod for Minecraft 1.0 (Fabric Loader 0.18.1 or newer, with Ornithe intermediary mappings). The same jar also runs on 1.1 and 1.2.1 to 1.2.5.

> **Status:** Queue Wall is not on legal-builds yet and is waiting on approval from the 1.0 moderators. Under rule A.8.5, runs using it can't be submitted until it's approved.

## Features

- Background world generation on several threads, with a configurable number of worlds waiting behind the wall
- An in-game wall with a preview of each world's spawn
- Locking: locked worlds move to a row along the top of the screen (up to 12 by default)
- Reset, reset all, focus reset and lock keys, plus optional mouse controls
- A reset counter written to `resets.txt` for an OBS text source
- Automatic cleanup of old reset worlds that keeps the files needed for verification (A.7.8, A.7.8.a)

## Fairness

- Worlds are created the same way as Create New World with an empty seed box: a random seed from `new Random().nextLong()`, survival, structures on, not hardcore.
- A queued world only gets what vanilla's "Building terrain" step gives it: the same 17x17 chunks around spawn, in the same order, with the same lighting. It's never ticked while it waits.
- Queued worlds are built by vanilla's own code: the `World` constructor and `Minecraft.prepareWorld` (the Building terrain step), run on a background thread.
- Playing a world goes through vanilla's normal `startGame`, so Simulating world, stats and the player spawn run exactly as in vanilla.
- The preview is drawn by vanilla's `GameRenderer.renderWorld` from the camera of the player vanilla creates for the world: at the spawn point, facing north. It only draws terrain the world already has, never farther than the in-game render distance, with no entities.
- Nothing reads the seed, and worlds are shown in the order they were queued. Nothing sorts or filters them.
- Every reset comes from a key press or mouse click (A.10.5).
- F3 shows `Queue Wall <version>` under the seed line (A.2.11.e).
- Every wall event is written to the game log (`[Queue Wall] queued / generated / reset / locked / unlocked / played New World N`), so a played world can be matched to the video and world files.
- Each loading slot shows vanilla's own progress screen for that world, with its real progress and a percentage (A.9.3).
- Nothing inside a world folder is ever read (A.3.10). Cleanup only looks at folder names and whether `level.dat` exists.
- 1.0's world generator wasn't written for multiple threads, so a few shared caches are made per-thread on the generator threads (see How it works). Worlds come out block-for-block identical to vanilla's Create New World, which the generation test checks (see Building).

## Install

1. Make a 1.0 instance (or 1.1 to 1.2.5) with Fabric Loader (0.20.1 is current) and Ornithe intermediary, for example from the instance generator at https://mc.sr/mods/generate/. Ornithe isn't a separate install: in MultiMC or Prism it's the "Intermediary Mappings" component listed under the instance's Version page, next to Fabric Loader.
2. Put `queuewall-<version>.jar` in the instance's `.minecraft/mods` folder.
3. Start the game. The title screen is unchanged. Create a world through Singleplayer as usual, or press **Y** on the title screen to open the wall.
4. Settings are in `.minecraft/config/queuewall.properties`, which is created on first start. They can also be changed in game: Options on the title screen, then the book button in the top right. Changes apply the next time the wall opens, except `rows`, `columns` and `lockedRow`, which need a restart.

Each queued world uses roughly 40 MB of memory. The defaults (4 on the wall, 4 behind it) fit in 2 GB. Allocate more for larger queues.

## Keys

Hover the mouse over a world, then:

| Key | Action |
|---|---|
| W | Play this world (or the oldest locked world, with `playLockedFirst`) |
| Y | Reset this world (once it shows a preview, unless `resetLoadingWorlds` is on) |
| T | Reset every world on the wall. Locked worlds are kept |
| F | Focus reset: reset the world under the mouse. With `focusResetPlays=true`, play it and reset the rest of the wall instead |
| R | Lock or unlock this world |
| Y or F6 (in a world) | Save and quit, then open the wall. Counts as a reset |
| Esc | Title screen |
| Stop Resets & Quit (Esc, Options..., bottom left) | Save and quit to the title screen instead of the wall |

Mouse: left click plays, right click resets, Shift + left click locks. Each can be turned off in the settings.

## Settings

| Setting | Default | Description |
|---|---|---|
| `rows`, `columns` | 2, 2 | Wall layout, 1 to 4 each |
| `backgroundWorlds` | 4 | Worlds generating behind the wall (0 to 32). Raise it for fast reset-all |
| `generatorThreads` | cores minus 2, at most 3 | Worlds generated at the same time (1 to 16). Leave about 2 cores free for the game |
| `generateWhilePlaying` | false | Keep generating in the background while in a world |
| `previewDistance` | 1 | 1 = normal (128 blocks), 2 = short, 3 = tiny. Never farther than the in-game render distance |
| `previewBuildMillis` | 12 | Milliseconds per frame spent building previews |
| `openWallOnStartup` | false | Open the wall instead of the title screen on launch |
| `returnToWallOnQuit` | true | Save and Quit returns to the wall |
| `keepResetWorlds` | true | Reset worlds keep their folder and `level.dat` |
| `deleteOldWorlds`, `keepNewestWorlds` | true, 6 | Delete old reset worlds, keeping the newest ones and any protected for verification |
| `deletePlayedWorlds` | false | Allow played worlds to be deleted too |
| `resetCounter`, `resetCounterText` | true, `Resets: ` | Write `resets.txt` |
| `showHints` | true | Key hints along the bottom of the wall |
| `showLoadingPercent` | true | Show a % under each slot's loading bar |
| `lockedRow` | true | Move locked worlds to a row along the top |
| `lockedRows`, `lockedColumns` | 0, 0 | Layout of the locked area. 0 = automatic |
| `lockedMax` | 12 | Maximum number of locked worlds (1 to 16) |
| `chainLockedWorlds` | false | Leaving a locked world goes straight into the next locked one |
| `playLockedFirst` | false | The play key plays the oldest locked world first |
| `focusResetPlays` | false | See the F key above |
| `resetLoadingWorlds` | false | Let the reset keys and right click reset worlds that are still loading |
| `clickToPlay`, `clickToReset`, `clickToLock` | true | Mouse controls |
| `keyPlay`, `keyReset`, `keyResetAll`, `keyFocusReset`, `keyLock`, `keyOpenWall`, `keyResetWhilePlaying`, `keyResetWhilePlaying2` | W, Y, T, F, R, Y, Y, F6 | LWJGL key names. `NONE` disables a key |

Worlds are named `New World 1`, `New World 2` and so on. Folders with any other name are never touched.

## How it works

Queue Wall runs vanilla's own code and changes it only through small Mixin hooks, all in `src/main/java/queuewall/mixin`.

- **Generation:** a generator thread makes a `World` for the queued world's folder and calls vanilla's `Minecraft.prepareWorld`, the Building terrain step that loads the 17x17 spawn chunks. While it runs on that thread, `MinecraftMixin` points its reads of the client's world, player, interaction manager and progress screen at the queued world, and stops it before Simulating world, which runs when the world is played. The player vanilla would create for the world is made the same way vanilla's `setWorld` does and used as the preview camera.
- **Thread safety:** 1.0 keeps some generation state in static fields. On Queue Wall's generator threads only:
  1. `IntCache` (the biome layer's array pool) uses one pool per thread, with vanilla's logic (`IntCacheMixin`).
  2. The bounding box and vector pools hand out new objects (`BoxMixin`, `Vec3dMixin`).
  3. Chunk population (trees, ores, sand) runs one chunk at a time across all threads (`ChunkCacheMixin`).

  The main game thread runs vanilla's code unchanged.
- **Previews:** each slot has its own vanilla `WorldRenderer` pointed at its queued world. The wall builds its chunk sections nearest first, then draws the slot with vanilla's `GameRenderer.renderWorld` (sky, fog, clouds and terrain), with the client's world, player and window size swapped for the slot's while it draws. The picture is kept once every section is built, since a queued world never changes.
- **Loading slots:** a world that's still generating is shown with vanilla's `ProgressRenderer`, given the title, stage and percentage `prepareWorld` reported for it.
- **Playing:** vanilla's `startGame` is called for the world's folder, as Create New World does, and given the already-built world in place of a new one. Everything after that is vanilla.
- **Reset key in a world:** presses the Game menu's own Save and Quit to Title, then opens the wall.

## Building

The project is a standard Fabric Loom build with Ornithe's [Ploceus](https://github.com/OrnitheMC/ploceus) plugin and Feather mappings. Gradle needs JDK 25 or newer (Loom requires it). The mod itself targets Java 8, and the game runs on Java 8 or newer.

```
./gradlew build          # the jar is in build/libs
./gradlew runClient      # a development client with the mod
./gradlew runGenTest     # the generation test (below)
```

The Minecraft version and mapping builds are in `gradle.properties`. The build is against 1.0, and the one jar runs on 1.0 to 1.2.5: Ornithe's intermediary names are the same across these versions, so building against any of them gives the same class files. The one call that differs (`WorldSettings` takes a world type from 1.1 on) is picked at runtime in `src/main/java/queuewall/compat/Versions.java`.

To build or test against another version, pass its mapping builds on the command line (1.2.4 has no raven or sparrow build and 1.2.5 no sparrow build, so those are left empty):

```
./gradlew runGenTest -Pminecraft_version=1.1 -Pnests_build=11
./gradlew runGenTest -Pminecraft_version=1.2.1 -Pnests_build=10
./gradlew runGenTest -Pminecraft_version=1.2.5 -Pnests_build=10 -Psparrow_build=
```

**Generation test:** `runGenTest` starts the game and, for 12 fixed seeds, creates each world through vanilla's Create New World path and through Queue Wall's queue (three rounds of 12 worlds on 4 threads), then plays one queued world. It compares every block and its data within 120 blocks of spawn, and fails if any world differs. Vanilla itself can place a few trees differently the first time a seed is generated in a session, so the vanilla reference is generated twice and the second run is used. The test passes on 1.0, 1.1, 1.2.1 and 1.2.5.

## License

MIT. See `LICENSE`.
