package queuewall.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.unmapped.C_8651652;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import queuewall.QueueWall;

@Mixin(C_8651652.class)
public abstract class DebugScreenMixin {
    @Shadow
    private Minecraft f_0711669;

    // the mod and its version on F3, under the seed line, so it shows in every run's recording (A.2.11.e)
    @Inject(method = "m_1283885", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glPopMatrix()V", ordinal = 0))
    private void queuewall$showModOnF3(float partialTicks, boolean hasScreen, int mouseX, int mouseY, CallbackInfo ci) {
        f_0711669.f_0426313.m_1950885("Queue Wall " + QueueWall.VERSION, 2, 114, 14737632);
    }
}
