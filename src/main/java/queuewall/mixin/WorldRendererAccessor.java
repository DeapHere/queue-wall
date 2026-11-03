package queuewall.mixin;

import net.minecraft.client.render.world.RenderChunk;
import net.minecraft.client.render.world.WorldRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(WorldRenderer.class)
public interface WorldRendererAccessor {
    /** Every chunk section of the renderer. setWorld and a render distance change replace the array. */
    @Accessor("chunks")
    RenderChunk[] queuewall$chunks();
}
