package queuewall.mixin;

import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(GameRenderer.class)
public interface GameRendererAccessor {
    /** renderWorld rebuilds the lightmap from the current world when this is set. */
    @Accessor("lightMapDirty")
    void queuewall$setLightMapDirty(boolean dirty);

    /** How bright fog and sky are, eased toward the light at the camera once per tick. */
    @Accessor("fogBrightness")
    float queuewall$getFogBrightness();

    @Accessor("fogBrightness")
    void queuewall$setFogBrightness(float brightness);

    @Accessor("lastFogBrightness")
    float queuewall$getLastFogBrightness();

    @Accessor("lastFogBrightness")
    void queuewall$setLastFogBrightness(float brightness);

    /** The field of view multiplier from the camera's movement speed, eased once per tick like fogBrightness. */
    @Accessor("movementFovMultiplier")
    float queuewall$getFovMultiplier();

    @Accessor("movementFovMultiplier")
    void queuewall$setFovMultiplier(float multiplier);

    @Accessor("lastFov")
    float queuewall$getLastFovMultiplier();

    @Accessor("lastFov")
    void queuewall$setLastFovMultiplier(float multiplier);
}
