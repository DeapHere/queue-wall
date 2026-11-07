package queuewall;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import org.lwjgl.input.Keyboard;

/** config/queuewall.properties, written with defaults on first start. */
public final class Config {
    public int rows = 2;
    public int columns = 2;
    public int backgroundWorlds = 4;
    public int generatorThreads = Math.max(1, Math.min(3, Runtime.getRuntime().availableProcessors() - 2));
    public boolean generateWhilePlaying = false;
    public int previewDistance = 1;
    public int previewBuildMillis = 12;
    public boolean openWallOnStartup = false;
    public boolean returnToWallOnQuit = true;
    public boolean focusResetPlays = false;
    public boolean clickToPlay = true;
    public boolean clickToReset = true;
    public boolean clickToLock = true;
    public boolean keepResetWorlds = true;
    public boolean deleteOldWorlds = true;
    public int keepNewestWorlds = 6;
    public boolean deletePlayedWorlds = false;
    public boolean resetCounter = true;
    public String resetCounterText = "Resets: ";
    public boolean showHints = true;
    public boolean showLoadingPercent = true;
    public boolean lockedRow = true;
    public int lockedRowHeight = 25;
    public int lockedMax = 12;
    public int lockedRows = 0;
    public int lockedColumns = 0;
    public boolean playLockedFirst = false;
    public boolean chainLockedWorlds = false;
    public boolean resetLoadingWorlds = false;

    public int keyPlay = Keyboard.KEY_W;
    public int keyReset = Keyboard.KEY_Y;
    public int keyResetAll = Keyboard.KEY_T;
    public int keyFocusReset = Keyboard.KEY_F;
    public int keyLock = Keyboard.KEY_R;
    public int keyOpenWall = Keyboard.KEY_Y;
    public int keyResetWhilePlaying = Keyboard.KEY_Y;
    public int keyResetWhilePlaying2 = Keyboard.KEY_F6;

    private static final int VERSION = 2;

    /** When the settings file was last read or written, to see whether it was edited while the game is open. */
    private long readStamp;
    private File file;

    public static Config load(File dir) {
        Config c = read(new File(dir, "queuewall.properties"));
        c.save(c.file);
        c.readStamp = c.file.lastModified();
        return c;
    }

    /**
     * Applies queuewall.properties again if it was edited since it was last read, so most settings change without a
     * restart. The wall layout (rows, columns, lockedRow) stays as the game started with it. Returns whether it reloaded.
     */
    public boolean reloadIfChanged() {
        return reload(false);
    }

    boolean reload(boolean always) {
        if (file == null || (!always && file.lastModified() == readStamp)) return false;
        Config n = read(file);
        readStamp = file.lastModified();
        try {
            for (java.lang.reflect.Field fd : Config.class.getDeclaredFields()) {
                int m = fd.getModifiers();
                if (java.lang.reflect.Modifier.isStatic(m) || !java.lang.reflect.Modifier.isPublic(m)) continue;
                String name = fd.getName();
                if (name.equals("rows") || name.equals("columns") || name.equals("lockedRow")) continue;
                fd.set(this, fd.get(n));
            }
        } catch (IllegalAccessException e) {
            System.out.println("[Queue Wall] couldn't apply the settings: " + e);
            return false;
        }
        System.out.println("[Queue Wall] settings reloaded");
        return true;
    }

    /** A separate copy read from the file, for the settings screen to edit. */
    Config copyFromFile() {
        return read(file);
    }

    /** Writes this copy to the settings file. The live settings pick it up with reloadIfChanged. */
    void writeFile() {
        save(file);
    }

