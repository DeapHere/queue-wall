package queuewall.mixin;

import net.minecraft.client.ParticleManager;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import queuewall.Preview;

// particles belong to the world being played, never to a preview
@Mixin(ParticleManager.class)
public abstract class ParticleManagerMixin {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void queuewall$noParticlesInPreviews(Entity camera, float tickDelta, CallbackInfo ci) {
        if (Preview.active) ci.cancel();
    }

    @Inject(method = "renderLit", at = @At("HEAD"), cancellable = true)
    private void queuewall$noLitParticlesInPreviews(Entity camera, float tickDelta, CallbackInfo ci) {
        if (Preview.active) ci.cancel();
    }
}
