package queuewall;

import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.unmapped.C_3020744;
import net.minecraft.unmapped.C_5786166;
import net.minecraft.unmapped.C_8938952;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

/** The wall. Slots are numbered left to right, top to bottom, and the locked row runs along the top. */
public class WallScreen extends C_3020744 {
    // previews follow their world, so a world keeps its built preview when it moves to the locked row
    private static final Map<QueuedWorld, PreviewRenderer> previews = new IdentityHashMap<>();
    private static final List<PreviewRenderer> spare = new ArrayList<>();
    private static int lockTexture = -2;
    private static int buildTurn;

    @Override
    public void m_3593494() {
        QueueWall.startQueue(this.f_7153641);
        QueueWall.onWallOpened();
        QueueWall.queue.topUp();
    }

    @Override
    public void m_2450377() {
        QueueWall.queue.topUp();
    }

    @Override
    public boolean m_4615225() {
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
        int top = lockedCount > 0 ? h * cfg().lockedRowHeight / 100 : 0;
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
        int dw = this.f_7153641.f_0545414;
        int dh = this.f_7153641.f_5990000;
        int mx = Mouse.getX();
        int my = dh - 1 - Mouse.getY();
        for (int p = 0; p < places(); p++) {
            int[] r = rect(p, dw, dh);
            if (mx >= r[0] && mx < r[2] && my >= r[1] && my < r[3]) return p;
        }
        return -1;
    }

    private PreviewRenderer preview(QueuedWorld q) {
        PreviewRenderer p = previews.get(q);
        if (p == null) {
            p = spare.isEmpty() ? new PreviewRenderer(this.f_7153641, cfg().previewDistance) : spare.remove(spare.size() - 1);
            p.show(q);
            previews.put(q, p);
        }
        return p;
    }

    /** Must be called before a world is played or reset: the preview lets go of it. */
    private static void release(QueuedWorld q) {
        PreviewRenderer p = previews.remove(q);
        if (p != null) {
            p.show(null);
            spare.add(p);
        }
    }