    private static Config read(File f) {
        Config c = new Config();
        c.file = f;
        Properties p = new Properties();
        if (f.isFile()) {
            try (InputStream in = new FileInputStream(f)) {
                p.load(in);
            } catch (Exception e) {
                System.out.println("[Queue Wall] couldn't read " + f + ": " + e);
            }
        }
        // settings files from 0.1.x: the defaults for these changed, so they're reset once
        boolean old = i(p, "configVersion", 1) < VERSION;
        if (old) {
            p.remove("previewDistance");
            p.remove("previewBuildMillis");
            p.remove("openWallOnStartup");
            p.remove("keyResetWhilePlaying");
        }
        c.rows = clamp(i(p, "rows", c.rows), 1, 4);
        c.columns = clamp(i(p, "columns", c.columns), 1, 4);
        c.backgroundWorlds = clamp(i(p, "backgroundWorlds", c.backgroundWorlds), 0, 32);
        c.generatorThreads = clamp(i(p, "generatorThreads", c.generatorThreads), 1, 16);
        c.generateWhilePlaying = b(p, "generateWhilePlaying", c.generateWhilePlaying);
        c.previewDistance = clamp(i(p, "previewDistance", c.previewDistance), 1, 3);
        c.previewBuildMillis = clamp(i(p, "previewBuildMillis", c.previewBuildMillis), 1, 50);
        c.openWallOnStartup = b(p, "openWallOnStartup", c.openWallOnStartup);
        c.returnToWallOnQuit = b(p, "returnToWallOnQuit", c.returnToWallOnQuit);
        c.focusResetPlays = b(p, "focusResetPlays", c.focusResetPlays);
        c.clickToPlay = b(p, "clickToPlay", c.clickToPlay);
        c.clickToReset = b(p, "clickToReset", c.clickToReset);
        c.clickToLock = b(p, "clickToLock", c.clickToLock);
        c.keepResetWorlds = b(p, "keepResetWorlds", c.keepResetWorlds);
        c.deleteOldWorlds = b(p, "deleteOldWorlds", c.deleteOldWorlds);
        c.keepNewestWorlds = clamp(i(p, "keepNewestWorlds", c.keepNewestWorlds), 5, 1000);
        c.deletePlayedWorlds = b(p, "deletePlayedWorlds", c.deletePlayedWorlds);
        c.resetCounter = b(p, "resetCounter", c.resetCounter);
        c.resetCounterText = p.getProperty("resetCounterText", c.resetCounterText);
        c.showHints = b(p, "showHints", c.showHints);
        c.showLoadingPercent = b(p, "showLoadingPercent", c.showLoadingPercent);
        c.lockedRow = b(p, "lockedRow", c.lockedRow);
        c.lockedRowHeight = clamp(i(p, "lockedRowHeight", c.lockedRowHeight), 10, 40);
        c.lockedMax = clamp(i(p, "lockedMax", c.lockedMax), 1, 16);
        c.lockedRows = clamp(i(p, "lockedRows", c.lockedRows), 0, 4);
        c.lockedColumns = clamp(i(p, "lockedColumns", c.lockedColumns), 0, 16);
        c.playLockedFirst = b(p, "playLockedFirst", c.playLockedFirst);
        c.chainLockedWorlds = b(p, "chainLockedWorlds", c.chainLockedWorlds);
        c.keyPlay = k(p, "keyPlay", c.keyPlay);
        c.keyReset = k(p, "keyReset", c.keyReset);
        c.keyResetAll = k(p, "keyResetAll", c.keyResetAll);
        c.keyFocusReset = k(p, "keyFocusReset", c.keyFocusReset);
        c.keyLock = k(p, "keyLock", c.keyLock);
        c.keyOpenWall = k(p, "keyOpenWall", c.keyOpenWall);
        c.keyResetWhilePlaying = k(p, "keyResetWhilePlaying", c.keyResetWhilePlaying);
        c.keyResetWhilePlaying2 = k(p, "keyResetWhilePlaying2", c.keyResetWhilePlaying2);
        c.resetLoadingWorlds = b(p, "resetLoadingWorlds", c.resetLoadingWorlds);
        c.warnDuplicateKeys();
        return c;
    }

