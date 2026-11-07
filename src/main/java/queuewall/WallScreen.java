package queuewall;

import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.render.Window;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

/** The wall. Slots are numbered left to right, top to bottom, and the locked row runs along the top. */
public class WallScreen extends Screen {
    // previews follow their world, so a world keeps its built preview when it moves to the locked row
    private static final Map<QueuedWorld, Preview> previews = new IdentityHashMap<>();
    private static final List<Preview> spare = new ArrayList<>();
    private static final List<LoadingSlot> loadingSlots = new ArrayList<>();
    private static int lockTexture = -2;
    private static int buildTurn;

    @Override
    public void init() {
        // what opening the title screen does: F3 goes off and the chat is cleared, so neither is left over the wall
        this.minecraft.options.debugEnabled = false;
        this.minecraft.gui.clearChat();
        QueueWall.startQueue(this.minecraft);
        QueueWall.reloadSettings();
        QueueWall.onWallOpened();
        QueueWall.queue.topUp();
    }

    @Override
    public void tick() {
        QueueWall.queue.topUp();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private Config cfg() {
        return QueueWall.cfg;
    }

    private QueuedWorld[] slots() {
        return QueueWall.queue.slots;
    }

    private List<QueuedWorld> locked() {
        return QueueWall.queue.locked;
    }

    /** Wall slots first, then the locked row's places. Null for a locked place with nothing in it. */
    private QueuedWorld worldAt(int place) {
        QueuedWorld[] slots = slots();
        if (place < slots.length) return slots[place];
        int i = place - slots.length;
        return i < locked().size() ? locked().get(i) : null;
    }

    private int places() {
        return slots().length + cfg().lockedRowSize();
    }

    /** Where a place is drawn, as {x0, y0, x1, y1} in a w x h screen with the origin top left. */
    private int[] rect(int place, int w, int h) {
        int slotCount = slots().length;
        int lockedCount = cfg().lockedRowSize();
        int top = lockedCount > 0 ? h / 4 : 0;
        if (place >= slotCount) {
            // the locked area is a grid with as many rows as keep its tiles closest to the screen's shape
            int rows = 1;
            double best = Double.MAX_VALUE;
            if (cfg().lockedRows > 0) {
                rows = Math.min(cfg().lockedRows, lockedCount);
                best = -1;
            } else if (cfg().lockedColumns > 0) {
                rows = (lockedCount + cfg().lockedColumns - 1) / cfg().lockedColumns;
                best = -1;
            }
            for (int r = 1; best >= 0 && r <= lockedCount; r++) {
                int c = (lockedCount + r - 1) / r;
                double off = Math.abs(Math.log(((double) w / c) / ((double) top / r) / ((double) w / h)));
                if (off < best) {
                    best = off;
                    rows = r;
                }
            }
            int cols = cfg().lockedColumns > 0 ? cfg().lockedColumns : (lockedCount + rows - 1) / rows;
            int i = place - slotCount;
            int col = i % cols;
            int row = i / cols;
            return new int[] {col * w / cols, row * top / rows, (col + 1) * w / cols, (row + 1) * top / rows};
        }
        int cols = cfg().columns;
        int rows = cfg().rows;
        int col = place % cols;
        int row = place / cols;
        return new int[] {col * w / cols, top + row * (h - top) / rows, (col + 1) * w / cols, top + (row + 1) * (h - top) / rows};
    }

    /** Place under the mouse, or -1. */
    private int hovered() {
        int dw = this.minecraft.width;
        int dh = this.minecraft.height;
        int mx = Mouse.getX();
        int my = dh - 1 - Mouse.getY();
        for (int p = 0; p < places(); p++) {
            int[] r = rect(p, dw, dh);
            if (mx >= r[0] && mx < r[2] && my >= r[1] && my < r[3]) return p;
        }
        return -1;
    }

    private Preview preview(QueuedWorld q) {
        Preview p = previews.get(q);
        if (p == null) {
            p = spare.isEmpty() ? new Preview(this.minecraft, cfg().previewDistance) : spare.remove(spare.size() - 1);
            p.show(q);
            previews.put(q, p);
        }
        return p;
    }

    private LoadingSlot loadingSlot(int place) {
        while (loadingSlots.size() <= place) loadingSlots.add(new LoadingSlot(this.minecraft));
        return loadingSlots.get(place);
    }

    /** Must be called before a world is played or reset: the preview lets go of it. */
    private static void release(QueuedWorld q) {
        Preview p = previews.remove(q);
        if (p != null) {
            p.show(null);
            spare.add(p);
        }
    }

    @Override
    public void render(int mouseX, int mouseY, float tickDelta) {
        int dw = this.minecraft.width;
        int dh = this.minecraft.height;
        int places = places();

        // previews of worlds that left the wall go back to the spare pile
        List<QueuedWorld> shown = new ArrayList<>();
        for (int p = 0; p < places; p++) {
            QueuedWorld q = worldAt(p);
            if (q != null && q.isReady()) shown.add(q);
        }
        for (QueuedWorld q : new ArrayList<>(previews.keySet())) if (!shown.contains(q)) release(q);

        // then they share the build time, starting from a different one each frame
        if (!shown.isEmpty()) {
            long start = System.nanoTime();
            long share = cfg().previewBuildMillis * 1_000_000L / shown.size();
            buildTurn = (buildTurn + 1) % shown.size();
            for (int n = 0; n < shown.size(); n++) {
                Preview p = preview(shown.get((buildTurn + n) % shown.size()));
                if (!p.isBuilt()) p.build(Math.max(System.nanoTime() + share, start + share * (n + 1)));
            }
        }

        // each slot draws its picture with vanilla's renderers at the slot's size, keeping a 1 pixel gap
        int gap = places > 1 ? 1 : 0;
        for (int p = 0; p < places; p++) {
            QueuedWorld q = worldAt(p);
            int[] r = rect(p, dw, dh);
            int w = r[2] - r[0] - gap;
            int h = r[3] - r[1] - gap;
            if (w <= 0 || h <= 0) continue;
            if (q != null && q.isReady()) preview(q).update(w, h);
            else if (p < slots().length) loadingSlot(p).update(q, w, h);
        }

        // then the whole wall at once, with the GUI's own projection
        GL11.glViewport(0, 0, dw, dh);
        GL11.glClearColor(0.0F, 0.0F, 0.0F, 1.0F);
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
        this.minecraft.gameRenderer.setupGuiState();
        float scale = (float) dw / this.width;
        for (int p = 0; p < places; p++) {
            QueuedWorld q = worldAt(p);
            int[] r = rect(p, dw, dh);
            float x0 = r[0] / scale, y0 = r[1] / scale, x1 = (r[2] - gap) / scale, y1 = (r[3] - gap) / scale;
            if (q != null && q.isReady()) preview(q).draw(x0, y0, x1, y1);
            else if (p < slots().length) loadingSlot(p).draw(x0, y0, x1, y1);
        }

        GL11.glDisable(GL11.GL_FOG);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_ALPHA_TEST);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);

