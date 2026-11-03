package queuewall.mixin;

import java.util.List;
import net.minecraft.world.biome.IntCache;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** IntCache's own static fields, for IntCacheMixin to fall back to off the generator threads. */
@Mixin(IntCache.class)
public interface IntCacheAccessor {
    @Accessor("maxSize")
    static int queuewall$getMaxSize() {
        throw new AssertionError();
    }

    @Accessor("maxSize")
    static void queuewall$setMaxSize(int size) {
        throw new AssertionError();
    }

    @Accessor("tcache")
    static List<int[]> queuewall$tcache() {
        throw new AssertionError();
    }

    @Accessor("tallocated")
    static List<int[]> queuewall$tallocated() {
        throw new AssertionError();
    }

    @Accessor("cache")
    static List<int[]> queuewall$cache() {
        throw new AssertionError();
    }

    @Accessor("allocated")
    static List<int[]> queuewall$allocated() {
        throw new AssertionError();
    }
}
