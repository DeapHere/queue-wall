package queuewall.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GameGui;
import net.minecraft.client.gui.GuiElement;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import queuewall.QueueWall;

@Mixin(GameGui.class)
public abstract class GameGuiMixin extends GuiElement {
    @Shadow
    private Minecraft minecraft;

    // the mod and its version on F3, on the line under the seed, so it shows in every run's recording (A.2.11.e)
    @Inject(method = "render", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glPopMatrix()V", ordinal = 0, remap = false))
    private void queuewall$showModOnF3(float tickDelta, boolean screenOpen, int mouseX, int mouseY, CallbackInfo ci) {
        this.drawString(this.minecraft.textRenderer, "Queue Wall " + QueueWall.VERSION, 2, 114, 14737632);
    }
}
