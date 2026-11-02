package queuewall.mixin;

import net.minecraft.unmapped.C_5577922;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import queuewall.GenThread;

@Mixin(C_5577922.class)
public abstract class BoxPoolMixin {
    // the bounding box pool is static and reset by the main thread every tick, so generator threads get fresh boxes
    @Inject(method = "m_8513514", at = @At("HEAD"), cancellable = true)
    private static void queuewall$freshOffMainThread(double x1, double y1, double z1, double x2, double y2, double z2, CallbackInfoReturnable<C_5577922> cir) {
        if (GenThread.isCurrent()) {
            cir.setReturnValue(C_5577922.m_9058890(x1, y1, z1, x2, y2, z2));
        }
    }
}
