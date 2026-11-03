package queuewall.mixin;

import java.util.List;
import net.minecraft.world.biome.IntCache;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import queuewall.ThreadSafety;
import queuewall.ThreadSafety.IntCacheState;

/**
 * IntCache (the biome layers' array pool) keeps its pool in static fields, so two threads generating terrain at
 * once would hand each other's arrays out. On Queue Wall's generator threads, vanilla's get() and next() run
 * unchanged but on that thread's own copy of the fields. Every other thread uses the real static fields.
 */
@Mixin(IntCache.class)
public abstract class IntCacheMixin {
    @Redirect(method = {"get", "next"}, at = @At(value = "FIELD", target = "Lnet/minecraft/world/biome/IntCache;tcache:Ljava/util/List;", opcode = Opcodes.GETSTATIC))
    private static List<int[]> queuewall$tcache() {
        IntCacheState s = ThreadSafety.intCache();
        return s != null ? s.tcache : IntCacheAccessor.queuewall$tcache();
    }

    @Redirect(method = {"get", "next"}, at = @At(value = "FIELD", target = "Lnet/minecraft/world/biome/IntCache;tallocated:Ljava/util/List;", opcode = Opcodes.GETSTATIC))
    private static List<int[]> queuewall$tallocated() {
        IntCacheState s = ThreadSafety.intCache();
        return s != null ? s.tallocated : IntCacheAccessor.queuewall$tallocated();
    }

    @Redirect(method = {"get", "next"}, at = @At(value = "FIELD", target = "Lnet/minecraft/world/biome/IntCache;cache:Ljava/util/List;", opcode = Opcodes.GETSTATIC))
    private static List<int[]> queuewall$cache() {
        IntCacheState s = ThreadSafety.intCache();
        return s != null ? s.cache : IntCacheAccessor.queuewall$cache();
    }

    @Redirect(method = {"get", "next"}, at = @At(value = "FIELD", target = "Lnet/minecraft/world/biome/IntCache;allocated:Ljava/util/List;", opcode = Opcodes.GETSTATIC))
    private static List<int[]> queuewall$allocated() {
        IntCacheState s = ThreadSafety.intCache();
        return s != null ? s.allocated : IntCacheAccessor.queuewall$allocated();
    }

    @Redirect(method = "get", at = @At(value = "FIELD", target = "Lnet/minecraft/world/biome/IntCache;maxSize:I", opcode = Opcodes.GETSTATIC))
    private static int queuewall$getMaxSize() {
        IntCacheState s = ThreadSafety.intCache();
        return s != null ? s.maxSize : IntCacheAccessor.queuewall$getMaxSize();
    }

    @Redirect(method = "get", at = @At(value = "FIELD", target = "Lnet/minecraft/world/biome/IntCache;maxSize:I", opcode = Opcodes.PUTSTATIC))
    private static void queuewall$setMaxSize(int size) {
        IntCacheState s = ThreadSafety.intCache();
        if (s != null) s.maxSize = size;
        else IntCacheAccessor.queuewall$setMaxSize(size);
    }
}