    private void save(File f) {
        f.getParentFile().mkdirs();
        try (OutputStream out = new FileOutputStream(f); Writer w = new OutputStreamWriter(out, StandardCharsets.ISO_8859_1)) {
            w.write("# Queue Wall settings. Changes apply the next time the wall opens, except rows, columns and lockedRow, which need a restart.\n");
            w.write("configVersion=" + VERSION + "\n\n");
            w.write("# wall layout, 1 to 4 each\n");
            w.write("rows=" + rows + "\ncolumns=" + columns + "\n\n");
            w.write("# extra worlds kept generating behind the wall, so a reset is replaced straight away (0 to 32).\n# raise it if you reset the whole wall quickly and see loading screens\n");
            w.write("# every world uses memory (roughly 40 MB each), 8 in total is fine with 2 GB, give the instance more if you raise this\n");
            w.write("backgroundWorlds=" + backgroundWorlds + "\n\n");
            w.write("# how many worlds generate at the same time, 1 to 16. leave about 2 cores free for the game\n");
            w.write("generatorThreads=" + generatorThreads + "\n\n");
            w.write("# false = background generation pauses while you're in a world, so it can't cost you frames\n");
            w.write("generateWhilePlaying=" + generateWhilePlaying + "\n\n");
            w.write("# preview render distance: 1 = normal (the whole spawn area, 128 blocks), 2 = short, 3 = tiny. never farther than your in-game render distance\n");
            w.write("previewDistance=" + previewDistance + "\n\n");
            w.write("# time per frame spent building preview chunks. higher fills previews faster but lowers wall fps\n");
            w.write("previewBuildMillis=" + previewBuildMillis + "\n\n");
            w.write("# open the wall instead of the title screen when the game starts. keyOpenWall opens it from the title screen either way\n");
            w.write("openWallOnStartup=" + openWallOnStartup + "\n\n");
            w.write("# Save and Quit (and the death screen's Title Screen button) go back to the wall\n");
            w.write("returnToWallOnQuit=" + returnToWallOnQuit + "\n\n");
            w.write("# mouse on the wall: left click plays the world under the mouse, right click resets it,\n");
            w.write("# shift + left click locks it. false turns that click off (the keys still work)\n");
            w.write("clickToPlay=" + clickToPlay + "\n");
            w.write("clickToReset=" + clickToReset + "\n");
            w.write("clickToLock=" + clickToLock + "\n\n");
            w.write("# reset worlds keep their folder and level.dat in saves (no chunks), like worlds reset by the old wall did\n");
            w.write("keepResetWorlds=" + keepResetWorlds + "\n\n");
            w.write("# delete old 'New World <number>' folders, keeping the newest keepNewestWorlds (at least 5, A.7.8).\n");
            w.write("# worlds you actually played are only deleted with deletePlayedWorlds=true. keep the world of\n");
            w.write("# any run you submit until it's verified (A.7.2.a). renaming a folder always keeps it\n");
            w.write("deleteOldWorlds=" + deleteOldWorlds + "\n");
            w.write("keepNewestWorlds=" + keepNewestWorlds + "\n");
            w.write("deletePlayedWorlds=" + deletePlayedWorlds + "\n\n");
            w.write("# resets.txt in .minecraft, for an OBS text source. delete it to start from 0\n");
            w.write("resetCounter=" + resetCounter + "\n");
            w.write("resetCounterText=" + resetCounterText + "\n\n");
            w.write("# key hints along the bottom of the wall\n");
            w.write("showHints=" + showHints + "\n\n");
            w.write("# the % under each loading bar on the wall. vanilla 1.0 only shows the bar, the % is there so the\n");
            w.write("# recording shows each world's progress legibly (A.9.3). false = exactly the vanilla loading screen\n");
            w.write("showLoadingPercent=" + showLoadingPercent + "\n\n");
            w.write("# locking a world moves it to an area along the top of the wall, and its wall slot gets a new world.\n");
            w.write("# lockedRowHeight is that area's height in percent of the screen (10 to 40), lockedMax how many worlds it holds (1 to 16)\n");
            w.write("lockedRow=" + lockedRow + "\n");
            w.write("lockedRowHeight=" + lockedRowHeight + "\n");
            w.write("lockedMax=" + lockedMax + "\n");
            w.write("# rows and columns of the locked area. 0 picks them from lockedMax so each world keeps roughly the screen's shape.\n");
            w.write("# with both set, the area holds lockedRows x lockedColumns worlds and lockedMax is ignored\n");
            w.write("lockedRows=" + lockedRows + "\n");
            w.write("lockedColumns=" + lockedColumns + "\n\n");
            w.write("# the play key plays the first locked world (oldest lock) while there is one, wherever the mouse is.\n");
            w.write("# clicking still plays the world you click\n");
            w.write("playLockedFirst=" + playLockedFirst + "\n\n");
            w.write("# true: leaving a locked world (reset key, Save and Quit, or dying) goes straight into the next locked world,\n");
            w.write("# and the wall comes back once every locked world has been played. false: always back to the wall\n");
            w.write("chainLockedWorlds=" + chainLockedWorlds + "\n\n");
            w.write("# true: the reset keys and right click also reset worlds that are still loading. false: they only reset\n");
            w.write("# worlds showing a preview, so spamming reset can't throw away worlds that are partly generated\n");
            w.write("resetLoadingWorlds=" + resetLoadingWorlds + "\n\n");
            w.write("# keys use LWJGL names: W, E, Y, F6, LSHIFT, NUMPAD1 and so on. NONE turns a key off\n");
            w.write("keyPlay=" + name(keyPlay) + "\n");
            w.write("keyReset=" + name(keyReset) + "\n");
            w.write("keyResetAll=" + name(keyResetAll) + "\n");
            w.write("keyFocusReset=" + name(keyFocusReset) + "\n");
            w.write("# false: the focus reset key resets the world under the mouse. true: it plays that world and resets the rest of the wall\n");
            w.write("focusResetPlays=" + focusResetPlays + "\n");
            w.write("keyLock=" + name(keyLock) + "\n");
            w.write("# on the title screen: opens the wall\n");
            w.write("keyOpenWall=" + name(keyOpenWall) + "\n");
            w.write("# in a world: save and quit, then back to the wall (counts as a reset)\n");
            w.write("keyResetWhilePlaying=" + name(keyResetWhilePlaying) + "\n");
            w.write("keyResetWhilePlaying2=" + name(keyResetWhilePlaying2) + "\n");
        } catch (Exception e) {
            System.out.println("[Queue Wall] couldn't write " + f + ": " + e);
        }
    }

