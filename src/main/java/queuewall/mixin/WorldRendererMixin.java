package queuewall.mixin;

import net.minecraft.client.render.Culler;
import net.minecraft.client.render.world.WorldRenderer;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import queuewall.Preview;

@Mixin(WorldRenderer.class)
public abstract class WorldRendererMixin {
    // previews show terrain only, no mobs, items or block entities
    @Inject(method = "renderEntities", at = @At("HEAD"), cancellable = true)
    private void queuewall$noEntitiesInPreviews(Vec3d cameraPos, Culler culler, float tickDelta, CallbackInfo ci) {
        if (Preview.active) ci.cancel();
    }
}
