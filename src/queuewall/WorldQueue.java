package queuewall;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;
import net.minecraft.unmapped.C_5478016;

/** Owns the queued worlds. Everything except generate() and cleanUp() runs on the main thread. */
public final class WorldQueue {
    private static final Pattern NAME = Pattern.compile("New World (\\d+)");

    private final Config cfg;
    private final C_5478016 saveLoader;
    private final File savesDir;
    private final ExecutorService generators;
    private final ExecutorService io;

    /** Every world we hold, oldest first. */
    final List<QueuedWorld> worlds = new ArrayList<>();
    /** What each wall slot shows, null for empty. */
    final QueuedWorld[] slots;
    /** Locked worlds in the row along the top, oldest lock first. They stay in worlds. */
    final List<QueuedWorld> locked = new ArrayList<>();

    /** Folders that must never be deleted: queued worlds and the one being played. */
    private final Set<String> liveFolders = ConcurrentHashMap.newKeySet();
    private int nextNumber;
    /** Every world that was played, from config/queuewall-played.txt. Lines are "folder<tab>highest world number at the time". */
    private final File playedFile;
    private final Set<String> played = ConcurrentHashMap.newKeySet();
    private final List<int[]> recentRuns = new CopyOnWriteArrayList<>();
    private static final int PROTECTED_RUNS = 30;
    /** Worlds up to this number were there before the played list existed, and are never deleted. */
    private volatile int keepUpTo = -1;
    private final Object pauseLock = new Object();
    private volatile boolean paused;

    WorldQueue(Minecraft mc, Config cfg) {
        this.cfg = cfg;
        this.saveLoader = mc.m_5293485();
        this.savesDir = new File(Minecraft.m_7340082(), "saves");
        this.slots = new QueuedWorld[cfg.rows * cfg.columns];
        AtomicInteger n = new AtomicInteger();
        this.generators = Executors.newFixedThreadPool(cfg.generatorThreads, r -> new GenThread(r, "Queue Wall generator " + n.incrementAndGet()));
        this.io = Executors.newSingleThreadExecutor(r -> new GenThread(r, "Queue Wall cleanup"));
        this.nextNumber = highestNumber() + 1;
        this.playedFile = new File(new File(Minecraft.m_7340082(), "config"), "queuewall-played.txt");
        loadPlayed();
        io.execute(this::deleteLeftovers);
    }

    // queued worlds from the last session that were never played or reset: no level.dat and not in the played list.
    // only the folder names and whether level.dat exists are looked at, nothing inside a world is read
    private void deleteLeftovers() {
        String[] names = savesDir.list();
        if (names == null) return;
        for (String s : names) {
            File dir = new File(savesDir, s);
            Matcher m = NAME.matcher(s);
            if (!m.matches() || parseInt(m.group(1)) <= keepUpTo || liveFolders.contains(s) || played.contains(s)
                    || new File(dir, "level.dat").exists()) continue;
            deleteDir(dir);
        }
    }

    private void loadPlayed() {
        if (!playedFile.isFile()) {
            // first start with this file: nothing that's already in saves is ever deleted, since the old versions
            // didn't keep a list of played worlds
            int highest = nextNumber - 1;
            try {
                playedFile.getParentFile().mkdirs();
                Files.write(playedFile.toPath(), ("#before\t" + highest + "\n").getBytes(StandardCharsets.UTF_8));
            } catch (IOException e) {
                System.out.println("[Queue Wall] couldn't write " + playedFile + ": " + e);
            }
            keepUpTo = highest;
            return;
        }
        try {
            for (String line : Files.readAllLines(playedFile.toPath(), StandardCharsets.UTF_8)) {
                String[] parts = line.split("\t");
                if (parts[0].isEmpty()) continue;
                if (parts[0].equals("#before")) {
                    keepUpTo = parts.length > 1 ? parseInt(parts[1]) : Integer.MAX_VALUE;
                    continue;
                }
                addPlayed(parts[0], parts.length > 1 ? parseInt(parts[1]) : -1);
            }
        } catch (IOException e) {
            System.out.println("[Queue Wall] couldn't read " + playedFile + ": " + e);
        }
    }

    private void addPlayed(String folder, int highest) {
        played.add(folder);
        Matcher m = NAME.matcher(folder);
        if (m.matches() && highest >= 0) {
            recentRuns.add(new int[] {parseInt(m.group(1)), highest});
            while (recentRuns.size() > PROTECTED_RUNS) recentRuns.remove(0);
        }
    }

