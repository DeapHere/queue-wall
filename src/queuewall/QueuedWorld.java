package queuewall;

import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.unmapped.C_2670477;
import net.minecraft.unmapped.C_3674802;
import net.minecraft.unmapped.C_5478016;
import net.minecraft.unmapped.C_5528202;
import net.minecraft.unmapped.C_5553933;
import net.minecraft.unmapped.C_8048208;

/** One world in the queue. Made and filled on a generator thread, then only touched by the main thread. */
public final class QueuedWorld {
    public enum State { WAITING, GENERATING, READY, FAILED }

    public final String folder;
    // same as the Create New World screen with an empty seed box: survival, structures on, not hardcore
    public final C_2670477 settings = new C_2670477(new Random().nextLong(), 0, true, false);

    public volatile State state = State.WAITING;
    public volatile int progress;
    public volatile boolean cancelled;
    public volatile boolean finished;
    final AtomicBoolean cleanedUp = new AtomicBoolean();

    public C_5528202 saveHandler;
    public C_5553933 world;
    public double camX, camY, camZ;

    public boolean locked;
    public long readyAt;

    QueuedWorld(String folder) {
        this.folder = folder;
    }

    /** Runs on a generator thread. Does what startWorld and the "Building terrain" step do in vanilla, in the same order. */
    void generate(C_5478016 saveLoader, WorldQueue queue) {
        try {
            if (cancelled) return;
            state = State.GENERATING;
            saveHandler = saveLoader.m_0190283(folder, false);
            C_5553933 w = new C_5553933(saveHandler, "New World", settings);
            world = w;
            C_3674802 spawn = w.m_9708082();
            int radius = 128;
            int total = (radius * 2 / 16 + 1) * (radius * 2 / 16 + 1);
            int done = 0;
            for (int dx = -radius; dx <= radius; dx += 16) {
                for (int dz = -radius; dz <= radius; dz += 16) {
                    queue.waitWhilePaused();
                    if (cancelled) return;
                    w.m_9893076(spawn.f_7795707 + dx, 64, spawn.f_9469462 + dz);
                    while (w.m_5077137()) {
                    }
                    progress = ++done * 100 / total;
                }
            }
            placeCamera(w, spawn);
            readyAt = System.nanoTime();
            state = State.READY;
            QueueWall.log("generated " + folder);
        } catch (Throwable t) {
            System.out.println("[Queue Wall] world " + folder + " failed to generate");
            t.printStackTrace();
            state = State.FAILED;
        } finally {
            finished = true;
            if (cancelled) queue.cleanUp(this);
        }
    }

    // where a new player ends up: spawn x/z + 0.5, pushed up out of blocks, then fallen to the ground
    private void placeCamera(C_5553933 w, C_3674802 spawn) {
        int x = spawn.f_7795707;
        int z = spawn.f_9469462;
        int y = spawn.f_1706448 + 1;
        while (y < 127 && (solid(w, x, y, z) || solid(w, x, y + 1, z))) y++;
        while (y > 1 && !solid(w, x, y - 1, z) && !w.m_5294276(x, y - 1, z).m_8583295()) y--;
        camX = x + 0.5;
        camY = y + 1.62;
        camZ = z + 0.5;
    }

    private static boolean solid(C_5553933 w, int x, int y, int z) {
        C_8048208 m = w.m_5294276(x, y, z);
        return m.m_9427296();
    }

    public boolean isReady() {
        return state == State.READY && !cancelled;
    }
}
