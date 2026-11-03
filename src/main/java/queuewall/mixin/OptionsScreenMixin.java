package queuewall.mixin;

import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.options.OptionsScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import queuewall.QueueWall;

/** "Stop Resets & Quit" on the Options screen opened from the Game menu, in a world played from the wall. */
@Mixin(OptionsScreen.class)
public abstract class OptionsScreenMixin extends Screen {
    private static final int STOP_BUTTON = 7302;
    // vanilla's button ids: Done on this screen, Save and Quit to Title on the Game menu
    private static final int DONE = 200;
    private static final int SAVE_AND_QUIT = 1;

    @Shadow
    private Screen parent;

    // bottom left, only in a world played from the wall
    @Inject(method = "init", at = @At("TAIL"))
    private void queuewall$addButton(CallbackInfo ci) {
        if (this.parent instanceof GameMenuScreen && QueueWall.isPlayingFromWall() && !this.minecraft.isMultiplayer()) {
            this.buttons.add(new ButtonWidget(STOP_BUTTON, 4, this.height - 24, Math.min(120, this.width / 2 - 108), 20, "Stop Resets & Quit"));
        }
    }

    // Done, then the Game menu's Save and Quit to Title, with the wall told to stay away so the title screen opens
    @Inject(method = "buttonClicked", at = @At("HEAD"), cancellable = true)
    private void queuewall$click(ButtonWidget button, CallbackInfo ci) {
        if (button.id != STOP_BUTTON) return;
        ci.cancel();
        QueueWall.stopResets();
        ((ScreenInvoker) this).queuewall$buttonClicked(new ButtonWidget(DONE, 0, 0, ""));
        ((ScreenInvoker) this.parent).queuewall$buttonClicked(new ButtonWidget(SAVE_AND_QUIT, 0, 0, ""));
    }
}
