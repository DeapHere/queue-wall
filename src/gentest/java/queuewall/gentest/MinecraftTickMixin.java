package queuewall.gentest;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import queuewall.GenerationTest;

@Mixin(Minecraft.class)
public abstract class MinecraftTickMixin {
    private static boolean queuewall$started;

    // runs the test once the title screen is up, then quits
    @Inject(method = "tick", at = @At("HEAD"))
    private void queuewall$runTest(CallbackInfo ci) {
        Minecraft mc = (Minecraft) (Object) this;
        if (queuewall$started || !(mc.screen instanceof TitleScreen)) return;
        queuewall$started = true;
        int code = GenerationTest.run(mc);
        System.exit(code);
    }
}
