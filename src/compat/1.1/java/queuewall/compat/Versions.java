package queuewall.compat;

import net.minecraft.world.WorldSettings;
import net.minecraft.world.gen.WorldGeneratorType;

/** The few calls that differ between Minecraft versions. This copy is for 1.1 and 1.2. */
public final class Versions {
    private Versions() {
    }

    /** What the Create New World screen makes with an empty seed box: survival, structures on, not hardcore, default world type. */
    public static WorldSettings newWorldSettings(long seed) {
        return new WorldSettings(seed, 0, true, false, WorldGeneratorType.DEFAULT);
    }
}
