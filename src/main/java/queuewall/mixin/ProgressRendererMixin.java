package queuewall.mixin;

import net.minecraft.client.render.ProgressRenderer;
import org.lwjgl.opengl.Display;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import queuewall.Preview;

@Mixin(ProgressRenderer.class)
public abstract class ProgressRendererMixin {
    // the wall draws each loading world with the vanilla progress screen, into its own slot. The screen is shown
    // once with the rest of the wall, not on its own
    @Redirect(method = "progressStagePercentage", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/Display;update()V", remap = false))
    private void queuewall$noSwapForWallSlots() {
        if (!Preview.active) Display.update();
    }
}