    @Override
    public void m_7261014(int mouseX, int mouseY, float partialTicks) {
        int dw = this.f_7153641.f_0545414;
        int dh = this.f_7153641.f_5990000;
        int places = places();

        GL11.glClearColor(0.0F, 0.0F, 0.0F, 1.0F);
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);

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
                PreviewRenderer p = preview(shown.get((buildTurn + n) % shown.size()));
                if (!p.isBuilt()) p.build(Math.max(System.nanoTime() + share, start + share * (n + 1)));
            }
        }

        // 1 pixel black line between places
        int gap = places > 1 ? 1 : 0;
        for (int p = 0; p < places; p++) {
            QueuedWorld q = worldAt(p);
            if (q == null || !q.isReady()) continue;
            int[] r = rect(p, dw, dh);
            preview(q).render(r[0], dh - r[3] + gap, r[2] - r[0] - gap, r[3] - r[1] - gap);
        }

        // back to the GUI's own projection for the overlays
        GL11.glViewport(0, 0, dw, dh);
        GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT);
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glLoadIdentity();
        GL11.glOrtho(0.0, this.f_5465691, this.f_3080061, 0.0, 1000.0, 3000.0);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glLoadIdentity();
        GL11.glTranslatef(0.0F, 0.0F, -2000.0F);
        GL11.glDisable(GL11.GL_FOG);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_ALPHA_TEST);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);

        int slotCount = slots().length;
        for (int p = 0; p < places; p++) {
            int[] r = rect(p, this.f_5465691, this.f_3080061);
            int x0 = r[0], y0 = r[1], x1 = r[2], y1 = r[3];
            QueuedWorld q = worldAt(p);
            int lockSize = Math.max(p >= slotCount ? 10 : 16, (y1 - y0) / 6);
            if (q == null) {
                if (p < slotCount) drawLoadingScreen(x0, y0, x1, y1, "Generating level", "", -1);
                else if (p == slotCount && locked().isEmpty()) this.f_2020658.m_1950885("Locked worlds go here (" + key(cfg().keyLock) + ")", x0 + 4, y0 + 4, 0x808080);
            } else if (!q.isReady()) {
                // what 1.0 shows while a new world loads
                boolean started = q.state == QueuedWorld.State.GENERATING;
                drawLoadingScreen(x0, y0, x1, y1, "Generating level", started ? "Building terrain" : "", started ? q.progress : -1);
            } else if (!preview(q).isBuilt()) {
                int lx = x0 + (q.locked ? lockSize + 6 : 4);
                String label = "Loading preview...";
                if (lx + this.f_2020658.m_0040387(label) > x1 - 2) label = "Loading...";
                if (lx + this.f_2020658.m_0040387(label) <= x1 - 2) this.f_2020658.m_1950885(label, lx, y0 + 4, 0xFFFFFF);
            }
            if (q != null && q.locked) drawLock(x0 + 3, y0 + 3, lockSize);
        }

        if (cfg().showHints) {
            String hint = "Play " + key(cfg().keyPlay) + "  Reset " + key(cfg().keyReset) + "  Reset all " + key(cfg().keyResetAll)
                    + (cfg().focusResetPlays ? "  Play + reset others " : "  Focus reset ") + key(cfg().keyFocusReset) + "  Lock " + key(cfg().keyLock) + "  Title screen Esc";
            int w = this.f_2020658.m_0040387(hint);
            this.m_9889399(this.f_5465691 / 2 - w / 2 - 3, this.f_3080061 - 12, this.f_5465691 / 2 + w / 2 + 3, this.f_3080061, 0x90000000, 0x90000000);
            this.f_2020658.m_1950885(hint, this.f_5465691 / 2 - w / 2, this.f_3080061 - 10, 0xFFFFFF);
        }
    }

    // the vanilla loading screen (LoadingScreenRenderer.setLoadingProgress), drawn inside one slot
    private void drawLoadingScreen(int x0, int y0, int x1, int y1, String title, String message, int progress) {
        C_5786166 t = C_5786166.f_7436514;
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, this.f_7153641.f_9413506.m_9688911("/gui/background.png"));
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        float tile = 32.0F;
        t.m_5815628();
        t.m_8270370(0x404040);
        t.m_1165938(x0, y1, 0.0, x0 / tile, y1 / tile);
        t.m_1165938(x1, y1, 0.0, x1 / tile, y1 / tile);
        t.m_1165938(x1, y0, 0.0, x1 / tile, y0 / tile);
        t.m_1165938(x0, y0, 0.0, x0 / tile, y0 / tile);
        t.m_7459300();
        int cx = (x0 + x1) / 2;
        int cy = (y0 + y1) / 2;
        if (progress >= 0) {
            int left = cx - 50;
            int top = cy + 16;
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            t.m_5815628();
            t.m_8270370(0x808080);
            t.m_4415074(left, top, 0.0);
            t.m_4415074(left, top + 2, 0.0);
            t.m_4415074(left + 100, top + 2, 0.0);
            t.m_4415074(left + 100, top, 0.0);
            t.m_8270370(0x80FF80);
            t.m_4415074(left, top, 0.0);
            t.m_4415074(left, top + 2, 0.0);
            t.m_4415074(left + progress, top + 2, 0.0);
            t.m_4415074(left + progress, top, 0.0);
            t.m_7459300();
            GL11.glEnable(GL11.GL_TEXTURE_2D);
        }
        this.f_2020658.m_1950885(title, cx - this.f_2020658.m_0040387(title) / 2, cy - 4 - 16, 0xFFFFFF);
        this.f_2020658.m_1950885(message, cx - this.f_2020658.m_0040387(message) / 2, cy - 4 + 8, 0xFFFFFF);
        // the percentage too, so the recording shows each world's progress legibly (A.9.3.a)
        if (progress >= 0 && cfg().showLoadingPercent) {
            String pct = progress + "%";
            this.f_2020658.m_1950885(pct, cx - this.f_2020658.m_0040387(pct) / 2, cy + 22, 0xFFFFFF);
        }
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
                    lockTexture = this.f_7153641.f_9413506.m_0440500(img);
                }
            } catch (Exception e) {
                System.out.println("[Queue Wall] couldn't load lock.png: " + e);
            }
        }
        if (lockTexture < 0) {
            this.f_2020658.m_1950885("LOCKED", x, y, 0xFFFF55);
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
    protected void m_6992336(char c, int key) {
        Config cfg = cfg();
        if (key == Keyboard.KEY_ESCAPE) {
            this.f_7153641.m_6408915(new C_8938952());
            return;
        }
        if (key == cfg.keyResetAll) {
            resetWall(null);
            return;
        }
        // a key set to both focus reset and play does focus reset
        boolean focusReset = key == cfg.keyFocusReset;
        boolean playKey = !focusReset && (key == cfg.keyPlay || key == cfg.keyPlay2);
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
    protected void m_7362766(int mouseX, int mouseY, int button) {
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
        playWorld(this.f_7153641, q);
    }

    static void playWorld(Minecraft mc, QueuedWorld q) {
        if (!q.isReady()) return;
        release(q);
        QueueWall.play(mc, q);
    }

    private void reset(QueuedWorld q) {
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
        this.f_7153641.f_3843367.m_7271958("random.click", 1.0F, 1.0F);
    }
}
