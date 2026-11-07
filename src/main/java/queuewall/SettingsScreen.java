package queuewall;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import org.lwjgl.input.Keyboard;

/**
 * Queue Wall's settings in game (the QW button on the Options screen from the title screen). It edits a copy of
 * queuewall.properties, writes the file on Done, and the wall applies it the next time it opens.
 */
public final class SettingsScreen extends Screen {
    private static final int PER_PAGE = 12;
    private static final int PREV = 900, NEXT = 901, DONE = 902;

    private final Screen parent;
    private final Config c;
    private final List<Entry> entries = new ArrayList<>();
    private int page;
    private Entry waitingForKey;
    private ButtonWidget waitingButton;

    public SettingsScreen(Screen parent) {
        this.parent = parent;
        QueueWall.init(null);
        this.c = QueueWall.cfg.copyFromFile();
        // the layout ones only change after a restart, since the wall's slots are made when generation starts
        num("Rows (restart)", "Rows of worlds on the wall", 1, 4, () -> c.rows, v -> c.rows = v);
        num("Columns (restart)", "Columns of worlds on the wall", 1, 4, () -> c.columns, v -> c.columns = v);
        num("Background worlds", "Extra worlds generating behind the wall (about 40 MB each)", 0, 32, () -> c.backgroundWorlds, v -> c.backgroundWorlds = v);
        num("Generator threads", "Worlds generating at once. Leave about 2 cores free", 1, 16, () -> c.generatorThreads, v -> c.generatorThreads = v);
        bool("Generate while playing", "Off: generation pauses while you're in a world", () -> c.generateWhilePlaying, v -> c.generateWhilePlaying = v);
        num("Preview distance", "1 = normal, 2 = short, 3 = tiny", 1, 3, () -> c.previewDistance, v -> c.previewDistance = v);
        num("Preview build ms", "Time per frame building previews. Higher = faster previews, lower fps", 1, 50, () -> c.previewBuildMillis, v -> c.previewBuildMillis = v);
        bool("Reset loading worlds", "On: reset also works on worlds that are still loading", () -> c.resetLoadingWorlds, v -> c.resetLoadingWorlds = v);
        bool("Open wall on startup", "Open the wall instead of the title screen", () -> c.openWallOnStartup, v -> c.openWallOnStartup = v);
        bool("Back to wall on quit", "Save and Quit goes back to the wall", () -> c.returnToWallOnQuit, v -> c.returnToWallOnQuit = v);
        bool("Show key hints", "The key hints along the bottom of the wall", () -> c.showHints, v -> c.showHints = v);
        bool("Show loading %", "The % under each loading bar", () -> c.showLoadingPercent, v -> c.showLoadingPercent = v);

        bool("Click to play", "Left click plays the world under the mouse", () -> c.clickToPlay, v -> c.clickToPlay = v);
        bool("Click to reset", "Right click resets the world under the mouse", () -> c.clickToReset, v -> c.clickToReset = v);
        bool("Shift+click to lock", "Shift + left click locks the world under the mouse", () -> c.clickToLock, v -> c.clickToLock = v);
        bool("Focus reset plays", "On: the focus reset key plays that world and resets the rest", () -> c.focusResetPlays, v -> c.focusResetPlays = v);
        bool("Locked area (restart)", "Locked worlds move to an area along the top", () -> c.lockedRow, v -> c.lockedRow = v);
        num("Locked area height %", "Height of the locked area, in % of the screen", 10, 40, () -> c.lockedRowHeight, v -> c.lockedRowHeight = v);
        num("Locked max", "How many worlds the locked area holds", 1, 16, () -> c.lockedMax, v -> c.lockedMax = v);
        num("Locked rows", "0 = picked from Locked max", 0, 4, () -> c.lockedRows, v -> c.lockedRows = v);
        num("Locked columns", "0 = picked from Locked max", 0, 16, () -> c.lockedColumns, v -> c.lockedColumns = v);
        bool("Play locked first", "The play keys play the oldest locked world first", () -> c.playLockedFirst, v -> c.playLockedFirst = v);
        bool("Chain locked worlds", "Leaving a locked world goes straight into the next locked one", () -> c.chainLockedWorlds, v -> c.chainLockedWorlds = v);
        bool("Reset counter", "Writes resets.txt for OBS", () -> c.resetCounter, v -> c.resetCounter = v);

        key("Play", () -> c.keyPlay, v -> c.keyPlay = v);
        key("Play (second key)", () -> c.keyPlay2, v -> c.keyPlay2 = v);
        key("Reset", () -> c.keyReset, v -> c.keyReset = v);
        key("Reset all", () -> c.keyResetAll, v -> c.keyResetAll = v);
        key("Focus reset", () -> c.keyFocusReset, v -> c.keyFocusReset = v);
        key("Lock", () -> c.keyLock, v -> c.keyLock = v);
        key("Open wall (title)", () -> c.keyOpenWall, v -> c.keyOpenWall = v);
        key("Reset in world", () -> c.keyResetWhilePlaying, v -> c.keyResetWhilePlaying = v);
        key("Reset in world (2nd)", () -> c.keyResetWhilePlaying2, v -> c.keyResetWhilePlaying2 = v);
        bool("Keep reset worlds", "Reset worlds keep their folder and level.dat", () -> c.keepResetWorlds, v -> c.keepResetWorlds = v);
        bool("Delete old worlds", "Delete old New World folders, keeping the newest ones", () -> c.deleteOldWorlds, v -> c.deleteOldWorlds = v);
        bool("Delete played worlds", "Also delete worlds you played. Keep a run's world until it's verified", () -> c.deletePlayedWorlds, v -> c.deletePlayedWorlds = v);
        list("Keep newest worlds", "How many New World folders are kept (at least 5)", new int[]{5, 6, 8, 10, 15, 20, 30, 50, 100, 200, 500, 1000}, () -> c.keepNewestWorlds, v -> c.keepNewestWorlds = v);
    }

