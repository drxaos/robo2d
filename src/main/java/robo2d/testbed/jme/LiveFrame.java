package robo2d.testbed.jme;

import com.jme3.app.SimpleApplication;
import com.jme3.asset.plugins.FileLocator;
import com.jme3.collision.CollisionResult;
import com.jme3.collision.CollisionResults;
import com.jme3.light.AmbientLight;
import com.jme3.light.DirectionalLight;
import com.jme3.material.Material;
import com.jme3.math.*;
import com.jme3.renderer.RenderManager;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Node;
import com.jme3.shadow.DirectionalLightShadowRenderer;
import com.jme3.shadow.EdgeFilteringMode;
import com.jme3.system.AppSettings;
import com.jme3.system.Natives;
import com.jme3.terrain.geomipmap.TerrainLodControl;
import com.jme3.terrain.geomipmap.TerrainQuad;
import com.jme3.terrain.geomipmap.lodcalc.DistanceLodCalculator;
import com.jme3.terrain.heightmap.AbstractHeightMap;
import com.jme3.terrain.heightmap.ImageBasedHeightMap;
import com.jme3.texture.Texture;
import robo2d.game.Game;
import robo2d.game.box2d.Physical;
import robo2d.game.impl.RobotImpl;
import robo2d.game.impl.WallImpl;
import slick2d.NativeLoader;
import straightedge.geom.KPoint;

