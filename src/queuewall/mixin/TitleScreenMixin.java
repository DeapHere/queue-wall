package queuewall.mixin;

import net.minecraft.unmapped.C_3020744;
import net.minecraft.unmapped.C_8938952;
import org.lwjgl.input.Keyboard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import queuewall.QueueWall;

@Mixin(C_8938952.class)
public abstract class TitleScreenMixin extends C_3020744 {
    // the title screen stays vanilla, a key opens the wall
    @Inject(method = "m_6992336", at = @At("HEAD"), cancellable = true)
    private void queuewall$openWallKey(char c, int key, CallbackInfo ci) {
        QueueWall.init(this.f_7153641);
        if (key != Keyboard.KEY_NONE && key == QueueWall.cfg.keyOpenWall) {
            QueueWall.openWall(this.f_7153641);
            ci.cancel();
        }
    }
}
