package queuewall.mixin;

import net.minecraft.client.render.world.RenderChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import queuewall.Preview;

@Mixin(RenderChunk.class)
public abstract class RenderChunkMixin {
    // WorldRenderer always makes 16 block sections (1.2 drops the size fields)
    private static final int SIZE = 16;

    // previews only draw chunks the queued world already has. Without this, a section past the spawn area would
    // make the game generate new chunks of the queued world on the main thread
    @Inject(method = "compile", at = @At("HEAD"), cancellable = true)
    private void queuewall$onlyLoadedChunks(CallbackInfo ci) {
        if (!Preview.active) return;
        RenderChunk r = (RenderChunk) (Object) this;
        if (r.world == null || !r.world.isAreaLoaded(r.originX - 1, r.originY - 1, r.originZ - 1,
                r.originX + SIZE + 1, r.originY + SIZE + 1, r.originZ + SIZE + 1)) {
            ci.cancel();
        }
    }
}
