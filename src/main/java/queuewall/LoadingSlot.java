package queuewall;

import net.minecraft.client.Minecraft;
import net.minecraft.client.render.ProgressRenderer;
import org.lwjgl.opengl.GL11;

/**
 * A wall slot whose world is still generating. It shows vanilla's own progress screen for that world, the same
 * screen a new world shows while it loads, drawn at the slot's size by a ProgressRenderer of its own.
 */
final class LoadingSlot {
    private final Minecraft mc;
    private final ProgressRenderer screen;
    private final SlotTexture picture = new SlotTexture();

    LoadingSlot(Minecraft mc) {
        this.mc = mc;
        this.screen = new ProgressRenderer(mc);
    }

    /** Draws the progress screen for a world (or for one not started yet, with null) at this size in window pixels. */
    void update(QueuedWorld q, int width, int height) {
        QueuedWorld.Progress p = q == null ? null : q.progress;
        int savedWidth = mc.width;
        int savedHeight = mc.height;
        mc.width = width;
        mc.height = height;
        Preview.active = true;
        GL11.glViewport(0, 0, width, height);
        try {
            // before generation starts: the title that setWorld shows for a new world, with no stage yet
            screen.progressStart(p == null ? "Generating level" : p.title);
            screen.progressStage(p == null ? "" : p.stage);
            int percentage = p == null ? -1 : p.percentage;
            if (percentage >= 0) screen.progressStagePercentage(percentage);
        } finally {
            Preview.active = false;
            mc.width = savedWidth;
            mc.height = savedHeight;
        }
        picture.copyFromWindow(width, height);
    }

    void draw(float x0, float y0, float x1, float y1) {
        picture.draw(x0, y0, x1, y1);
    }
}