import java.awt.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class LiveFrame extends SimpleApplication {

    static {
        NativeLoader.load("build/natives");
        Natives.setExtractionDir("build/natives");
    }

    public static LiveFrame create(Game game) {
        final LiveFrame app = new LiveFrame(game);
        AppSettings appSettings = new AppSettings(true);
        appSettings.setFullscreen(false);
        appSettings.setVSync(true);
        appSettings.setResolution(1024, 768);
        appSettings.setDepthBits(24);
        appSettings.setBitsPerPixel(24);
        app.setSettings(appSettings);
        app.setShowSettings(false);
        app.setPauseOnLostFocus(false);
        Thread thread = new Thread() {
            @Override
            public void run() {
                app.start();
            }
        };
        thread.setDaemon(true);
        thread.start();
//        app.startCanvas();
//        JmeCanvasContext ctx = (JmeCanvasContext) app.getContext();
//        Dimension dim = new Dimension(640, 480);
//        ctx.getCanvas().setPreferredSize(dim);
//        app.canvas = ctx.getCanvas();
        return app;
    }


    Canvas canvas;

    public Canvas getCanvas() {
        return canvas;
    }

    Game game;

    TerrainQuad terrain;
    RobotModel robotModel;

    Map<RobotImpl, Node> robotMap = new HashMap<RobotImpl, Node>();
    java.util.List<WallImpl> walls = new ArrayList<WallImpl>();

    public LiveFrame(Game game) {
        this.game = game;
    }

    @Override
    public void simpleInitApp() {
        assetManager.registerLocator("./", FileLocator.class);

        rootNode.detachAllChildren();
        flyCam.setMoveSpeed(10);
        flyCam.setDragToRotate(true);
        getCamera().setLocation(new Vector3f(-15, 25, -15));
        getCamera().lookAt(new Vector3f(0, 0, 0), new Vector3f(0, 1, 0));

        rootNode.setShadowMode(RenderQueue.ShadowMode.CastAndReceive);
        rootNode.addLight(createAmbient());
        DirectionalLight sun = createSun();
        rootNode.addLight(sun);

        terrain = createGround();
        rootNode.attachChild(terrain);

        robotModel = new RobotModel(assetManager);
        for (Physical physical : game.getPhysicals()) {
            if (physical instanceof RobotImpl) {
                Node robotLive = robotModel.createRobot();
                robotMap.put((RobotImpl) physical, robotLive);
                rootNode.attachChild(robotLive);
            } else if (physical instanceof WallImpl) {
                walls.add((WallImpl) physical);
            }
        }

        getCamera().setLocation(getTerrainPoint(-15, -15).add(0, 25, 0));

        /* Drop shadows */
        final int SHADOWMAP_SIZE = 2048;
        DirectionalLightShadowRenderer dlsr = new DirectionalLightShadowRenderer(assetManager, SHADOWMAP_SIZE, 3);
        dlsr.setEdgeFilteringMode(EdgeFilteringMode.PCFPOISSON);
        dlsr.setEdgesThickness(2);
        dlsr.setShadowIntensity(0.7f);
        dlsr.setLight(sun);
        viewPort.addProcessor(dlsr);
    }

    private Vector3f getTerrainPoint(float x, float z) {
        CollisionResults results = new CollisionResults();
        Ray ray = new Ray();
        Vector3f pos = new Vector3f(x, 1000, z);
        Vector3f dir = new Vector3f(x, 999, z);
        dir.subtractLocal(pos).normalizeLocal();
        ray.setOrigin(pos);
        ray.setDirection(dir);
        terrain.collideWith(ray, results);
        CollisionResult result = results.getClosestCollision();
        return result.getContactPoint();
    }

    @Override
    public void simpleUpdate(float tpf) {
        for (Map.Entry<RobotImpl, Node> e : robotMap.entrySet()) {
            KPoint point = e.getKey().getBox().getPosition();
            float x = (float) point.getY();
            float z = (float) point.getX();
            float angle = (float) e.getKey().getBox().getAngle() + FastMath.PI;
            Node node = e.getValue();

            Quaternion yaw = new Quaternion();
            yaw.fromAngleAxis(angle, new Vector3f(0, 1, 0));

            float radius = (robotModel.getFinalLength() + robotModel.getFinalWidth()) / 4;

            Vector3f x1, x2, z1, z2;
            x1 = getTerrainPoint(x - radius, z);
            x2 = getTerrainPoint(x + radius, z);
            z1 = getTerrainPoint(x, z - radius);
            z2 = getTerrainPoint(x, z + radius);

            Vector3f zAxis = z2.subtract(z1);
            Vector3f xAxis = x2.subtract(x1);
            Vector3f yAxis = new Vector3f(0, 1, 0);
            Quaternion quat = new Quaternion().fromAxes(
                    xAxis,
                    yAxis,
                    zAxis
            );
            node.setLocalRotation(quat.mult(yaw));
            node.setLocalTranslation(x, Math.min(x1.y + x2.y, z1.y + z2.y) / 2, z);
        }

        Vector3f cam = getCamera().getLocation();
        game.getPlayer().getBox().setPosition(cam.z, cam.x);
        game.stepSync();
        KPoint newPos = game.getPlayer().getBox().getPosition();
        cam.setY(getTerrainPoint((float) newPos.y, (float) newPos.x).y + 1.3f);
        cam.setZ((float) newPos.x);
        cam.setX((float) newPos.y);
        getCamera().setLocation(cam);
    }

    @Override
    public void simpleRender(RenderManager rm) {
        //TODO: add render code
    }

    private TerrainQuad createGround() {
        Material mat = new Material(assetManager,
                "Common/MatDefs/Terrain/Terrain.j3md");
        mat.setBoolean("useTriPlanarMapping", false);
        mat.setTexture("Alpha", assetManager.loadTexture("models/ground/alphamap.png"));

        Texture grass = assetManager.loadTexture("models/ground/grass.jpg");
        grass.setWrap(Texture.WrapMode.Repeat);
        mat.setTexture("Tex1", grass);
        mat.setFloat("Tex1Scale", 64);

        Texture dirt = assetManager.loadTexture("models/ground/dirt.jpg");
        dirt.setWrap(Texture.WrapMode.Repeat);
        mat.setTexture("Tex2", dirt);
        mat.setFloat("Tex2Scale", 16);

        Texture rock = assetManager.loadTexture("models/ground/ground.jpg");
        rock.setWrap(Texture.WrapMode.Repeat);
        mat.setTexture("Tex3", rock);
        mat.setFloat("Tex3Scale", 450);

        Texture heightMapImage = assetManager.loadTexture("models/ground/ground2048.png");
        AbstractHeightMap heightmap = new ImageBasedHeightMap(heightMapImage.getImage(), 0.3f);
        heightmap.load();

        TerrainQuad terrain = new TerrainQuad("ground", 65, 2049, heightmap.getHeightMap());
        terrain.setShadowMode(RenderQueue.ShadowMode.Receive);
        TerrainLodControl control = new TerrainLodControl(terrain, getCamera());
        control.setLodCalculator(new DistanceLodCalculator(65, 2.7f)); // patch size, and a multiplier
        terrain.addControl(control);
        terrain.setMaterial(mat);

        return terrain;
    }

    private DirectionalLight createSun() {
        DirectionalLight sun = new DirectionalLight();
        sun.setColor(ColorRGBA.White.mult(1.5f));
        sun.setDirection(new Vector3f(0.3f, -1f, 0.7f).normalizeLocal());
        return sun;
    }

    private AmbientLight createAmbient() {
        AmbientLight al = new AmbientLight();
        al.setColor(ColorRGBA.White.mult(0.2f));
        return al;
    }
}