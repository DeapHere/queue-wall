package queuewall;

/** Background world generation threads. Mixins check for this class to keep vanilla behaviour on the main thread. */
public final class GenThread extends Thread {
    public GenThread(Runnable r, String name) {
        super(r, name);
        setDaemon(true);
        setPriority(Thread.MIN_PRIORITY);
    }

    public static boolean isCurrent() {
        return Thread.currentThread() instanceof GenThread;
    }
}