    // the wall checks resetAll, then focusReset, then play, reset and lock, so a key in two of them only does the first
    private void warnDuplicateKeys() {
        String[] names = {"keyResetAll", "keyFocusReset", "keyPlay", "keyReset", "keyLock"};
        int[] keys = {keyResetAll, keyFocusReset, keyPlay, keyReset, keyLock};
        for (int i = 0; i < keys.length; i++) {
            for (int j = i + 1; j < keys.length; j++) {
                if (keys[i] != Keyboard.KEY_NONE && keys[i] == keys[j]) {
                    System.out.println("[Queue Wall] " + names[i] + " and " + names[j] + " are both " + name(keys[i]) + ", it only does " + names[i]);
                }
            }
        }
    }

    private static String name(int key) {
        return key == Keyboard.KEY_NONE ? "NONE" : Keyboard.getKeyName(key);
    }

    private static int k(Properties p, String key, int def) {
        String v = p.getProperty(key);
        if (v == null) return def;
        v = v.trim().toUpperCase();
        if (v.isEmpty() || v.equals("NONE")) return Keyboard.KEY_NONE;
        int idx = Keyboard.getKeyIndex(v);
        if (idx == Keyboard.KEY_NONE) {
            System.out.println("[Queue Wall] unknown key '" + v + "' for " + key + ", using " + name(def));
            return def;
        }
        return idx;
    }

    private static int i(Properties p, String key, int def) {
        try {
            return Integer.parseInt(p.getProperty(key, "" + def).trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static boolean b(Properties p, String key, boolean def) {
        String v = p.getProperty(key);
        return v == null ? def : Boolean.parseBoolean(v.trim());
    }

    /** How many worlds fit in the locked row, 0 when it's off. */
    public int lockedRowSize() {
        if (!lockedRow) return 0;
        if (lockedRows > 0 && lockedColumns > 0) return lockedRows * lockedColumns;
        return lockedMax;
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
