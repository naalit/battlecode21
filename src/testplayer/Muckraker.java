package testplayer;

import battlecode.common.*;
import static battlecode.common.RobotType.*;

class Muckraker implements Unit {
  Robot rc;

  public Muckraker(Robot rc) {
    this.rc = rc;
    rc.retarget();
  }

  public void turn() {
    rc.update();

    Team enemy = rc.rc.getTeam().opponent();
    for (RobotInfo i : rc.nearby) {
      if (i.team == enemy && i.type == SLANDERER) {
        if (i.location.isWithinDistanceSquared(rc.getLocation(), MUCKRAKER.actionRadiusSquared)) {
          rc.expose(i.location);
        } else {
          // Move towards any slanderers that aren't in range yet
          rc.target = i.location;
        }
      }
    }

    // Wander around constantly
    rc.target_move(true);

    rc.finish();
  }
}
