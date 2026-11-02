package queuewall.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.unmapped.C_2670477;
import net.minecraft.unmapped.C_3020744;
import net.minecraft.unmapped.C_5478016;
import net.minecraft.unmapped.C_5528202;
import net.minecraft.unmapped.C_5553933;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import queuewall.QueueWall;
import queuewall.QueuedWorld;

@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
    // Playing a queued world goes through the normal startWorld so stats and other mods see a normal world start.
    // These two swap in the save handler and world that were already made in the background.
    @Redirect(method = "m_5272083", at = @At(value = "INVOKE", target = "Lnet/minecraft/unmapped/C_5478016;m_0190283(Ljava/lang/String;Z)Lnet/minecraft/unmapped/C_5528202;"))
    private C_5528202 queuewall$queuedSaveHandler(C_5478016 saveLoader, String folder, boolean players) {
        QueuedWorld q = QueueWall.pending;
        if (q != null && q.folder.equals(folder)) {
            return q.saveHandler;
        }
        return saveLoader.m_0190283(folder, players);
    }

    @Redirect(method = "m_5272083", at = @At(value = "NEW", target = "net/minecraft/unmapped/C_5553933"))
    private C_5553933 queuewall$queuedWorld(C_5528202 saveHandler, String name, C_2670477 settings) {
        QueuedWorld q = QueueWall.pending;
        if (q != null && q.saveHandler == saveHandler) {
            return q.world;
        }
        return new C_5553933(saveHandler, name, settings);
    }

    @Inject(method = "m_8832598", at = @At("HEAD"))
    private void queuewall$tick(CallbackInfo ci) {
        QueueWall.onTick((Minecraft) (Object) this);
    }

    @ModifyVariable(method = "m_6408915", at = @At("HEAD"), argsOnly = true)
    private C_3020744 queuewall$screen(C_3020744 screen) {
        return QueueWall.redirectScreen((Minecraft) (Object) this, screen);
    }
}
