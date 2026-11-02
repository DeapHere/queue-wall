package queuewall.mixin;

import net.minecraft.unmapped.C_0349999;
import net.minecraft.unmapped.C_1860331;
import net.minecraft.unmapped.C_2348249;
import net.minecraft.unmapped.C_3020744;
import net.minecraft.unmapped.C_7077203;
import net.minecraft.unmapped.C_8938952;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import queuewall.QueueWall;

// the Options screen opened from the Game menu
@Mixin(C_1860331.class)
public abstract class OptionsScreenMixin extends C_3020744 {
    private static final int STOP_BUTTON = 7302;

    @Shadow
    private C_3020744 f_8123119;

    // bottom left, only in a world played from the wall
    @Inject(method = "m_3593494", at = @At("TAIL"))
    private void queuewall$addButton(CallbackInfo ci) {
        if (f_8123119 instanceof C_7077203 && QueueWall.isPlayingFromWall() && !this.f_7153641.m_2812472()) {
            this.f_2213969.add(new C_2348249(STOP_BUTTON, 4, this.f_3080061 - 24, Math.min(120, this.f_5465691 / 2 - 108), 20, "Stop Resets & Quit"));
        }
    }

    // Save and Quit to Title, but to the real title screen: no wall and no next locked world
    @Inject(method = "m_7971793", at = @At("HEAD"), cancellable = true)
    private void queuewall$click(C_2348249 button, CallbackInfo ci) {
        if (button.f_5920996 == STOP_BUTTON) {
            this.f_7153641.f_9967940.m_2313216();
            QueueWall.stopResets();
            this.f_7153641.f_8029336.m_5854284(C_0349999.f_8680599, 1);
            this.f_7153641.m_8675683(null);
            this.f_7153641.m_6408915(new C_8938952());
            ci.cancel();
        }
    }
}
