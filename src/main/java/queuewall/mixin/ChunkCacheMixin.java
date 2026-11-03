package queuewall.mixin;

import net.minecraft.world.chunk.ChunkCache;
import net.minecraft.world.chunk.ChunkSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import queuewall.ThreadSafety;

@Mixin(ChunkCache.class)
public abstract class ChunkCacheMixin {
    // population uses the static biome decorators and SandBlock.fallInstantly, so only one thread populates at a
    // time. This includes the main thread, which can populate while worlds generate (generateWhilePlaying)
    @Redirect(method = "populateChunk", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/chunk/ChunkSource;populateChunk(Lnet/minecraft/world/chunk/ChunkSource;II)V"))
    private void queuewall$populateOneAtATime(ChunkSource generator, ChunkSource source, int chunkX, int chunkZ) {
        ThreadSafety.POPULATE.lock();
        try {
            generator.populateChunk(source, chunkX, chunkZ);
        } finally {
            ThreadSafety.POPULATE.unlock();
        }
    }
}