        int slotCount = slots().length;
        for (int p = 0; p < places; p++) {
            int[] r = rect(p, this.width, this.height);
            int x0 = r[0], y0 = r[1], x1 = r[2];
            QueuedWorld q = worldAt(p);
            int lockSize = Math.max(p >= slotCount ? 10 : 16, (r[3] - y0) / 6);
            if (q == null) {
                if (p == slotCount && locked().isEmpty()) this.drawString(this.textRenderer, "Locked worlds go here (" + key(cfg().keyLock) + ")", x0 + 4, y0 + 4, 0x808080);
            } else if (!q.isReady()) {
                if (cfg().showLoadingPercent) drawPercentage(q, p, scale);
            } else if (!preview(q).isBuilt()) {
                int lx = x0 + (q.locked ? lockSize + 6 : 4);
                String label = "Loading preview...";
                if (lx + this.textRenderer.getWidth(label) > x1 - 2) label = "Loading...";
                if (lx + this.textRenderer.getWidth(label) <= x1 - 2) this.drawString(this.textRenderer, label, lx, y0 + 4, 0xFFFFFF);
            }
            if (q != null && q.locked) drawLock(x0 + 3, y0 + 3, lockSize);
        }

        if (cfg().showHints) {
            String hint = "Play " + key(cfg().keyPlay) + "  Reset " + key(cfg().keyReset) + "  Reset all " + key(cfg().keyResetAll)
                    + (cfg().focusResetPlays ? "  Play + reset others " : "  Focus reset ") + key(cfg().keyFocusReset) + "  Lock " + key(cfg().keyLock) + (cfg().clickToLock ? " / Shift+click" : "") + "  Title screen Esc";
            int w = this.textRenderer.getWidth(hint);
            this.fillGradient(this.width / 2 - w / 2 - 3, this.height - 12, this.width / 2 + w / 2 + 3, this.height, 0x90000000, 0x90000000);
            this.drawString(this.textRenderer, hint, this.width / 2 - w / 2, this.height - 10, 0xFFFFFF);
        }
    }

    // the percentage under a loading slot's progress bar, so the recording shows each world's progress legibly
    // (A.9.3.a). It goes just below where the progress screen put the bar at the slot's size
    private void drawPercentage(QueuedWorld q, int place, float scale) {
        QueuedWorld.Progress progress = q.progress;
        if (progress == null || progress.percentage < 0) return;
        int[] px = rect(place, this.minecraft.width, this.minecraft.height);
        Window slot = new Window(this.minecraft.options, px[2] - px[0], px[3] - px[1]);
        float barBottom = (slot.getHeight() / 2 + 16 + 2) * slot.scale;
        String pct = progress.percentage + "%";
        int cx = (int) ((px[0] + px[2]) / 2 / scale);
        int y = (int) ((px[1] + barBottom) / scale) + 4;
        this.drawCenteredString(this.textRenderer, pct, cx, y, 0xFFFFFF);
    }

    private static String key(int k) {
        return k == Keyboard.KEY_NONE ? "-" : Keyboard.getKeyName(k);
    }

    private void drawLock(int x, int y, int size) {
        if (lockTexture == -2) {
            lockTexture = -1;
            try (InputStream in = WallScreen.class.getResourceAsStream("/assets/queuewall/lock.png")) {
                if (in != null) {
                    BufferedImage img = ImageIO.read(in);
                    lockTexture = this.minecraft.textureManager.load(img);
                }
            } catch (Exception e) {
                System.out.println("[Queue Wall] couldn't load lock.png: " + e);
            }
        }
        if (lockTexture < 0) {
            this.drawString(this.textRenderer, "LOCKED", x, y, 0xFFFF55);
            return;
        }
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, lockTexture);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glTexCoord2f(0, 0);
        GL11.glVertex3f(x, y, 0);
        GL11.glTexCoord2f(0, 1);
        GL11.glVertex3f(x, y + size, 0);
        GL11.glTexCoord2f(1, 1);
        GL11.glVertex3f(x + size, y + size, 0);
        GL11.glTexCoord2f(1, 0);
        GL11.glVertex3f(x + size, y, 0);
        GL11.glEnd();
        GL11.glDisable(GL11.GL_BLEND);
    }

    @Override
    protected void keyPressed(char chr, int key) {
        Config cfg = cfg();
        if (QueueWall.isResetKeyPressThatQuit(key)) return;
        if (key == Keyboard.KEY_ESCAPE) {
            this.minecraft.openScreen(new TitleScreen());
            return;
        }
        if (key == cfg.keyResetAll) {
            resetWall(null);
            return;
        }
        // a key set to both focus reset and play does focus reset
        boolean focusReset = key == cfg.keyFocusReset;
        boolean playKey = !focusReset && key == cfg.keyPlay;
        if (playKey && cfg.lockedRow && cfg.playLockedFirst) {
            // the first locked world that's ready, before anything on the wall
            for (QueuedWorld q : locked()) {
                if (q.isReady()) {
                    play(q);
                    return;
                }
            }
        }
        int place = hovered();
        QueuedWorld q = place < 0 ? null : worldAt(place);
        if (q == null) return;
        boolean inRow = place >= slots().length;
        if (focusReset && !cfg.focusResetPlays) {
            // just this world, like the reset key
            if (inRow || !q.locked) reset(q);
        } else if (focusReset) {
            if (q.isReady()) {
                resetWall(q);
                play(q);
            }
        } else if (playKey) {
            play(q);
        } else if (key == cfg.keyReset) {
            // a locked world only resets from the locked row, so it can't be reset by accident on the wall
            if (inRow || !q.locked) reset(q);
        } else if (key == cfg.keyLock) {
            toggleLock(q);
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) {
        int place = hovered();
        QueuedWorld q = place < 0 ? null : worldAt(place);
        if (q == null) return;
        if (button == 0 && (Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT))) {
            if (cfg().clickToLock) toggleLock(q);
        } else if (button == 0) {
            if (cfg().clickToPlay) play(q);
        } else if (button == 1 && cfg().clickToReset) {
            if (place >= slots().length || !q.locked) reset(q);
        }
    }

    private void play(QueuedWorld q) {
        playWorld(this.minecraft, q);
    }

    static void playWorld(Minecraft mc, QueuedWorld q) {
        if (!q.isReady()) return;
        release(q);
        QueueWall.play(mc, q);
    }

    private void reset(QueuedWorld q) {
        // a world that's still loading keeps generating unless resetLoadingWorlds is on
        if (!q.isReady() && !cfg().resetLoadingWorlds) return;
        release(q);
        QueueWall.reset(q);
    }

    // resets every unlocked world on the wall except keep. the locked row is left alone
    private void resetWall(QueuedWorld keep) {
        for (QueuedWorld q : slots().clone()) {
            if (q != null && q != keep && !q.locked) reset(q);
        }
    }

    private void toggleLock(QueuedWorld q) {
        WorldQueue queue = QueueWall.queue;
        if (!cfg().lockedRow) {
            q.locked = !q.locked;
        } else if (q.locked) {
            queue.unlock(q);
        } else if (!queue.lock(q)) {
            return; // the row is full
        }
        this.minecraft.soundEngine.play("random.click", 1.0F, 1.0F);
    }
}
