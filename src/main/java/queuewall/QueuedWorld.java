package queuewall;

import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.client.Minecraft;
import net.minecraft.client.SurvivalInteractionManager;
import net.minecraft.client.render.ProgressRenderer;
import net.minecraft.entity.mob.player.PlayerEntity;
import net.minecraft.world.World;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.storage.WorldStorage;
import net.minecraft.world.storage.WorldStorageSource;
import queuewall.compat.Versions;
import queuewall.mixin.MinecraftAccessor;

/**
 * One world in the queue. Made and filled on a generator thread, then only touched by the main thread.
 *
 * Generation is vanilla's own code: the World constructor, then Minecraft.prepareWorld (the "Building terrain"
 * step), run on the generator thread. MinecraftMixin points prepareWorld's reads of the client's world, player,
 * interaction manager and progress screen at this object while it runs there, and stops it before
 * "Simulating world for a bit", which happens when the world is played.
 */
public final class QueuedWorld {
    public enum State { WAITING, GENERATING, READY, FAILED }

    private static final ThreadLocal<QueuedWorld> GENERATING_HERE = new ThreadLocal<>();

    public final String folder;
    // same as the Create New World screen with an empty seed box: survival, structures on, not hardcore
    public final WorldSettings settings;

    public volatile State state = State.WAITING;
    public volatile boolean cancelled;
    public volatile boolean finished;
    final AtomicBoolean cleanedUp = new AtomicBoolean();

    public WorldStorage storage;
    public World world;
    /** What prepareWorld reads as the client's interaction manager: a survival one, like Create New World sets. */
    public SurvivalInteractionManager interactionManager;
    /** Records prepareWorld's progress for the wall instead of drawing it. */
    public Progress progress;
    /** The player vanilla would create for this world, used as the preview camera. Never added to the world. */
    public PlayerEntity camera;

    public boolean locked;
    public long readyAt;

    QueuedWorld(String folder) {
        this(folder, new Random().nextLong());
    }

    QueuedWorld(String folder, long seed) {
        this.folder = folder;
        this.settings = Versions.newWorldSettings(seed);
    }

    /** The world that Minecraft.prepareWorld is building on this thread, or null (always null on the main thread). */
    public static QueuedWorld generatingHere() {
        return GENERATING_HERE.get();
    }

    /** Runs on a generator thread. */
    void generate(Minecraft mc, WorldStorageSource storageSource, WorldQueue queue) {
        GENERATING_HERE.set(this);
        try {
            if (cancelled) return;
            state = State.GENERATING;
            interactionManager = new SurvivalInteractionManager(mc);
            progress = new Progress(mc, queue);
            storage = storageSource.get(folder, false);
            world = new World(storage, "New World", settings);
            // the same message setWorld passes for a new world
            ((MinecraftAccessor) mc).queuewall$prepareWorld("Generating level");
            if (cancelled) return;
            // what setWorld does after prepareWorld: create the player, push it up out of blocks, face it north
            camera = interactionManager.createPlayer(world);
            camera.resetPos();
            interactionManager.initPlayer(camera);
            readyAt = System.nanoTime();
            state = State.READY;
            QueueWall.log("generated " + folder);
        } catch (Cancelled c) {
            // reset while generating
        } catch (Throwable t) {
            System.out.println("[Queue Wall] world " + folder + " failed to generate");
            t.printStackTrace();
            state = State.FAILED;
        } finally {
            GENERATING_HERE.remove();
            finished = true;
            if (cancelled) queue.cleanUp(this);
        }
    }

    public boolean isReady() {
        return state == State.READY && !cancelled;
    }

    /**
     * The progress screen prepareWorld reports to on a generator thread. It keeps the title, stage and percentage
     * for the wall to show with the vanilla progress screen, and is where generation pauses or stops, since
     * prepareWorld reports progress before loading each chunk.
     */
    public final class Progress extends ProgressRenderer {
        private final WorldQueue queue;
        public volatile String title = "";
        public volatile String stage = "";
        public volatile int percentage = -1;

        Progress(Minecraft mc, WorldQueue queue) {
            super(mc);
            this.queue = queue;
        }

        @Override
        public void progressStart(String title) {
            this.title = title;
            this.stage = "";
            this.percentage = -1;
        }

        @Override
        public void progressStage(String stage) {
            this.stage = stage;
            this.percentage = -1;
        }

        @Override
        public void progressStagePercentage(int percentage) {
            queue.waitWhilePaused();
            if (cancelled) throw new Cancelled();
            this.percentage = percentage;
        }
    }

    private static final class Cancelled extends RuntimeException {
        Cancelled() {
            super(null, null, false, false);
        }
    }
}
