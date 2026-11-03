package queuewall;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.ClientPlayerInteractionManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.mob.player.ClientPlayerEntity;
import net.minecraft.client.options.GameOptions;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.render.world.RenderChunk;
import net.minecraft.client.render.world.WorldRenderer;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.HitResult;
import net.minecraft.world.World;
import queuewall.mixin.GameRendererAccessor;
import queuewall.mixin.WorldRendererAccessor;

/**
 * One wall slot's spawn preview: its own WorldRenderer pointed at a queued world, drawn by vanilla's
 * GameRenderer.renderWorld from the camera of the player vanilla made for that world. While it draws, the client's
 * world, renderer, player, window size and lightmap are swapped for the preview's, and swapped back right after.
 * The world isn't ticked, so this is the spawn as it is when the world is played.
 */
public final class Preview {
    /** True while a preview or a wall slot's progress screen is being drawn. Read by the render mixins. */
    public static boolean active;

    private final Minecraft mc;
    // the configured preview distance, and the one in use: never farther than the game's own render distance
    private final int cfgDistance;
    private int distance;
    private WorldRenderer renderer;
    private QueuedWorld shown;
    private int lightmap = -1;
    private boolean built;
    private final List<RenderChunk> queue = new ArrayList<>();
    private int queuePos;
    private final SlotTexture picture = new SlotTexture();
    /** The picture is of the fully built world and can be redrawn as it is. */
    private boolean cached;

    // what begin() swapped out
    private World savedWorld;
    private WorldRenderer savedRenderer;
    private ClientPlayerEntity savedPlayer;
    private MobEntity savedCamera;
    private ClientPlayerInteractionManager savedInteractionManager;
    private HitResult savedTarget;
    private int savedWidth, savedHeight;
    private int savedDistance;
    private boolean savedOcclusion;
    private boolean savedAnaglyph;
    private int savedLightmap;
    private float savedFog, savedLastFog, savedFov, savedLastFov;

    Preview(Minecraft mc, int distance) {
        this.mc = mc;
        this.cfgDistance = distance;
        this.distance = effectiveDistance();
    }

    // 1 = normal, 2 = short, 3 = tiny. far counts as normal, since only the 128 block spawn area exists
    private int effectiveDistance() {
        return Math.max(cfgDistance, Math.max(1, mc.options.viewDistance));
    }

    boolean isBuilt() {
        return built;
    }

    /** Points this slot at a world, or at nothing. Must be called with null before a world is played or dropped. */
    void show(QueuedWorld q) {
        if (q == shown) return;
        shown = q;
        built = false;
        cached = false;
        queue.clear();
        queuePos = 0;
        if (renderer == null && q == null) return;
        if (renderer == null) renderer = new WorldRenderer(mc, mc.textureManager);
        if (lightmap < 0) lightmap = mc.textureManager.load(new BufferedImage(16, 16, BufferedImage.TYPE_INT_RGB));
        Preview.active = true;
        MobEntity camera = mc.camera;
        savedDistance = mc.options.viewDistance;
        try {
            // with the preview's render distance and camera, so the renderer places its sections around the spawn
            mc.options.viewDistance = distance;
            if (q != null) mc.camera = q.camera;
            renderer.setWorld(q == null ? null : q.world);
        } finally {
            mc.camera = camera;
            mc.options.viewDistance = savedDistance;
            Preview.active = false;
            // setWorld points the shared entity render dispatcher at its world
            EntityRenderDispatcher.INSTANCE.setWorld(mc.world);
        }
    }

    /**
     * Builds chunk sections until the deadline, nearest first. Returns true once everything is built.
     * This compiles the sections itself instead of relying on WorldRenderer.compileChunks, which only builds a few
     * sections per call (and with OptiFine says it's done after the first call).
     */
    boolean build(long deadline) {
        if (shown == null || built) return true;
        Preview.active = true;
        try {
            while (System.nanoTime() < deadline) {
                if (queuePos >= queue.size()) {
                    findDirtySections();
                    if (queue.isEmpty()) {
                        built = true;
                        break;
                    }
                }
                RenderChunk section = queue.get(queuePos++);
                if (section.dirty) {
                    section.compile();
                    section.dirty = false;
                }
            }
        } finally {
            Preview.active = false;
        }
        return built;
    }

    private RenderChunk[] sections() {
        RenderChunk[] s = ((WorldRendererAccessor) renderer).queuewall$chunks();
        return s == null ? new RenderChunk[0] : s;
    }

    private void findDirtySections() {
        queue.clear();
        queuePos = 0;
        for (RenderChunk section : sections()) {
            if (section.dirty) queue.add(section);
        }
        queue.sort(Comparator.comparingDouble(section -> section.squaredDistanceToCenter(shown.camera)));
    }

    // true if the renderer has sections that still need building (OptiFine moves sections around while rendering)
    private boolean hasDirtySections() {
        for (RenderChunk section : sections()) {
            if (section.dirty) return true;
        }
        return false;
    }

