package robo2d.locations;

import robo2d.game.Game;
import robo2d.game.impl.*;
import robo2d.testbed.RobotTest;
import straightedge.geom.KPoint;

public class ConcurrentTest extends RobotTest {

    @Override
    public Game createGame() {
        Game game = new Game(getWorld(), getDebugDraw());

        PlayerImpl player1 = new PlayerImpl("player1", new KPoint(10, 10), 0);
        player1.setNotebookDir("locations/concurrentTest/notebook");
        game.addPlayer(player1);

        for (int i = 0; i < 10; i++) {
            String model = (i % 2 == 0 ? "MR" : "AGR");
            RobotImpl robot = new RobotImpl(model + "_CONC_" + i, game, player1, new KPoint(100 * Math.random(), 100 * Math.random()), Math.PI * 4 * Math.random());
            ChassisImpl chassis = new ChassisImpl(model.equals("MR") ? 300d : 350d);
            RadarImpl radar = new RadarImpl(game, 100d);
            ComputerImpl computer = new ComputerImpl(true);
            robot.addEquipment(chassis);
            robot.addEquipment(radar);
            robot.addEquipment(computer);
            robot.charge(4000);
            game.addRobot(robot);
        }

        game.addGps();

        return game;
    }

    public static void main(String[] args) {
        new ConcurrentTest().start();
    }
}
