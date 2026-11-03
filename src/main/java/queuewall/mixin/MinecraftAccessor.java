package queuewall.mixin;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Minecraft.class)
public interface MinecraftAccessor {
    /** Vanilla's "Building terrain" step. QueuedWorld runs it on a generator thread. */
    @Invoker("prepareWorld")
    void queuewall$prepareWorld(String message);
}
