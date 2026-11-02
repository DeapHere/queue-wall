# Queue Wall

A SeedQueue-style wall for Minecraft 1.0 random seed runs. One instance generates worlds in the background and shows them on an in-game wall with a preview of each spawn. Play the one you like and reset the rest, without running several instances or an AutoHotkey wall.

Inspired by SeedQueue for 1.16+, but written from scratch for 1.0. It shares no code or assets with SeedQueue.

Fabric mod for Minecraft 1.0 (Fabric Loader 0.18.1 or newer, with Ornithe intermediary mappings). Works with OptiFine 1.0.0 HD D3 through OptiFabric.

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
- Playing a world goes through vanilla's normal `startWorld`, so Building terrain, Simulating world, stats and the player spawn run exactly as in vanilla.
- The preview is drawn from the spawn point, facing north like a new player, with the player's FOV. It only draws terrain the world already has, never farther than the in-game render distance, with no entities.
- Nothing reads the seed, and worlds are shown in the order they were queued. Nothing sorts or filters them.
- Every reset comes from a key press or mouse click (A.10.5).
- F3 shows `Queue Wall <version>` under the seed line (A.2.11.e).
- Every wall event is written to the game log (`[Queue Wall] queued / generated / reset / locked / unlocked / played New World N`), so a played world can be matched to the video and world files.
- Each loading slot shows the Building terrain screen with its real progress and a percentage (A.9.3).
- Nothing inside a world folder is ever read (A.3.10). Cleanup only looks at folder names and whether `level.dat` exists.
- 1.0's world generator wasn't written for multiple threads, so a few shared caches are made per-thread on the generator threads (see How it works). Worlds come out block-for-block identical to single-threaded vanilla generation.

## Install

1. Put `queuewall-<version>.jar` in the instance's `.minecraft/mods` folder.
2. Start the game. The title screen is unchanged. Create a world through Singleplayer as usual, or press **Y** on the title screen to open the wall.
3. Settings are in `.minecraft/config/queuewall.properties`, which is created on first start. Restart the game after changing it.

Each queued world uses roughly 40 MB of memory. The defaults (4 on the wall, 4 behind it) fit in 2 GB. Allocate more for larger queues.

## Keys

Hover the mouse over a world, then:

| Key | Action |
|---|---|
| W or E | Play this world (or the oldest locked world, with `playLockedFirst`) |
| Y | Reset this world |
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
| `lockedRowHeight` | 25 | Height of the locked area, in percent of the screen |
| `lockedRows`, `lockedColumns` | 0, 0 | Layout of the locked area. 0 = automatic |
| `lockedMax` | 12 | Maximum number of locked worlds (1 to 16) |
| `chainLockedWorlds` | false | Leaving a locked world goes straight into the next locked one |
| `playLockedFirst` | false | The play key plays the oldest locked world first |
| `focusResetPlays` | false | See the F key above |
| `clickToPlay`, `clickToReset`, `clickToLock` | true | Mouse controls |
| `keyPlay`, `keyPlay2`, `keyReset`, `keyResetAll`, `keyFocusReset`, `keyLock`, `keyOpenWall`, `keyResetWhilePlaying`, `keyResetWhilePlaying2` | W, E, Y, T, F, R, Y, Y, F6 | LWJGL key names. `NONE` disables a key |

Worlds are named `New World 1`, `New World 2` and so on. Folders with any other name are never touched.

## How it works

- **Generation:** background threads each create a world and load the same 17x17 spawn chunks vanilla's Building terrain step loads.
- **Thread safety:** 1.0 keeps some generation state in static fields. On Queue Wall's generator threads only:
  1. `IntCache` (the biome layer's array pool) uses one pool per thread, with vanilla's logic.
  2. The bounding box and vector pools hand out new objects.
  3. Chunk population (trees, ores, sand) runs one chunk at a time across all threads.

  The main game thread runs vanilla's code unchanged.
- **Previews:** each slot has its own world renderer pointed at its queued world. It builds every chunk section nearest-first, then keeps the finished picture, since a queued world never changes.
- **Playing:** vanilla's `startWorld` is called for the world's folder and given the already-built world in place of a new one. Everything after that is vanilla.

## Building

`build.sh` compiles the jar with plain `javac`. The jars it needs are listed at the top of the script. `client-intermediary.jar` is the remapped jar Fabric creates in `.minecraft/.fabric/remappedJars`.

## License

MIT. See `LICENSE`.