    /** Brings the slot's picture up to date at this size in window pixels. A finished picture is kept as it is. */
    void update(int width, int height) {
        if (shown == null || width <= 0 || height <= 0) return;
        if (effectiveDistance() != distance) {
            // render distance changed in Options: load the renderer's sections again at the new distance
            distance = effectiveDistance();
            QueuedWorld q = shown;
            show(null);
            show(q);
        }
        if (cached && width == picture.width && height == picture.height) return;
        begin(width, height);
        try {
            // tick delta 1: the camera is drawn exactly where it is, and the render time limit is 0 so this doesn't
            // wait for chunk building (build() does that, within the wall's time budget)
            mc.gameRenderer.renderWorld(1.0F, 0L);
        } finally {
            end();
        }
        boolean copied = picture.copyFromWindow(width, height);
        if (built && hasDirtySections()) built = false;
        // a queued world never changes, so once it's fully built the picture is kept and redrawn from that
        cached = built && copied;
    }

    /** Draws the picture over a rectangle in GUI coordinates. */
    void draw(float x0, float y0, float x1, float y1) {
        picture.draw(x0, y0, x1, y1);
    }

    boolean hasPicture() {
        return !picture.isEmpty() && shown != null;
    }

    // renderWorld reads the world, player, interaction manager, render distance, window size and lightmap from the client
    private void begin(int width, int height) {
        GameOptions o = mc.options;
        savedWorld = mc.world;
        savedRenderer = mc.worldRenderer;
        savedPlayer = mc.player;
        savedCamera = mc.camera;
        savedInteractionManager = mc.interactionManager;
        savedTarget = mc.crosshairTarget;
        savedWidth = mc.width;
        savedHeight = mc.height;
        savedDistance = o.viewDistance;
        savedOcclusion = o.advancedOpengl;
        savedAnaglyph = o.anaglyph;
        savedLightmap = mc.gameRenderer.lightMapTexture;
        mc.world = shown.world;
        mc.worldRenderer = renderer;
        mc.player = (ClientPlayerEntity) shown.camera;
        mc.camera = shown.camera;
        mc.interactionManager = shown.interactionManager;
        mc.crosshairTarget = null;
        mc.width = width;
        mc.height = height;
        o.viewDistance = distance;
        // occlusion queries and 3D anaglyph are about the window, not a slot
        o.advancedOpengl = false;
        o.anaglyph = false;
        GameRendererAccessor gr = (GameRendererAccessor) mc.gameRenderer;
        savedFog = gr.queuewall$getFogBrightness();
        savedLastFog = gr.queuewall$getLastFogBrightness();
        savedFov = gr.queuewall$getFovMultiplier();
        savedLastFov = gr.queuewall$getLastFovMultiplier();
        // GameRenderer.tick eases the fog brightness and the field of view toward the camera's light and movement
        // speed, so a world starts dark and brightens, and before any world has been played the field of view is
        // 0. A preview uses the values they settle at for its camera (tick's targets, without the easing)
        float light = shown.world.getBrightness(MathHelper.floor(shown.camera.x), MathHelper.floor(shown.camera.y), MathHelper.floor(shown.camera.z));
        float far = (3 - distance) / 3.0F;
        gr.queuewall$setFogBrightness(light * (1.0F - far) + far);
        gr.queuewall$setLastFogBrightness(light * (1.0F - far) + far);
        float speed = ((ClientPlayerEntity) shown.camera).getAdjustedMovementSpeed();
        gr.queuewall$setFovMultiplier(speed);
        gr.queuewall$setLastFovMultiplier(speed);
        mc.gameRenderer.lightMapTexture = lightmap;
        gr.queuewall$setLightMapDirty(true);
        Preview.active = true;
    }

    private void end() {
        Preview.active = false;
        GameOptions o = mc.options;
        mc.world = savedWorld;
        mc.worldRenderer = savedRenderer;
        mc.player = savedPlayer;
        mc.camera = savedCamera;
        mc.interactionManager = savedInteractionManager;
        mc.crosshairTarget = savedTarget;
        mc.width = savedWidth;
        mc.height = savedHeight;
        o.viewDistance = savedDistance;
        o.advancedOpengl = savedOcclusion;
        o.anaglyph = savedAnaglyph;
        GameRendererAccessor gr = (GameRendererAccessor) mc.gameRenderer;
        gr.queuewall$setFogBrightness(savedFog);
        gr.queuewall$setLastFogBrightness(savedLastFog);
        gr.queuewall$setFovMultiplier(savedFov);
        gr.queuewall$setLastFovMultiplier(savedLastFov);
        mc.gameRenderer.lightMapTexture = savedLightmap;
        gr.queuewall$setLightMapDirty(true);
        EntityRenderDispatcher.INSTANCE.setWorld(mc.world);
    }
}
