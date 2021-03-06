package robo2d.game;

import com.robotech.military.api.Radar;
import org.apache.commons.io.FileUtils;
import org.apache.maven.cli.MavenCli;
import org.jbox2d.callbacks.DebugDraw;
import org.jbox2d.callbacks.RayCastCallback;
import org.jbox2d.common.Color3f;
import org.jbox2d.common.Vec2;
import org.jbox2d.dynamics.Body;
import org.jbox2d.dynamics.Fixture;
import org.jbox2d.dynamics.FixtureDef;
import org.jbox2d.dynamics.World;
import robo2d.game.box2d.Physical;
import robo2d.game.box2d.RobotBox;
import robo2d.game.impl.*;
import straightedge.geom.KPoint;

import java.awt.geom.Point2D;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Game {
    protected List<Physical> physicals = new ArrayList<Physical>();
    protected List<RobotImpl> robots = new ArrayList<RobotImpl>();
    protected PlayerImpl player;
    protected BaseImpl base;

    final Map<SatelliteScanner, SatelliteScanner.Request> satelliteRequests = new HashMap<SatelliteScanner, SatelliteScanner.Request>();

    Object stepSync = new Object();

    protected World worldBox;
    protected DebugDraw debugDraw;
    public static final Color3f GRAY = new Color3f(0.3f, 0.3f, 0.3f);

    Integer satelliteResolution = null;
    Integer satelliteLag = null;
    boolean gps = false;

    public Game(World worldBox, DebugDraw debugDraw) {
        this.worldBox = worldBox;
        this.debugDraw = debugDraw;
    }

    public void addRobot(RobotImpl robot) {
        physicals.add(robot);
        robots.add(robot);
    }

    public void addPlayer(PlayerImpl player) {
        physicals.add(player);
        this.player = player;
    }

    public void addBase(BaseImpl base) {
        physicals.add(base);
        this.base = base;
    }

    public PlayerImpl getPlayer() {
        return player;
    }

    public List<RobotImpl> getRobots() {
        return robots;
    }

    public List<Physical> getPhysicals() {
        return physicals;
    }

    public void addSatellite(int resolution, int lag) {
        this.satelliteResolution = resolution;
        this.satelliteLag = lag;
    }

    public Integer getSatelliteResolution() {
        return satelliteResolution;
    }

    public void addGps() {
        this.gps = true;
    }

    public boolean hasGps() {
        return gps;
    }

    public Object stepSync() {
        return stepSync;
    }

    public void addWall(WallImpl wall) {
        physicals.add(wall);
    }

    public void start() {
        for (Physical physical : physicals) {
            Body body = worldBox.createBody(physical.getBox().bodyDef);
            for (FixtureDef fixtureDef : physical.getBox().fixtureDefs) {
                body.createFixture(fixtureDef);
            }
            physical.getBox().body = body;
        }


        // Setup notebook

        File template = new File("templates/notebook");
        File notebook = new File("notebook");
        try {
            FileUtils.deleteDirectory(notebook);
        } catch (IOException e) {
            e.printStackTrace();
        }
        File playerNotebook = new File(getPlayer().getNotebookDir());
        try {
            FileUtils.copyDirectory(playerNotebook, notebook);
        } catch (IOException e) {
            e.printStackTrace();
        }
        try {
            FileUtils.copyDirectory(template, notebook);
        } catch (IOException e) {
            e.printStackTrace();
        }
        try {
            MavenCli cli = new MavenCli();
            cli.doMain(new String[]{"clean", "compile"}, notebook.getAbsolutePath(), System.out, System.out);
        } catch (Exception e) {
            e.printStackTrace();
        }

        // Start programs

        for (RobotImpl robot : robots) {
            ComputerImpl computer = robot.getComputer();
            if (computer != null && computer.isInitWorking()) {
                computer.startProgram();
            }
        }
    }

    public void stop() {
        for (RobotImpl robot : robots) {
            ComputerImpl computer = robot.getComputer();
            if (computer != null) {
                computer.stopProgram();
            }
        }
    }

    public void satelliteRequest(SatelliteScanner scanner, Vec2 request, double accuracy, int resolution) {
        synchronized (satelliteRequests) {
            satelliteRequests.put(scanner, new SatelliteScanner.Request(request, accuracy, resolution, getTime() + satelliteLag));
        }
    }

    public void satelliteProcess() {
        synchronized (satelliteRequests) {
            Map<SatelliteScanner, SatelliteScanner.Request> tmp = new HashMap<SatelliteScanner, SatelliteScanner.Request>(satelliteRequests);
            satelliteRequests.clear();
            for (Map.Entry<SatelliteScanner, SatelliteScanner.Request> e : tmp.entrySet()) {
                if (e.getValue().waitUntil > getTime()) {
                    satelliteRequests.put(e.getKey(), e.getValue());
                } else {
                    e.getKey().setSatResponse(satelliteScan(e.getValue().accuracy, e.getValue().point, e.getKey().getRobot(), e.getValue().resolution));
                }
            }
        }
    }

    public Long getTime() {
        return System.currentTimeMillis();
    }

    public void beforeStep() {
        satelliteProcess();
        managePrograms();
        applyEffects();
        sync();
    }

    public void afterStep() {
        debug();
    }

    public void managePrograms() {
        for (RobotImpl robot : robots) {
            ComputerImpl computer = robot.getComputer();
            if (computer != null) {
                if (robot.getEnergy() < 0.01) {
                    computer.stopProgram();
                }
            }
        }
    }

    public void applyEffects() {
        for (RobotImpl robot : robots) {
            robot.applyEffects();
        }
    }

    private Radar.Type recognizeType(Physical physical, RobotImpl forRobot) {
        if (physical instanceof WallImpl) {
            return Radar.Type.WALL;
        } else if (physical instanceof RobotImpl) {
            if (physical == forRobot) {
                return Radar.Type.ME;
            } else if (((RobotImpl) physical).getOwner() == forRobot.getOwner()) {
                return Radar.Type.MATE_BOT;
            } else {
                return Radar.Type.ENEMY_BOT;
            }
        } else {
            return Radar.Type.UNKNOWN;
        }
    }

    public Radar.Type resolvePoint(double x, double y, RobotImpl forRobot) {
        for (Physical physical : physicals) {
            if (physical.getBox().hasPoint(new Vec2((float) x, (float) y))) {
                return recognizeType(physical, forRobot);
            }
        }
        return Radar.Type.EMPTY;
    }

    public Radar.SatelliteScanData satelliteScan(double accuracy, Vec2 center, RobotImpl robot, int satelliteResolution) {
        double centerX = Math.round(center.x / accuracy) * accuracy;
        double centerY = Math.round(center.y / accuracy) * accuracy;
        Radar.Type[][] map = new Radar.Type[satelliteResolution * 2 + 1][satelliteResolution * 2 + 1];
        for (int x = -satelliteResolution; x <= satelliteResolution; x++) {
            for (int y = -satelliteResolution; y <= satelliteResolution; y++) {
                if (Math.sqrt(x * x + y * y) > satelliteResolution) {
                    map[x + satelliteResolution][y + satelliteResolution] = Radar.Type.UNKNOWN;
                } else {
                    double resolveX = centerX + ((Math.random() - 0.5) * 0.4 + x) * accuracy;
                    double resolveY = centerY + ((Math.random() - 0.5) * 0.4 + y) * accuracy;
                    map[x + satelliteResolution][y + satelliteResolution] = resolvePoint(resolveX, resolveY, robot);
                }
            }
        }
        return new Radar.SatelliteScanData(map, accuracy, satelliteResolution, satelliteResolution);
    }

    public BaseImpl getBase() {
        return base;
    }

    private static class RayCastClosestCallback implements RayCastCallback {
        Body fromBody;
        Vec2 m_point;
        Body body;

        private RayCastClosestCallback(Body fromBody) {
            this.fromBody = fromBody;
        }

        public void init() {
            body = null;
            m_point = null;
        }

        public float reportFixture(Fixture fixture, Vec2 point,
                                   Vec2 normal, float fraction) {
            body = fixture.getBody();
            m_point = point;
            return fraction;
        }

        public Body getBody() {
            return body;
        }

        public Vec2 getPoint() {
            return m_point;
        }
    }

    public Radar.LocatorScanData resolveDirection(double angle, double scanDistance, RobotImpl forRobot) {
        RayCastClosestCallback callback = new RayCastClosestCallback(forRobot.getBox().body);
        worldBox.raycast(callback,
                forRobot.getBox().getPositionVec2(),
                forRobot.getBox().getPositionVec2().add(
                        new Vec2((float) (Math.cos(angle) * scanDistance),
                                (float) (Math.sin(angle) * scanDistance))
                ));
        Body body = callback.getBody();
        Vec2 point = callback.getPoint();
        Physical physical = null;
        for (Physical p : physicals) {
            if (p.getBox().body == body) {
                physical = p;
                break;
            }
        }
        if (physical == null || point == null) {
            return new Radar.LocatorScanData(Radar.Type.EMPTY, scanDistance, angle);
        }
        Point2D forRobotPosition = forRobot.getBox().getPosition();
        return new Radar.LocatorScanData(
                recognizeType(physical, forRobot),
                KPoint.distance(
                        new KPoint(point.x, point.y),
                        new KPoint(forRobotPosition.getX(), forRobotPosition.getY())
                ),
                angle
        );
    }

    public void sync() {
        for (RobotImpl robot : robots) {
            robot.sync();
        }
    }

    public void debug() {
        Vec2 res = new Vec2();
        for (RobotImpl robot : robots) {
            String msg = robot.getDebug();
            if (msg != null && !msg.isEmpty()) {
                debugDraw.getWorldToScreenToOut(robot.getBox().getPositionVec2().add(new Vec2(1.5f * (float) RobotBox.SIZE, 1.5f * (float) RobotBox.SIZE)), res);
                debugDraw.drawString(res, msg, Color3f.RED);
            }
            debugDraw.getWorldToScreenToOut(robot.getBox().getPositionVec2().add(new Vec2(1.5f * (float) RobotBox.SIZE, -1.5f * (float) RobotBox.SIZE)), res);
            debugDraw.drawString(res, String.format("E:%.2f", robot.getEnergy()) + ", " + (robot.getComputer() != null && robot.getComputer().isRunning() ? "ON" : "OFF"), Color3f.BLUE);
            Point2D point = robot.getDebugPoint();
            if (point != null) {
                debugDraw.drawPoint(new Vec2((float) point.getX(), (float) point.getY()), 3, Color3f.WHITE);
                debugDraw.drawSegment(new Vec2((float) point.getX(), (float) point.getY()), robot.getBox().getPositionVec2(), GRAY);
            }
        }
    }
}
