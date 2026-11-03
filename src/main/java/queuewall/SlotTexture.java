package queuewall;

import org.lwjgl.opengl.GL11;

/**
 * One wall slot's picture. Vanilla's renderers only draw to the whole window, so a slot is drawn at the window's
 * bottom left at the slot's size, copied into this texture, and drawn into its place once everything is copied.
 */
final class SlotTexture {
    private int id = -1;
    private int texW, texH;
    int width, height;

    /** Copies the bottom left width x height pixels of the window. False if GL reported an error. */
    boolean copyFromWindow(int width, int height) {
        // clear errors left by earlier drawing, so only the copy's own error counts
        for (int i = 0; i < 10 && GL11.glGetError() != GL11.GL_NO_ERROR; i++) {
        }
        if (id < 0) id = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, id);
        if (texW != width || texH != height) {
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
            GL11.glCopyTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGB, 0, 0, width, height, 0);
            texW = width;
            texH = height;
        } else {
            GL11.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, 0, 0, width, height);
        }
        this.width = width;
        this.height = height;
        return GL11.glGetError() == GL11.GL_NO_ERROR;
    }

    boolean isEmpty() {
        return id < 0 || width == 0;
    }

    /** Draws the picture over a rectangle in GUI coordinates (origin top left). */
    void draw(float x0, float y0, float x1, float y1) {
        if (isEmpty()) return;
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glDisable(GL11.GL_ALPHA_TEST);
        GL11.glDisable(GL11.GL_FOG);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, id);
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glTexCoord2f(0, 1);
        GL11.glVertex2f(x0, y0);
        GL11.glTexCoord2f(0, 0);
        GL11.glVertex2f(x0, y1);
        GL11.glTexCoord2f(1, 0);
        GL11.glVertex2f(x1, y1);
        GL11.glTexCoord2f(1, 1);
        GL11.glVertex2f(x1, y0);
        GL11.glEnd();
        GL11.glEnable(GL11.GL_ALPHA_TEST);
    }

    void delete() {
        if (id >= 0) GL11.glDeleteTextures(id);
        id = -1;
        texW = texH = width = height = 0;
    }
}
