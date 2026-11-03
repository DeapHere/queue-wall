package queuewall.mixin;

import net.minecraft.util.math.Box;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import queuewall.GenThread;

@Mixin(Box.class)
public abstract class BoxMixin {
    // the box pool is static and reset by the main thread every tick, so generator threads get unpooled boxes
    // from vanilla's Box.of instead
    @Inject(method = "fromPool", at = @At("HEAD"), cancellable = true)
    private static void queuewall$unpooledOffMainThread(double minX, double minY, double minZ, double maxX, double maxY, double maxZ, CallbackInfoReturnable<Box> cir) {
        if (GenThread.isCurrent()) cir.setReturnValue(Box.of(minX, minY, minZ, maxX, maxY, maxZ));
    }
}
