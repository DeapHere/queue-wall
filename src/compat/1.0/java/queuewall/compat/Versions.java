package queuewall.compat;

import net.minecraft.world.WorldSettings;

/** The few calls that differ between Minecraft versions. This copy is for 1.0. */
public final class Versions {
    private Versions() {
    }

    /** What the Create New World screen makes with an empty seed box: survival, structures on, not hardcore. */
    public static WorldSettings newWorldSettings(long seed) {
        return new WorldSettings(seed, 0, true, false);
    }
}
