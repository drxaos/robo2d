package robo2d.game.impl;

import com.robotech.military.api.Player;
import robo2d.game.box2d.Box;
import robo2d.game.box2d.Physical;
import robo2d.game.box2d.PlayerBox;
import straightedge.geom.KPoint;

import java.awt.geom.Point2D;

public class PlayerImpl implements Player, Physical {
    String name;
    PlayerBox box;
    float initAngle;
    String notebookDir;

    Enterable entered;

    public PlayerImpl(String name, KPoint position, float angle) {
        this.name = name;
        this.initAngle = angle;
        box = new PlayerBox(position);
    }

    public String getNotebookDir() {
        return notebookDir;
    }

    public void setNotebookDir(String notebookDir) {
        this.notebookDir = notebookDir;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public Box getBox() {
        return box;
    }

    public float getInitAngle() {
        return initAngle;
    }

    public void enter(Enterable enterable) {
        if (entered == null) {
            entered = enterable;
            entered.enter(this);

            box.body.setActive(false);
        }
    }

    public Enterable getEntered() {
        return entered;
    }

    public void exit() {
        if (entered != null) {
            Point2D exitPos = entered.exit();
            entered = null;

            box.resetPosition((float) exitPos.getX(), (float) exitPos.getY());
            box.body.setActive(true);
        }
    }
}
