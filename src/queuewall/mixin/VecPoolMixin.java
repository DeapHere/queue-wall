package queuewall.mixin;

import net.minecraft.unmapped.C_0557736;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import queuewall.GenThread;

@Mixin(C_0557736.class)
public abstract class VecPoolMixin {
    // same as the bounding box pool
    @Inject(method = "m_4784226", at = @At("HEAD"), cancellable = true)
    private static void queuewall$freshOffMainThread(double x, double y, double z, CallbackInfoReturnable<C_0557736> cir) {
        if (GenThread.isCurrent()) {
            cir.setReturnValue(C_0557736.m_7697424(x, y, z));
        }
    }
}
