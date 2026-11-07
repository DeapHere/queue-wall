package queuewall;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.SurvivalInteractionManager;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import org.lwjgl.input.Keyboard;
import queuewall.mixin.ScreenInvoker;

/** Glue between the mixins, the queue and the wall screen. Main thread only. */
public final class QueueWall {
    /** The version in fabric.mod.json, which the build fills in from gradle.properties. */
    public static final String VERSION = FabricLoader.getInstance().getModContainer("queuewall")
            .map(mod -> mod.getMetadata().getVersion().getFriendlyString()).orElse("unknown");
    /** Vanilla's id for Save and Quit to Title on the Game menu. */
    private static final int SAVE_AND_QUIT = 1;

    public static Config cfg;
    static WorldQueue queue;
    /** Set only while startGame runs for a queued world, read by MinecraftMixin. */
    public static QueuedWorld pending;

    private static boolean startupDone;
    /** In a world played from the wall: quitting to the title screen goes to the wall instead. */
    private static boolean returnToWall;
    /** The reset key quit the world: go to the wall whatever returnToWallOnQuit says. */
    private static boolean resetKeyQuit;
    /** The world being played came from the locked area, so leaving it goes into the next locked world. */
    private static boolean playingLocked;
    private static boolean playNextLocked;
    private static boolean resetKeyWasDown;
    private static int resets = -1;

    public static void init(Minecraft mc) {
        if (cfg == null) cfg = Config.load(new File(Minecraft.getWorkingDirectory(), "config"));
    }

    /** Generation starts the first time the wall opens, not at the title screen. */
    static void startQueue(Minecraft mc) {
        init(mc);
        if (queue == null) queue = new WorldQueue(mc, cfg);
    }

    public static void openWall(Minecraft mc) {
        init(mc);
        mc.openScreen(new WallScreen());
    }

    /** Picks up edits to queuewall.properties made while the game is open. */
    static void reloadSettings() {
        if (cfg != null && cfg.reloadIfChanged() && queue != null) queue.applySettings();
    }

    /** After the settings screen wrote the file. */
    static void applySettingsScreen() {
        if (cfg != null && cfg.reload(true) && queue != null) queue.applySettings();
    }

    static void onWallOpened() {
        returnToWall = false;
    }

    /** Every screen the game opens passes through here. Only the title screen is ever replaced, by the wall. */
    public static Screen redirectScreen(Minecraft mc, Screen screen) {
        if (!(screen instanceof TitleScreen)) return screen;
        if (!startupDone) {
            startupDone = true;
            init(mc);
            return cfg.openWallOnStartup ? new WallScreen() : screen;
        }
        boolean toWall = resetKeyQuit || (returnToWall && cfg.returnToWallOnQuit);
        resetKeyQuit = false;
        returnToWall = false;
        if (!toWall) return screen;
        // the wall plays the next locked world on its first tick, if there is one
        playNextLocked = playingLocked && cfg.chainLockedWorlds;
        return new WallScreen();
    }

    public static void onTick(Minecraft mc) {
        if (cfg == null) return;
        boolean inWorld = mc.world != null;
        if (queue != null) {
            queue.setPaused(inWorld && !cfg.generateWhilePlaying);
            queue.topUp();
        }

        boolean down = inWorld && (isDown(cfg.keyResetWhilePlaying) || isDown(cfg.keyResetWhilePlaying2));
        if (down && !resetKeyWasDown && (mc.screen == null || mc.screen instanceof GameMenuScreen) && !mc.isMultiplayer()) {
            // the Game menu's own Save and Quit to Title, then the wall. This works in any world, so the first
            // world of a session can be made through the normal screens and the wall opened from there
            log("reset key pressed in a world");
            countReset();
            resetKeyQuit = true;
            GameMenuScreen menu = new GameMenuScreen();
            menu.init(mc, mc.width, mc.height);
            ((ScreenInvoker) menu).queuewall$buttonClicked(new ButtonWidget(SAVE_AND_QUIT, 0, 0, ""));
        }
        resetKeyWasDown = down;
        if (playNextLocked && mc.screen instanceof WallScreen) {
            playNextLocked = false;
            playLocked(mc);
        }
    }

    /** Wall events go to the game log, so verifiers can match the world played to the video and world files. */
    static void log(String event) {
        System.out.println("[Queue Wall] " + event);
    }

    /**
     * True for the key press that just quit a world with the reset key. The game hands that same press to the wall
     * once it opens (later in the same tick), where it would otherwise also reset the slot under the cursor.
     */
    static boolean isResetKeyPressThatQuit(int key) {
        return resetKeyWasDown && key != Keyboard.KEY_NONE
                && (key == cfg.keyResetWhilePlaying || key == cfg.keyResetWhilePlaying2);
    }

    private static boolean isDown(int key) {
        return key != Keyboard.KEY_NONE && Keyboard.isKeyDown(key);
    }

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

    /** Starts a queued world the way Create New World does, through vanilla's startGame. */
    static void play(Minecraft mc, QueuedWorld q) {
        log("played " + q.folder + (q.locked ? " (locked)" : ""));
        playingLocked = q.locked;
        queue.take(q);
        pending = q;
        try {
            mc.interactionManager = new SurvivalInteractionManager(mc);
            mc.startGame(q.folder, "New World", q.settings);
        } finally {
            pending = null;
        }
        mc.openScreen(null);
        returnToWall = true;
    }

    static void reset(QueuedWorld q) {
        queue.reset(q);
        countReset();
    }

    static void countReset() {
        if (!cfg.resetCounter) return;
        File f = new File(Minecraft.getWorkingDirectory(), "resets.txt");
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
