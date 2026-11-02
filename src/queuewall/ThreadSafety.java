package queuewall;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Shared state that 1.0 world generation assumes it has to itself.
 *
 * IntCache (the biome layer array pool) gets one pool per generator thread; the main thread keeps vanilla's. Chunk population is
 * serialised with one lock, because it uses the static biome decorators and
 * BlockSand.fallInstantly.
 */
public final class ThreadSafety {
    public static final ReentrantLock POPULATE = new ReentrantLock();

    public static final class IntPool {
        public int bigSize = 256;
        public final List<int[]> freeSmall = new ArrayList<>();
        public final List<int[]> usedSmall = new ArrayList<>();
        public final List<int[]> freeBig = new ArrayList<>();
        public final List<int[]> usedBig = new ArrayList<>();
    }

    private static final ThreadLocal<IntPool> INT_POOL = ThreadLocal.withInitial(IntPool::new);

    // same logic as vanilla IntCache, just one pool per thread
    public static int[] getIntCache(int size) {
        IntPool p = INT_POOL.get();
        if (size <= 256) {
            int[] a = p.freeSmall.isEmpty() ? new int[256] : p.freeSmall.remove(p.freeSmall.size() - 1);
            p.usedSmall.add(a);
            return a;
        } else if (size > p.bigSize) {
            p.bigSize = size;
            p.freeBig.clear();
            p.usedBig.clear();
            int[] a = new int[p.bigSize];
            p.usedBig.add(a);
            return a;
        } else {
            int[] a = p.freeBig.isEmpty() ? new int[p.bigSize] : p.freeBig.remove(p.freeBig.size() - 1);
            p.usedBig.add(a);
            return a;
        }
    }

    public static void resetIntCache() {
        IntPool p = INT_POOL.get();
        if (!p.freeBig.isEmpty()) p.freeBig.remove(p.freeBig.size() - 1);
        if (!p.freeSmall.isEmpty()) p.freeSmall.remove(p.freeSmall.size() - 1);
        p.freeBig.addAll(p.usedBig);
        p.freeSmall.addAll(p.usedSmall);
        p.usedBig.clear();
        p.usedSmall.clear();
    }

    private ThreadSafety() {}
}
