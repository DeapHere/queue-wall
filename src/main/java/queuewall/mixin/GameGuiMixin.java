package queuewall.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GameGui;
import net.minecraft.client.gui.GuiElement;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Slice;
import queuewall.QueueWall;

@Mixin(GameGui.class)
public abstract class GameGuiMixin extends GuiElement {
    @Shadow
    private Minecraft minecraft;

    /**
     * Shows the mod and its version on F3, on the line under the seed, so it's in every run's recording (A.2.11.e).
     * This reads the y position F3 draws the seed line at (it moves between versions: 104 in 1.0 and 1.1, 112 in 1.2)
     * and draws one line below it. The seed line itself is left as it is.
     */
    @ModifyArg(method = "render",
            slice = @Slice(from = @At(value = "CONSTANT", args = "stringValue=Seed: ")),
            at = @At(value = "INVOKE", ordinal = 0,
                    target = "Lnet/minecraft/client/gui/GameGui;drawString(Lnet/minecraft/client/render/TextRenderer;Ljava/lang/String;III)V"),
            index = 3)
    private int queuewall$showModOnF3(int seedLineY) {
        this.drawString(this.minecraft.textRenderer, "Queue Wall " + QueueWall.VERSION, 2, seedLineY + 10, 14737632);
        return seedLineY;
    }
}
