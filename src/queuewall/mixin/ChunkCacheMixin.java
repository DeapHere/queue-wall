package queuewall.mixin;

import net.minecraft.unmapped.C_1316134;
import net.minecraft.unmapped.C_9848147;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import queuewall.ThreadSafety;

@Mixin(C_1316134.class)
public abstract class ChunkCacheMixin {
    // population uses static biome decorators and BlockSand.fallInstantly, so only one thread populates at a time
    @Redirect(method = "m_4129669", at = @At(value = "INVOKE", target = "Lnet/minecraft/unmapped/C_9848147;m_4129669(Lnet/minecraft/unmapped/C_9848147;II)V"))
    private void queuewall$lockPopulate(C_9848147 generator, C_9848147 provider, int chunkX, int chunkZ) {
        ThreadSafety.POPULATE.lock();
        try {
            generator.m_4129669(provider, chunkX, chunkZ);
        } finally {
            ThreadSafety.POPULATE.unlock();
        }
    }
}
