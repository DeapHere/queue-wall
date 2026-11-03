package queuewall.mixin;

import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import queuewall.GenThread;

@Mixin(Vec3d.class)
public abstract class Vec3dMixin {
    // same as BoxMixin, for the vector pool
    @Inject(method = "fromPool", at = @At("HEAD"), cancellable = true)
    private static void queuewall$unpooledOffMainThread(double x, double y, double z, CallbackInfoReturnable<Vec3d> cir) {
        if (GenThread.isCurrent()) cir.setReturnValue(Vec3d.of(x, y, z));
    }
}
