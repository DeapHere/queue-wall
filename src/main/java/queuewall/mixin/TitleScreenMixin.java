package queuewall.mixin;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import org.lwjgl.input.Keyboard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import queuewall.QueueWall;

@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin extends Screen {
    // the title screen stays vanilla, a key opens the wall
    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void queuewall$openWallKey(char chr, int key, CallbackInfo ci) {
        QueueWall.init(this.minecraft);
        if (key != Keyboard.KEY_NONE && key == QueueWall.cfg.keyOpenWall) {
            QueueWall.openWall(this.minecraft);
            ci.cancel();
        }
    }
}
