package queuewall;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Shared state that 1.0 world generation assumes it has to itself, and what Queue Wall's generator threads use
 * instead. The main thread always uses vanilla's own state.
 */
public final class ThreadSafety {
    /**
     * Chunk population uses the static biome decorators and SandBlock.fallInstantly, so the generator threads
     * populate one chunk at a time (ChunkCacheMixin).
     */
    public static final ReentrantLock POPULATE = new ReentrantLock();

    /** A generator thread's own copy of IntCache's static fields, starting out as vanilla's initial values. */
    public static final class IntCacheState {
        public int maxSize = 256;
        public final List<int[]> tcache = new ArrayList<>();
        public final List<int[]> tallocated = new ArrayList<>();
        public final List<int[]> cache = new ArrayList<>();
        public final List<int[]> allocated = new ArrayList<>();
    }

    private static final ThreadLocal<IntCacheState> INT_CACHE = ThreadLocal.withInitial(IntCacheState::new);

    /**
     * Gives this generator thread a fresh IntCache state, the state a new game starts with. Called before every world,
     * so nothing carries over from the worlds this thread made before, above all ones reset while still generating
     * (without this, after enough of those the pool could hand out arrays still in use and worlds failed to generate).
     */
    public static void freshIntCache() {
        INT_CACHE.set(new IntCacheState());
    }

    /** This thread's IntCache state, or null for every thread that isn't a generator thread. */
    public static IntCacheState intCache() {
        return GenThread.isCurrent() ? INT_CACHE.get() : null;
    }

    private ThreadSafety() {}
}
