package graphics;

import org.lwjgl.LWJGLException;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.DisplayMode;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.util.glu.GLU;
import org.newdawn.slick.UnicodeFont;
import org.newdawn.slick.font.effects.ColorEffect;

import java.awt.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

public class Draw3D {

    public void init() {
        //init display
        try {
            Display.setDisplayMode(new DisplayMode(Support.SCREEN_WIDTH, Support.SCREEN_HEIGHT));
            Display.create();
            Display.setVSyncEnabled(true);
        } catch (LWJGLException e) {
            e.printStackTrace();
            System.exit(0);
        }

        //init gl
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glClearColor(0.0f, 0.0f, 0.0f, 1.0f);

        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glLoadIdentity();
        GLU.gluPerspective(60.0f, 800f / 600f, 1f, 1500.0f);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);

        GL11.glLoadIdentity();
        GL11.glShadeModel(GL11.GL_SMOOTH);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glDepthFunc(GL11.GL_LESS);
        GL11.glDepthMask(true);
        GL11.glEnable(GL11.GL_ALPHA_TEST);
        GL11.glEnable(GL11.GL_NORMALIZE);

        GL11.glEnable(GL11.GL_CULL_FACE);
        GL11.glCullFace(GL11.GL_BACK);
    }

    public void setLight(float[] lightAmbient, float[] lightDiffuse, float[] lightPosition, int materialShinyness) {
        GL11.glEnable(GL11.GL_LIGHTING);

        ByteBuffer temp = ByteBuffer.allocateDirect(16);
        temp.order(ByteOrder.nativeOrder());

        GL11.glMaterial(GL11.GL_FRONT, GL11.GL_DIFFUSE, (FloatBuffer) temp.asFloatBuffer().put(lightDiffuse).flip());
        GL11.glMaterialf(GL11.GL_FRONT, GL11.GL_SHININESS, (int) materialShinyness);


        GL11.glLight(GL11.GL_LIGHT1, GL11.GL_POSITION, (FloatBuffer) temp.asFloatBuffer().put(lightPosition).flip());         // Position The Light
        GL11.glLight(GL11.GL_LIGHT1, GL11.GL_DIFFUSE, (FloatBuffer) temp.asFloatBuffer().put(lightDiffuse).flip());
        GL11.glLight(GL11.GL_LIGHT1, GL11.GL_AMBIENT, (FloatBuffer) temp.asFloatBuffer().put(lightAmbient).flip());

        GL11.glEnable(GL11.GL_LIGHT1);

        GL11.glLightModeli(GL12.GL_LIGHT_MODEL_COLOR_CONTROL, GL12.GL_SEPARATE_SPECULAR_COLOR);
        GL11.glColorMaterial(GL11.GL_FRONT_AND_BACK, GL11.GL_AMBIENT_AND_DIFFUSE);

    }

    public void seFog(float[] fogColour, float density, float start, float end) {
        GL11.glEnable(GL11.GL_FOG);
        GL11.glFogi(GL11.GL_FOG_MODE, GL11.GL_LINEAR);
        ByteBuffer temp = ByteBuffer.allocateDirect(16);
        temp.order(ByteOrder.nativeOrder());
        GL11.glFog(GL11.GL_FOG_COLOR, (FloatBuffer) temp.asFloatBuffer().put(fogColour).flip());
        GL11.glFogf(GL11.GL_FOG_DENSITY, density);
        GL11.glHint(GL11.GL_FOG_HINT, GL11.GL_NICEST);
        GL11.glFogf(GL11.GL_FOG_START, start);
        GL11.glFogf(GL11.GL_FOG_END, end);
    }

    public UnicodeFont loadFont(String fontPath, Color color) {
        UnicodeFont font = null;
        try {
            font = new UnicodeFont(fontPath, 25, true, false);
            font.addAsciiGlyphs();
            font.addGlyphs(400, 600);
            font.getEffects().add(new ColorEffect(color));
            font.loadGlyphs();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return font;
    }
}