    @Override
    public void init() {
        this.buttons.clear();
        waitingForKey = null;
        int pages = pages();
        if (page >= pages) page = pages - 1;
        for (int i = 0; i < PER_PAGE; i++) {
            int idx = page * PER_PAGE + i;
            if (idx >= entries.size()) break;
            this.buttons.add(new ButtonWidget(idx, this.width / 2 - 155 + i % 2 * 160, this.height / 6 + 24 * (i >> 1) - 12, 150, 20, entries.get(idx).label()));
        }
        int y = this.height / 6 + 24 * 6 - 4;
        ButtonWidget prev = new ButtonWidget(PREV, this.width / 2 - 155, y, 70, 20, "< Back");
        ButtonWidget next = new ButtonWidget(NEXT, this.width / 2 + 85, y, 70, 20, "Next >");
        prev.active = page > 0;
        next.active = page < pages - 1;
        this.buttons.add(prev);
        this.buttons.add(next);
        this.buttons.add(new ButtonWidget(DONE, this.width / 2 - 100, this.height / 6 + 168, "Done"));
    }

    private int pages() {
        return (entries.size() + PER_PAGE - 1) / PER_PAGE;
    }

    @Override
    protected void buttonClicked(ButtonWidget button) {
        if (!button.active) return;
        if (button.id == DONE) {
            c.writeFile();
            QueueWall.applySettingsScreen();
            this.minecraft.openScreen(parent);
        } else if (button.id == PREV) {
            page--;
            init();
        } else if (button.id == NEXT) {
            page++;
            init();
        } else if (button.id >= 0 && button.id < entries.size()) {
            Entry e = entries.get(button.id);
            if (e.isKey()) {
                if (waitingButton != null && waitingForKey != null) waitingButton.message = waitingForKey.label();
                waitingForKey = e;
                waitingButton = button;
                button.message = "> " + e.name + " <";
            } else {
                e.click(Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT));
                button.message = e.label();
            }
        }
    }

    @Override
    protected void keyPressed(char chr, int key) {
        if (waitingForKey != null) {
            // Escape cancels, Delete or Backspace turns the key off
            if (key != Keyboard.KEY_ESCAPE) {
                waitingForKey.setKey(key == Keyboard.KEY_DELETE || key == Keyboard.KEY_BACK ? Keyboard.KEY_NONE : key);
            }
            waitingButton.message = waitingForKey.label();
            waitingForKey = null;
            waitingButton = null;
            return;
        }
        // Escape leaves without saving
        if (key == Keyboard.KEY_ESCAPE) this.minecraft.openScreen(parent);
    }

    @Override
    public void render(int mouseX, int mouseY, float tickDelta) {
        this.renderBackground();
        this.drawCenteredString(this.textRenderer, "Queue Wall settings (" + (page + 1) + "/" + pages() + ")", this.width / 2, 5, 0xFFFFFF);
        super.render(mouseX, mouseY, tickDelta);
        String help = null;
        for (ButtonWidget b : this.buttons) {
            if (b.id >= 0 && b.id < entries.size() && mouseX >= b.x && mouseX < b.x + 150 && mouseY >= b.y && mouseY < b.y + 20) {
                help = entries.get(b.id).help();
            }
        }
        if (waitingForKey != null) help = "Press a key. Escape cancels, Delete turns it off";
        if (help != null) this.drawCenteredString(this.textRenderer, help, this.width / 2, 16, 0xA0A0A0);
    }

    // ---- entries

    private interface Get { int get(); }
    private interface Set { void set(int v); }
    private interface GetB { boolean get(); }
    private interface SetB { void set(boolean v); }

    private abstract static class Entry {
        final String name;
        Entry(String name) { this.name = name; }
        abstract String label();
        String help() { return null; }
        void click(boolean back) {}
        boolean isKey() { return false; }
        void setKey(int key) {}
    }

    private void bool(String name, String help, GetB get, SetB set) {
        entries.add(new Entry(name) {
            String label() { return name + ": " + (get.get() ? "ON" : "OFF"); }
            String help() { return help; }
            void click(boolean back) { set.set(!get.get()); }
        });
    }

    private void num(String name, String help, int min, int max, Get get, Set set) {
        int[] values = new int[max - min + 1];
        for (int i = 0; i < values.length; i++) values[i] = min + i;
        list(name, help, values, get, set);
    }

    // click goes to the next value, shift + click to the one before
    private void list(String name, String help, int[] values, Get get, Set set) {
        entries.add(new Entry(name) {
            String label() { return name + ": " + get.get(); }
            String help() { return help + " (shift+click goes down)"; }
            void click(boolean back) {
                int cur = get.get(), at = -1;
                for (int i = 0; i < values.length; i++) if (values[i] <= cur) at = i;
                if (at < 0) at = 0;
                int n = values.length;
                set.set(values[back ? (at - 1 + n) % n : (values[at] == cur ? at + 1 : at + 1) % n]);
            }
        });
    }

    private void key(String name, Get get, Set set) {
        entries.add(new Entry(name) {
            String label() { int k = get.get(); return name + ": " + (k == Keyboard.KEY_NONE ? "NONE" : Keyboard.getKeyName(k)); }
            String help() { return "Click, then press the key"; }
            boolean isKey() { return true; }
            void setKey(int key) { set.set(key); }
        });
    }
}
