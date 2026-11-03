package queuewall.mixin;

import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import queuewall.Preview;

/** While a preview is drawn with vanilla's renderWorld, it doesn't aim at blocks or draw the hand. */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    @Inject(method = "pick", at = @At("HEAD"), cancellable = true)
    private void queuewall$noPickInPreviews(float tickDelta, CallbackInfo ci) {
        if (Preview.active) ci.cancel();
    }

    @Inject(method = "renderItemInHand", at = @At("HEAD"), cancellable = true)
    private void queuewall$noHandInPreviews(float tickDelta, int anaglyphRenderPass, CallbackInfo ci) {
        if (Preview.active) ci.cancel();
    }
}
