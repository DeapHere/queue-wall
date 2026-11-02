package queuewall;

import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.lang.reflect.Field;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.unmapped.C_0557736;
import net.minecraft.unmapped.C_1331819;
import net.minecraft.unmapped.C_3362006;
import net.minecraft.unmapped.C_3716663;
import net.minecraft.unmapped.C_7947657;
import net.minecraft.unmapped.C_5553933;
import net.minecraft.unmapped.C_6057159;
import net.minecraft.unmapped.C_6607881;
import net.minecraft.unmapped.C_7028449;
import net.minecraft.unmapped.C_8398976;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.glu.GLU;

/**
 * Draws one wall slot: a second world renderer pointed at a queued world, looking from where the
 * player will spawn. The world isn't ticked, so this is the spawn as it is when you press play.
 */
final class PreviewRenderer {
    private final Minecraft mc;
    // the configured preview distance, and the one in use: never farther than the game's own render distance
    private final int cfgDistance;
    private int distance;
    private C_8398976 renderer;
    private QueuedWorld shown;
    private C_6607881 camera;
    private int lightmap = -1;
    private final int[] lightmapPixels = new int[256];
    private final float[] fog = new float[3];
    private boolean built;
    private final List<C_7028449> queue = new ArrayList<>();
    private int queuePos;
    private int cacheTex = -1;
    private int cacheW, cacheH;
    private int texW, texH;
    private boolean cached;

    // what begin() swapped out
    private C_6607881 savedView;
    private int savedDistance;
    private boolean savedOcclusion;
    private boolean savedAnaglyph;
    private int savedLightmap;

    PreviewRenderer(Minecraft mc, int distance) {
        this.mc = mc;
        this.cfgDistance = distance;
        this.distance = effectiveDistance();
    }

    // 1 = normal, 2 = short, 3 = tiny. far counts as normal, since only the 128 block spawn area exists
    private int effectiveDistance() {
        return Math.max(cfgDistance, Math.max(1, mc.f_9967940.f_7110074));
    }

    QueuedWorld shown() {
        return shown;
    }

    boolean isBuilt() {
        return built;
    }

    /** Points this slot at a world, or at nothing. Must be called with null before a world is played or dropped. */
    void show(QueuedWorld q) {
        if (q == shown) return;
        if (renderer == null && q == null) {
            shown = null;
            return;
        }
        if (q != null) {
            camera = new C_6057159(null);
            // survival players spawn facing north (PlayerControllerSP sets yaw -180)
            camera.m_0375747(q.camX, q.camY, q.camZ, -180.0F, 0.0F);
        }
        begin();
        try {
            if (renderer == null) renderer = new C_8398976(mc, mc.f_9413506);
            shown = q;
            built = false;
            cached = false;
            queue.clear();
            queuePos = 0;
            renderer.m_2989568(q == null ? null : q.world);
            if (q != null) {
                updateLightmap(q.world);
                updateFogColor(q.world);
            }
        } finally {
            end();
        }
    }

    /**
     * Builds chunk sections until the deadline, nearest first. Returns true once everything is built.
     * This builds the sections itself instead of calling RenderGlobal.updateRenderers, which only builds
     * a few sections per call (and with OptiFine says it's done after the first call).
     */
    boolean build(long deadline) {
        if (shown == null || built) return true;
        begin();
        try {
            while (System.nanoTime() < deadline) {
                if (queuePos >= queue.size()) {
                    findDirtySections();
                    if (queue.isEmpty()) {
                        built = true;
                        break;
                    }
                }
                C_7028449 section = queue.get(queuePos++);
                if (section.f_3085426) {
                    section.m_4904712();
                    section.f_3085426 = false;
                }
            }
        } finally {
            end();
        }
        return built;
    }

    // RenderGlobal's array of every chunk section. It's private, and loadRenderers replaces it
    private static Field sectionsField;

    private C_7028449[] sections() {
        try {
            if (sectionsField == null) {
                sectionsField = C_8398976.class.getDeclaredField("f_2222405");
                sectionsField.setAccessible(true);
            }
            C_7028449[] s = (C_7028449[]) sectionsField.get(renderer);
            return s == null ? new C_7028449[0] : s;
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("[Queue Wall] can't read the world renderer's sections", e);
        }
    }

    private void findDirtySections() {
        queue.clear();
        queuePos = 0;
        for (C_7028449 section : sections()) {
            if (section.f_3085426) queue.add(section);
        }
        queue.sort(Comparator.comparingDouble(section -> section.m_3039702(camera)));
    }

    // true if the renderer has sections that still need building (OptiFine moves sections around while rendering)
    private boolean hasDirtySections() {
        for (C_7028449 section : sections()) {
            if (section.f_3085426) return true;
        }
        return false;
    }

