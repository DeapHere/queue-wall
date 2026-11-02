# Queue Wall for Minecraft 1.0

A SeedQueue-style wall for 1.0 random seed runs. One instance generates worlds in the background and shows them on an in-game wall with a live preview of each spawn. You play the one you like and reset the rest, without running 4 instances or an AutoHotkey script.

Inspired by SeedQueue for 1.16+, but written from scratch for 1.0 and sharing no code or assets with it.

It's a Fabric mod for your existing 1.0 loader instance (Fabric Loader 0.18.1 with the 1.0 intermediary mappings).

---

## Before you use it for runs

**This mod isn't approved.** Rule A.8.5 only allows mods on the legal list, so runs done with Queue Wall can't be submitted until the 1.0 moderators approve it. Until then, keep using the 4-instance AHK wall for real runs and treat this as a test build. The source is in `queue-wall-source.zip` so the mods can read everything it does.

Things the mods will probably want to know (all of it is in the source):

- Every world is made the same way as Create New World with an empty seed box: a random seed from `new Random().nextLong()`, survival, structures on, not hardcore.
- In the background, a world only gets what vanilla's "Building terrain" step gives it: the same 17x17 chunks around spawn, loaded in the same order, with the same lighting. It is never ticked. When you press play, the world goes through vanilla's normal `startWorld`, so "Building terrain", "Simulating world for a bit", stats and the player spawn all run exactly as they do in vanilla.
- The preview is drawn from where the player will spawn, facing where the player will face (north, like vanilla), with your FOV setting. Nothing reads the seed, and nothing shows a map or anything beyond what the spawn view shows.
- F3 shows `Queue Wall <version>` under the seed, so every run's recording shows the mod and its version.
- Every wall event goes to the game log: `[Queue Wall] queued / generated / reset / locked / unlocked / played New World N`. Verifiers can match the world you played to your video and world files. Nothing about the seed or the map is logged.
- Every reset comes from a key press or a mouse click (A.10.5). One key can reset several worlds, the same as the AHK wall's reset all.
- Reset worlds keep their folder and `level.dat`, the same as the worlds the AHK wall reset. Played worlds are never deleted automatically. For the last 30 worlds you played, the 5 worlds before it and every world queued after it are kept too (A.7.8, A.7.8.a).
- Each loading slot shows the vanilla loading screen with its progress as a percentage, so the recording shows every world's generation (A.9.3).
- The mod never reads anything inside a world folder. Cleanup only looks at folder names and whether `level.dat` exists, and played worlds are tracked in `config/queuewall-played.txt`. On the first start of 0.2.3, every world already in `saves` is marked to keep.
- 1.0's world generator wasn't written to run on more than one thread, so the mod patches 4 things (listed under "How it works"). The worlds come out the same as single-threaded generation: 48 worlds generated 6 at a time matched the same seeds generated one by one, block for block.

## Install

1. Put `queuewall-0.3.4.jar` in your 1.0 instance's `.minecraft/mods` folder, next to StatsPerReset. They work together.
2. Start the game. It opens on the normal title screen. Press **Y** (`keyOpenWall`) to open the wall and start generating. The title screen itself stays vanilla. Set `openWallOnStartup=true` to skip the title screen.
3. Settings are in `.minecraft/config/queuewall.properties`. The file appears on the first start. Restart the game after changing it.

OptiFine: works with OptiFine 1.0.0 HD D3 through OptiFabric. Before 0.2.2, OptiFine made the previews stop after a few chunks, which left most of the spawn area missing.

Memory: each queued world takes about 40 MB. The default is 8 worlds (4 on the wall and 4 behind it), which used about 410 MB in testing, so your current 2048 MB is fine.

## OBS

You only need one instance now, so there's just one Game Capture of the game window. You don't need the `1.0 Wall` scene, the instance scenes, the lock picture sources or the scene hotkeys anymore. The wall and the lock pictures are drawn in the game itself, so they're also on the recording. If you like the fullscreen projector, point it at the scene with the game capture.

The reset counter still works. The mod writes `resets.txt` in `.minecraft` with the same `Resets: 123` text, so point your OBS Text source at that file. To keep your old count, copy your old `resets.txt` into `.minecraft`.

## Keys

The defaults match your AHK wall. Hover the mouse over a world, then:

| Key | What it does |
|---|---|
| W or E | Play the first locked world if there is one (`playLockedFirst`), otherwise this world |
| Y | Reset this world. On the wall, Y skips locked worlds. In the locked row, Y resets the locked world |
| T | Reset every world on the wall. The locked row is left alone |
| F | Focus reset: resets the world under the mouse. With `focusResetPlays=true` it plays this world and resets the rest of the wall instead, like the old AHK wall |
| R | Lock this world: it moves to the locked area along the top (up to 12 worlds), and its wall slot gets a new world. R on a locked world unlocks it, and it goes back on the wall in the next free slot |
| Y or F6 (in game) | Save and quit, then go straight to the wall. Counts as a reset. Works in any world, so you can make your first world through Singleplayer and open the wall from it |
| Esc | Title screen |
| Y (on the title screen) | Open the wall |
| Stop Resets & Quit (Esc, Options..., bottom left) | Save and quit to the title screen instead of the wall or the next locked world. Locked worlds stay locked |

Mouse: left click plays, right click resets, Shift + left click locks. Turn each one off with `clickToPlay=false`, `clickToReset=false` or `clickToLock=false`. The keys keep working.

Esc > Save and Quit to Title also goes back to the wall, and so does the death screen's Title Screen button.

## Settings

