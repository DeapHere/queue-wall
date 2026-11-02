package queuewall.mixin;

import net.minecraft.unmapped.C_7028449;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import queuewall.QueueWall;

@Mixin(C_7028449.class)
public abstract class WorldRendererMixin {
    // previews only draw chunks the queued world already has. Without this, a section past the
    // spawn area would make the game generate new chunks on the main thread
    @Inject(method = "m_4904712", at = @At("HEAD"), cancellable = true)
    private void queuewall$onlyLoadedChunks(CallbackInfo ci) {
        if (!QueueWall.drawingPreview) return;
        C_7028449 r = (C_7028449) (Object) this;
        if (r.f_9425072 == null || !r.f_9425072.m_8926744(r.f_9148018 - 1, r.f_8057675 - 1, r.f_1468741 - 1,
                r.f_9148018 + r.f_6592251 + 1, r.f_8057675 + r.f_8249186 + 1, r.f_1468741 + r.f_4676706 + 1)) {
            ci.cancel();
        }
    }
}