    /** Renders into a rectangle in window pixels (origin bottom left, like glViewport). */
    void render(int x, int y, int w, int h) {
        if (shown == null || w <= 0 || h <= 0) return;
        if (effectiveDistance() != distance) {
            // render distance changed in Options: the world renderer reloads its sections, so build again
            distance = effectiveDistance();
            built = false;
            cached = false;
            queue.clear();
            queuePos = 0;
        }
        if (cached && w == cacheW && h == cacheH) {
            drawCache(x, y, w, h);
            return;
        }
        // how far the preview reaches: the spawn area is loaded 128 blocks out
        float range = distance <= 1 ? 128.0F : distance == 2 ? 72.0F : 40.0F;
        begin();
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        // the player's own FOV setting, like EntityRenderer.getFOVModifier
        GLU.gluPerspective(70.0F + mc.f_9967940.f_7544507 * 40.0F, (float) w / h, 0.05F, range * 2.0F);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        try {
            GL11.glViewport(x, y, w, h);
            GL11.glEnable(GL11.GL_SCISSOR_TEST);
            GL11.glScissor(x, y, w, h);
            GL11.glClearColor(fog[0], fog[1], fog[2], 1.0F);
            GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);

            GL11.glEnable(GL11.GL_DEPTH_TEST);
            GL11.glDepthFunc(GL11.GL_LEQUAL);
            GL11.glDepthMask(true);
            GL11.glEnable(GL11.GL_CULL_FACE);
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glEnable(GL11.GL_ALPHA_TEST);
            GL11.glAlphaFunc(GL11.GL_GREATER, 0.1F);
            GL11.glDisable(GL11.GL_BLEND);
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
            GL11.glShadeModel(mc.f_9967940.f_7400378 ? GL11.GL_SMOOTH : GL11.GL_FLAT);

            // same as EntityRenderer.orientCamera in first person
            GL11.glTranslatef(0.0F, 0.0F, -0.1F);
            GL11.glRotatef(camera.f_0243146, 1.0F, 0.0F, 0.0F);
            GL11.glRotatef(camera.f_7165431 + 180.0F, 0.0F, 1.0F, 0.0F);

            GL11.glEnable(GL11.GL_FOG);
            GL11.glFogi(GL11.GL_FOG_MODE, GL11.GL_LINEAR);
            GL11.glFogf(GL11.GL_FOG_START, range * 0.6F);
            GL11.glFogf(GL11.GL_FOG_END, range);
            GL11.glFog(GL11.GL_FOG_COLOR, buffer(fog[0], fog[1], fog[2], 1.0F));

            // which sections are on screen, from the matrices just set up
            C_7947657.m_3188544();
            C_3716663 frustum = new C_3716663();
            frustum.m_0871758(camera.f_6638345, camera.f_1187082, camera.f_9103758);
            renderer.m_3595684(frustum, 1.0F);

            int terrain = mc.f_9413506.m_9688911("/terrain.png");
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, terrain);
            renderer.m_4843024(camera, 0, 1.0);