| Setting | Default | |
|---|---|---|
| `rows`, `columns` | 2, 2 | Wall layout, 1 to 4 each |
| `backgroundWorlds` | 4 | Worlds kept generating behind the wall, so a reset gets replaced straight away (up to 32). Raise it if you reset the whole wall quickly |
| `generatorThreads` | your cores minus 2, at most 3 | How many worlds generate at the same time (up to 16). Leave about 2 cores free for the game |
| `generateWhilePlaying` | false | false pauses background generation while you're in a world, so it can't cost you frames |
| `previewDistance` | 1 | 1 = normal (the whole 128 block spawn area), 2 = short, 3 = tiny. Never farther than your in-game render distance |
| `previewBuildMillis` | 12 | Time per frame spent building previews. Higher fills them in faster but lowers wall FPS |
| `openWallOnStartup` | false | Start on the wall instead of the title screen |
| `returnToWallOnQuit` | true | Save and Quit goes back to the wall |
| `keepResetWorlds` | true | Reset worlds keep their folder and level.dat |
| `deleteOldWorlds` | true | Delete old `New World <number>` folders, keeping the newest `keepNewestWorlds` (at least 5, A.7.8) |
| `keepNewestWorlds` | 6 | |
| `deletePlayedWorlds` | false | Off means worlds you actually played are never deleted. Keep anything you submit until it's verified (A.7.2.a) |
| `resetCounter`, `resetCounterText` | true, `Resets: ` | resets.txt for OBS |
| `showHints` | true | Key hints along the bottom of the wall |
| `showLoadingPercent` | true | The % under each slot's loading bar. Vanilla 1.0 only shows the bar. The % is there so recordings show each world's progress legibly (A.9.3). false gives exactly the vanilla screen |
| `lockedRow` | true | Locked worlds move to an area along the top. false keeps them in their wall slot with the lock picture, like 0.2.1 |
| `lockedRowHeight` | 25 | Height of the locked area in percent of the screen (10 to 40) |
| `lockedRows`, `lockedColumns` | 0, 0 | Rows and columns of the locked area. 0 works them out from `lockedMax`. With both set, the area holds rows x columns worlds |
| `lockedMax` | 12 | How many worlds can be locked (1 to 16). They're laid out in as many rows as keeps each one close to the screen's shape, so 2 rows of 6 by default |
| `chainLockedWorlds` | false | After playing a locked world, leaving it (Y, F6, Save and Quit or dying) goes straight into the next locked world. The wall comes back once every locked world has been played |
| `playLockedFirst` | false | W and E play the first locked world (oldest lock) while there is one, wherever the mouse is. Clicking still plays the world you click |
| `keyPlay`, `keyPlay2`, `keyReset`, `keyResetAll`, `keyFocusReset`, `keyLock`, `keyResetWhilePlaying`, `keyResetWhilePlaying2` | W, E, Y, T, F, R, Y, F6 | LWJGL key names, `NONE` turns one off |

Worlds are named `New World 1`, `New World 2` and so on. Rename a folder to anything else and the mod will never touch it.

## Not handled yet

- **LiveSplit:** the AHK wall pressed your LiveSplit start and reset keys. The mod can't press keys in other programs, so start the timer yourself. Or keep a small AHK script running just for that.
- **Instance moving and priority:** you don't need these with one instance. Background generation pauses while you play (`generateWhilePlaying=false`).

## How it works

- **Generation:** a few background threads each make a world (`new World(...)`), then load the same 17x17 chunks around spawn that vanilla's "Building terrain" loads. The world is ready when that's done.
- **Thread safety:** 1.0 keeps some world generation state in static fields, and parallel generation crashes vanilla straight away. The mod patches these:
  1. `IntCache` (the biome layer's array pool) becomes one pool per thread. Same logic, just not shared.
  2. Chunk population (trees, ores, sand and so on) runs one chunk at a time across all threads. It uses the shared biome decorators and `BlockSand.fallInstantly`.
  3. The bounding box pool and the vector pool give fresh objects on generator threads. The main thread keeps the vanilla pools.
- **Previews:** each wall slot has its own world renderer pointed at its queued world, drawn into its quarter of the window. Each preview builds every chunk section in the spawn area, nearest first, and shows "Loading preview..." until all of them are built. Then it draws the view once and keeps that picture, since a queued world never changes. The previews only draw chunks the world already has (the 128 block spawn area), so they never make the game generate more. The sky is plain fog colour, and there are no entities.
- **Playing:** the mod calls vanilla's `startWorld` for the world's folder and hands it the world it already made in place of building a new one. Everything after that is vanilla.

## Testing

Tested on Linux with Minecraft 1.0 on software OpenGL, with and without OptiFine 1.0.0 HD D3. Fabric's servers were blocked on the test machine, so the mod ran through a small stand-in loader using the same Mixin and MixinExtras versions your loader uses (sponge-mixin 0.16.5+mixin.0.8.7, MixinExtras 0.5.0). 0.2.1 and later have also run with the real Fabric Loader on Windows.

What was checked:

- The wall fills with 4 previews about 10 seconds after launch.
- Playing from the wall loads the world, and the player's position and view match the preview.
- F6 and Save and Quit go back to the wall, and the played world is saved.
- Y, T, F and R work, and locked worlds survive T and F.
- `resets.txt` counts every reset.
- Reset worlds keep their `level.dat`, and leftover folders from the last session get cleaned up.
- Old reset worlds are trimmed to the newest 6, and played worlds are kept.
- 48 worlds generated 6 at a time matched single-threaded generation block for block.
- 12 rapid T presses in a row didn't cause errors.

## Building

`build.sh` builds the jar with plain `javac`. See the top of the script for the four jars it needs. One of them is the `client-intermediary.jar` Fabric already made in your instance's `.minecraft/.fabric/remappedJars` folder.
