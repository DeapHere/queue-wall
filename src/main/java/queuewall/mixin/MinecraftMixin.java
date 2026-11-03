package queuewall.mixin;

import net.minecraft.client.ClientPlayerInteractionManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.mob.player.ClientPlayerEntity;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.render.ProgressRenderer;
import net.minecraft.world.World;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.storage.WorldStorage;
import net.minecraft.world.storage.WorldStorageSource;
import org.objectweb.asm.Opcodes;
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
    // ---- playing a queued world -------------------------------------------------------------------------------
    // Playing goes through the normal startGame, so stats, setWorld, Building terrain, Simulating world and the
    // player spawn all run as for any new world. These two hand startGame the storage and world that were already
    // made in the background, in place of making new ones for the same folder.

    @Redirect(method = "startGame", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/storage/WorldStorageSource;get(Ljava/lang/String;Z)Lnet/minecraft/world/storage/WorldStorage;"))
    private WorldStorage queuewall$queuedStorage(WorldStorageSource source, String folder, boolean players) {
        QueuedWorld q = QueueWall.pending;
        if (q != null && q.folder.equals(folder)) return q.storage;
        return source.get(folder, players);
    }

    @Redirect(method = "startGame", at = @At(value = "NEW", target = "net/minecraft/world/World"))
    private World queuewall$queuedWorld(WorldStorage storage, String name, WorldSettings settings) {
        QueuedWorld q = QueueWall.pending;
        if (q != null && q.storage == storage) return q.world;
        return new World(storage, name, settings);
    }

    // ---- generating a queued world ----------------------------------------------------------------------------
    // QueuedWorld runs prepareWorld on a generator thread. There, its reads of the client's fields go to the queued
    // world instead. On the main thread (every normal world load) these return the field unchanged.

    @Redirect(method = "prepareWorld", at = @At(value = "FIELD", target = "Lnet/minecraft/client/Minecraft;world:Lnet/minecraft/world/World;", opcode = Opcodes.GETFIELD))
    private World queuewall$generatingWorld(Minecraft mc) {
        QueuedWorld q = QueuedWorld.generatingHere();
        return q != null ? q.world : mc.world;
    }

    @Redirect(method = "prepareWorld", at = @At(value = "FIELD", target = "Lnet/minecraft/client/Minecraft;player:Lnet/minecraft/client/entity/mob/player/ClientPlayerEntity;", opcode = Opcodes.GETFIELD))
    private ClientPlayerEntity queuewall$generatingPlayer(Minecraft mc) {
        // a new world has no player yet when prepareWorld runs
        return QueuedWorld.generatingHere() != null ? null : mc.player;
    }

    @Redirect(method = "prepareWorld", at = @At(value = "FIELD", target = "Lnet/minecraft/client/Minecraft;interactionManager:Lnet/minecraft/client/ClientPlayerInteractionManager;", opcode = Opcodes.GETFIELD))
    private ClientPlayerInteractionManager queuewall$generatingInteractionManager(Minecraft mc) {
        QueuedWorld q = QueuedWorld.generatingHere();
        return q != null ? q.interactionManager : mc.interactionManager;
    }

    @Redirect(method = "prepareWorld", at = @At(value = "FIELD", target = "Lnet/minecraft/client/Minecraft;progressRenderer:Lnet/minecraft/client/render/ProgressRenderer;", opcode = Opcodes.GETFIELD))
    private ProgressRenderer queuewall$generatingProgress(Minecraft mc) {
        QueuedWorld q = QueuedWorld.generatingHere();
        return q != null ? q.progress : mc.progressRenderer;
    }

    // a queued world stops after Building terrain. Simulating world runs when it's played, through startGame
    @Inject(method = "prepareWorld", cancellable = true, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/ProgressRenderer;progressStage(Ljava/lang/String;)V", ordinal = 1))
    private void queuewall$stopBeforeSimulating(String message, CallbackInfo ci) {
        if (QueuedWorld.generatingHere() != null) ci.cancel();
    }

    // ---- wall hooks ---------------------------------------------------------------------------------------------

    @Inject(method = "tick", at = @At("HEAD"))
    private void queuewall$tick(CallbackInfo ci) {
        QueueWall.onTick((Minecraft) (Object) this);
    }

    @ModifyVariable(method = "openScreen", at = @At("HEAD"), argsOnly = true)
    private Screen queuewall$screen(Screen screen) {
        return QueueWall.redirectScreen((Minecraft) (Object) this, screen);
    }
}
