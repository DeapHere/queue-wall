package queuewall;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import net.minecraft.client.Minecraft;
import net.minecraft.unmapped.C_0349999;
import net.minecraft.unmapped.C_3020744;
import net.minecraft.unmapped.C_5353187;
import net.minecraft.unmapped.C_7077203;
import net.minecraft.unmapped.C_8938952;
import org.lwjgl.input.Keyboard;

/** Glue between the mixins, the queue and the wall screen. Main thread only. */
public final class QueueWall {
    /** Kept the same as fabric.mod.json (build.sh checks). */
    public static final String VERSION = "0.3.4";
    public static Config cfg;
    static WorldQueue queue;
    /** Set only while startWorld runs for a queued world, read by MinecraftMixin. */
    public static QueuedWorld pending;
    /** True while a wall preview is being built or drawn, read by WorldRendererMixin. */
    public static boolean drawingPreview;

    private static boolean startupDone;
    private static boolean returnToWall;
    /** The world being played came from the locked area, so leaving it goes into the next locked world. */
    private static boolean playingLocked;
    private static boolean playNextLocked;
    private static boolean resetKeyWasDown;
    private static int resets = -1;

    public static void init(Minecraft mc) {
        if (cfg == null) cfg = Config.load(new File(Minecraft.m_7340082(), "config"));
    }

    /** Generation starts the first time the wall opens, not at the title screen. */
    static void startQueue(Minecraft mc) {
        init(mc);
        if (queue == null) queue = new WorldQueue(mc, cfg);
    }

    public static void openWall(Minecraft mc) {
        init(mc);
        mc.m_6408915(new WallScreen());
    }

    static void onWallOpened() {
        returnToWall = false;
    }

    public static C_3020744 redirectScreen(Minecraft mc, C_3020744 screen) {
        if (screen instanceof C_8938952) {
            if (!startupDone) {
                startupDone = true;
                init(mc);
                if (cfg.openWallOnStartup) return new WallScreen();
            } else if (returnToWall) {
                returnToWall = false;
                if (cfg.returnToWallOnQuit) {
                    // the wall plays the next locked world on the next tick, if there is one
                    playNextLocked = playingLocked && cfg.chainLockedWorlds;
                    return new WallScreen();
                }
            }
        }
        return screen;
    }

    public static void onTick(Minecraft mc) {
        if (cfg == null) return;
        boolean inWorld = mc.f_5854988 != null;
        if (queue != null) {
            queue.setPaused(inWorld && !cfg.generateWhilePlaying);
            queue.topUp();
        }

        boolean down = inWorld && (isDown(cfg.keyResetWhilePlaying) || isDown(cfg.keyResetWhilePlaying2));
        if (down && !resetKeyWasDown && (mc.f_0723335 == null || mc.f_0723335 instanceof C_7077203) && !mc.m_2812472()) {
            // same as Save and Quit to Title, then straight to the wall. this works in any world, so the first
            // world of a session can be made through the normal screens and the wall opened from there
            log("reset key pressed in a world");
            mc.f_8029336.m_5854284(C_0349999.f_8680599, 1);
            mc.m_8675683(null);
            countReset();
            returnToWall = false;
            if (!(playingLocked && cfg.chainLockedWorlds && playLocked(mc))) openWall(mc);
        }
        resetKeyWasDown = down;
        if (playNextLocked && mc.f_0723335 instanceof WallScreen) {
            playNextLocked = false;
            playLocked(mc);
        }
    }

    /** Wall events go to the game log, so verifiers can match the world played to the video and world files. */
    static void log(String event) {
        System.out.println("[Queue Wall] " + event);
    }

    private static boolean isDown(int key) {
        return key != Keyboard.KEY_NONE && Keyboard.isKeyDown(key);
    }

    /** Hands a generated world to the game through the normal startWorld. */
    /** True while in a world that was played from the wall. */
    public static boolean isPlayingFromWall() {
        return returnToWall;
    }

    /** Stop Resets & Quit: the next screen is the title screen, not the wall or the next locked world. */
    public static void stopResets() {
        log("stopped resets");
        returnToWall = false;
        playingLocked = false;
        playNextLocked = false;
    }

    /** Plays the first ready locked world. False if there isn't one. */
    private static boolean playLocked(Minecraft mc) {
        for (QueuedWorld q : queue.locked) {
            if (q.isReady()) {
                WallScreen.playWorld(mc, q);
                return true;
            }
        }
        return false;
    }

    static void play(Minecraft mc, QueuedWorld q) {
        log("played " + q.folder + (q.locked ? " (locked)" : ""));
        playingLocked = q.locked;
        queue.take(q);
        pending = q;
        try {
            mc.f_1273243 = new C_5353187(mc);
            mc.m_5272083(q.folder, "New World", q.settings);
        } finally {
            pending = null;
        }
        mc.m_6408915(null);
        returnToWall = true;
    }

    static void reset(QueuedWorld q) {
        queue.reset(q);
        countReset();
    }

    static void countReset() {
        if (!cfg.resetCounter) return;
        File f = new File(Minecraft.m_7340082(), "resets.txt");
        if (resets < 0) {
            resets = 0;
            try {
                if (f.isFile()) {
                    String s = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8).replaceAll("[^0-9]", "");
                    if (!s.isEmpty()) resets = Integer.parseInt(s);
                }
            } catch (Exception ignored) {
            }
        }
        resets++;
        try {
            Files.write(f.toPath(), (cfg.resetCounterText + resets).getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            System.out.println("[Queue Wall] couldn't write resets.txt: " + e);
        }
    }

    private QueueWall() {}
}
