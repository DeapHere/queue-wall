package queuewall.mixin;

import net.minecraft.unmapped.C_9054816;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import queuewall.GenThread;
import queuewall.ThreadSafety;

@Mixin(C_9054816.class)
public abstract class IntCacheMixin {
    // vanilla's IntCache is one static pool, which breaks when two threads generate terrain. Queue Wall's
    // generator threads each get their own pool (same logic). Every other thread runs vanilla's code untouched.
    @Inject(method = "m_3840947", at = @At("HEAD"), cancellable = true)
    private static void queuewall$getIntCache(int size, CallbackInfoReturnable<int[]> cir) {
        if (GenThread.isCurrent()) cir.setReturnValue(ThreadSafety.getIntCache(size));
    }

    @Inject(method = "m_8366870", at = @At("HEAD"), cancellable = true)
    private static void queuewall$resetIntCache(CallbackInfo ci) {
        if (GenThread.isCurrent()) {
            ThreadSafety.resetIntCache();
            ci.cancel();
        }
    }
}