    private static int parseInt(String s) {
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    // the files a run's verifiers can ask for (A.7.8 and A.7.8.a): the 5 worlds before it and every world made before you
    // left it. those stay for the last 30 played worlds, whatever keepNewestWorlds says
    private boolean protectedForRun(int number) {
        if (number <= keepUpTo) return true;
        for (int[] run : recentRuns) {
            if (number >= run[0] - 5 && number <= run[1]) return true;
        }
        return false;
    }

    private int highestNumber() {
        int max = 0;
        String[] names = savesDir.list();
        if (names != null) {
            for (String s : names) {
                Matcher m = NAME.matcher(s);
                if (m.matches()) {
                    try {
                        max = Math.max(max, Integer.parseInt(m.group(1)));
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
        }
        return max;
    }

    private String nextFolder() {
        String name;
        do {
            name = "New World " + nextNumber++;
        } while (new File(savesDir, name).exists());
        return name;
    }

    /** Keeps slots + backgroundWorlds worlds queued. */
    void topUp() {
        worlds.removeIf(q -> {
            if (q.state == QueuedWorld.State.FAILED) {
                removeFromSlots(q);
                q.cancelled = true;
                io.execute(() -> cleanUp(q));
                return true;
            }
            return false;
        });
        int target = slots.length + cfg.backgroundWorlds + locked.size();
        while (worlds.size() < target) {
            QueuedWorld q = new QueuedWorld(nextFolder());
            liveFolders.add(q.folder);
            worlds.add(q);
            QueueWall.log("queued " + q.folder);
            generators.execute(() -> q.generate(saveLoader, this));
        }
        fillSlots();
    }

    /** Fills empty slots with the oldest ready world, or failing that the oldest one still generating. */
    void fillSlots() {
        for (int i = 0; i < slots.length; i++) {
            if (slots[i] != null) continue;
            QueuedWorld pick = null;
            for (QueuedWorld q : worlds) {
                if (inSlot(q) || q.cancelled || locked.contains(q)) continue;
                if (q.isReady()) {
                    pick = q;
                    break;
                }
                if (pick == null) pick = q;
            }
            slots[i] = pick;
        }
    }

    private boolean inSlot(QueuedWorld q) {
        for (QueuedWorld s : slots) if (s == q) return true;
        return false;
    }

    private void removeFromSlots(QueuedWorld q) {
        for (int i = 0; i < slots.length; i++) if (slots[i] == q) slots[i] = null;
        locked.remove(q);
    }

    /** Moves a world from the wall to the locked row. False if the row is full. */
    boolean lock(QueuedWorld q) {
        if (locked.contains(q)) return true;
        if (locked.size() >= cfg.lockedRowSize()) return false;
        removeFromSlots(q);
        q.locked = true;
        QueueWall.log("locked " + q.folder);
        locked.add(q);
        topUp();
        return true;
    }

    /** Takes a world out of the locked row. It goes back on the wall in the next free slot. */
    void unlock(QueuedWorld q) {
        QueueWall.log("unlocked " + q.folder);
        locked.remove(q);
        q.locked = false;
        worlds.remove(q);
        worlds.add(0, q);
        fillSlots();
    }

    /** Generation progress of the worlds still loading, newest last, for the empty slots. */
    List<QueuedWorld> loading() {
        List<QueuedWorld> out = new ArrayList<>();
        for (QueuedWorld q : worlds) if (!q.isReady()) out.add(q);
        return out;
    }

    /** Takes a world out of the queue to be played. Its folder stays protected until the next one is played. */
    void take(QueuedWorld q) {
        int highest = nextNumber - 1;
        addPlayed(q.folder, highest);
        try {
            playedFile.getParentFile().mkdirs();
            Files.write(playedFile.toPath(), (q.folder + "\t" + highest + "\n").getBytes(StandardCharsets.UTF_8),
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            System.out.println("[Queue Wall] couldn't write " + playedFile + ": " + e);
        }
        worlds.remove(q);
        removeFromSlots(q);
        liveFolders.clear();
        for (QueuedWorld w : worlds) liveFolders.add(w.folder);
        liveFolders.add(q.folder);
    }

    void reset(QueuedWorld q) {
        QueueWall.log("reset " + q.folder + (q.isReady() ? "" : " (still generating)"));
        worlds.remove(q);
        removeFromSlots(q);
        q.cancelled = true;
        // a world still generating (or not started yet) cleans itself up when its thread notices the cancel
        if (q.finished) {
            io.execute(() -> cleanUp(q));
        }
    }

    /** Runs off the main thread once nothing else uses the world. */
    void cleanUp(QueuedWorld q) {
        if (!q.cleanedUp.compareAndSet(false, true)) return;
        try {
            if (cfg.keepResetWorlds && q.world != null && q.saveHandler != null) {
                // the region format version, as vanilla's own save sets it. without it Select World lists the world as "Must be converted!"
                q.world.m_6536808().m_1998885(19132);
                q.saveHandler.m_6356619(q.world.m_6536808());
            } else {
                deleteDir(new File(savesDir, q.folder));
            }
            q.world = null;
            q.saveHandler = null;
            liveFolders.remove(q.folder);
            if (cfg.deleteOldWorlds) deleteOldWorlds();
        } catch (Throwable t) {
            System.out.println("[Queue Wall] cleanup of " + q.folder + " failed");
            t.printStackTrace();
        }
    }

    private void deleteOldWorlds() {
        String[] names = savesDir.list();
        if (names == null) return;
        List<Integer> numbers = new ArrayList<>();
        for (String s : names) {
            Matcher m = NAME.matcher(s);
            if (m.matches() && !liveFolders.contains(s) && new File(new File(savesDir, s), "level.dat").isFile()) {
                try {
                    numbers.add(Integer.parseInt(m.group(1)));
                } catch (NumberFormatException ignored) {
                }
            }
        }
        Collections.sort(numbers, Collections.reverseOrder());
        for (int i = cfg.keepNewestWorlds; i < numbers.size(); i++) {
            String name = "New World " + numbers.get(i);
            if (protectedForRun(numbers.get(i))) continue;
            // worlds you actually played stay unless deletePlayedWorlds is on (A.7.2.a, keep runs until verified)
            if (cfg.deletePlayedWorlds || !played.contains(name)) deleteDir(new File(savesDir, name));
        }
    }

    private static void deleteDir(File f) {
        File[] kids = f.listFiles();
        if (kids != null) for (File k : kids) deleteDir(k);
        f.delete();
    }

    void setPaused(boolean p) {
        if (paused == p) return;
        paused = p;
        if (!p) {
            synchronized (pauseLock) {
                pauseLock.notifyAll();
            }
        }
    }

    void waitWhilePaused() {
        while (paused) {
            synchronized (pauseLock) {
                try {
                    pauseLock.wait(250);
                } catch (InterruptedException e) {
                    return;
                }
            }
        }
    }
}