            GL11.glShadeModel(GL11.GL_FLAT);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, terrain);
            renderer.m_4843024(camera, 1, 1.0);
            if (built && hasDirtySections()) built = false;
            // a queued world never changes, so once it's fully built the picture is kept and redrawn from that
            if (built) saveCache(x, y, w, h);
        } finally {
            GL11.glMatrixMode(GL11.GL_PROJECTION);
            GL11.glPopMatrix();
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            GL11.glPopMatrix();
            GL11.glPopAttrib();
            end();
        }
    }

    private void saveCache(int x, int y, int w, int h) {
        for (int i = 0; i < 10 && GL11.glGetError() != GL11.GL_NO_ERROR; i++) {
            // clear errors left by earlier drawing, so only the copy's own error counts
        }
        if (cacheTex < 0) cacheTex = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, cacheTex);
        if (texW != w || texH != h) {
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
            GL11.glCopyTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGB, x, y, w, h, 0);
            texW = w;
            texH = h;
        } else {
            GL11.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, x, y, w, h);
        }
        cacheW = w;
        cacheH = h;
        cached = GL11.glGetError() == GL11.GL_NO_ERROR;
    }

    private void drawCache(int x, int y, int w, int h) {
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        GL11.glOrtho(0.0, 1.0, 0.0, 1.0, -1.0, 1.0);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        try {
            GL11.glViewport(x, y, w, h);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glDisable(GL11.GL_FOG);
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glDisable(GL11.GL_BLEND);
            GL11.glDisable(GL11.GL_ALPHA_TEST);
            GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, cacheTex);
            GL11.glBegin(GL11.GL_QUADS);
            GL11.glTexCoord2f(0, 0);
            GL11.glVertex2f(0, 0);
            GL11.glTexCoord2f(1, 0);
            GL11.glVertex2f(1, 0);
            GL11.glTexCoord2f(1, 1);
            GL11.glVertex2f(1, 1);
            GL11.glTexCoord2f(0, 1);
            GL11.glVertex2f(0, 1);
            GL11.glEnd();
        } finally {
            GL11.glMatrixMode(GL11.GL_PROJECTION);
            GL11.glPopMatrix();
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            GL11.glPopMatrix();
            GL11.glPopAttrib();
        }
    }

    // The world renderer reads the camera, render distance and lightmap from Minecraft, so swap ours in while it works.
    private void begin() {
        C_1331819 gs = mc.f_9967940;
        savedView = mc.f_2096674;
        savedDistance = gs.f_7110074;
        savedOcclusion = gs.f_6096380;
        savedAnaglyph = gs.f_5010661;
        savedLightmap = mc.f_4267957.f_2242117;
        if (camera != null) mc.f_2096674 = camera;
        gs.f_7110074 = distance;
        gs.f_6096380 = false;
        gs.f_5010661 = false;
        if (lightmap >= 0) mc.f_4267957.f_2242117 = lightmap;
        QueueWall.drawingPreview = true;
    }

    private void end() {
        QueueWall.drawingPreview = false;
        C_1331819 gs = mc.f_9967940;
        mc.f_2096674 = savedView;
        gs.f_7110074 = savedDistance;
        gs.f_6096380 = savedOcclusion;
        gs.f_5010661 = savedAnaglyph;
        mc.f_4267957.f_2242117 = savedLightmap;
        // the world renderer points the shared tile entity renderer at its world
        C_3362006.f_9792930.m_1035027(mc.f_5854988);
    }

    // EntityRenderer.updateLightmap for this world, without torch flicker
    private void updateLightmap(C_5553933 world) {
        if (lightmap < 0) lightmap = mc.f_9413506.m_0440500(new BufferedImage(16, 16, BufferedImage.TYPE_INT_RGB));
        float[] table = world.f_6669533.f_3718428;
        float sun = world.m_9739314(1.0F);
        float gamma = mc.f_9967940.f_5541398;
        for (int i = 0; i < 256; i++) {
            float sky = table[i / 16] * (sun * 0.95F + 0.05F);
            float block = table[i % 16] * 1.5F;
            if (world.f_6992629 > 0) sky = table[i / 16];
            float skyR = sky * (sun * 0.65F + 0.35F);
            float skyG = sky * (sun * 0.65F + 0.35F);
            float blockG = block * ((block * 0.6F + 0.4F) * 0.6F + 0.4F);
            float blockB = block * (block * block * 0.6F + 0.4F);
            float r = (skyR + block) * 0.96F + 0.03F;
            float g = (skyG + blockG) * 0.96F + 0.03F;
            float b = (sky + blockB) * 0.96F + 0.03F;
            if (world.f_6669533.f_8006473 == 1) {
                r = 0.22F + block * 0.75F;
                g = 0.28F + blockG * 0.75F;
                b = 0.25F + blockB * 0.75F;
            }
            r = applyGamma(Math.min(r, 1.0F), gamma);
            g = applyGamma(Math.min(g, 1.0F), gamma);
            b = applyGamma(Math.min(b, 1.0F), gamma);
            lightmapPixels[i] = 0xFF000000 | (int) (r * 255.0F) << 16 | (int) (g * 255.0F) << 8 | (int) (b * 255.0F);
        }
        mc.f_9413506.m_0236438(lightmapPixels, 16, 16, lightmap);
    }

    private static float applyGamma(float c, float gamma) {
        float inv = 1.0F - c;
        inv = 1.0F - inv * inv * inv * inv;
        c = c * (1.0F - gamma) + inv * gamma;
        c = c * 0.96F + 0.03F;
        return Math.max(0.0F, Math.min(1.0F, c));
    }

    // fog colour blended toward the sky colour the way EntityRenderer does for short/tiny render distance
    private void updateFogColor(C_5553933 world) {
        C_0557736 sky = world.m_3993231(camera, 1.0F);
        C_0557736 fogColor = world.m_7031870(1.0F);
        float f = 1.0F - (float) Math.pow(1.0F / (4 - distance), 0.25);
        fog[0] = (float) (fogColor.f_8797516 + (sky.f_8797516 - fogColor.f_8797516) * f);
        fog[1] = (float) (fogColor.f_7064947 + (sky.f_7064947 - fogColor.f_7064947) * f);
        fog[2] = (float) (fogColor.f_1767139 + (sky.f_1767139 - fogColor.f_1767139) * f);
    }

    private static FloatBuffer buffer(float a, float b, float c, float d) {
        FloatBuffer fb = ByteBuffer.allocateDirect(16).order(ByteOrder.nativeOrder()).asFloatBuffer();
        fb.put(a).put(b).put(c).put(d).flip();
        return fb;
    }
}
