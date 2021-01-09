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

    // Target the slanderer with the highest influence (which generates the most
    // money) in range
    Team enemy = rc.rc.getTeam().opponent();
    RobotInfo strongest_close = null;
    RobotInfo strongest_far = null;
    for (RobotInfo i : rc.nearby) {
      if (i.team == enemy && i.type == SLANDERER) {
        if (i.location.isWithinDistanceSquared(rc.getLocation(), MUCKRAKER.actionRadiusSquared)) {
          if (strongest_close == null || i.influence > strongest_close.influence)
            strongest_close = i;
        } else {
          if (strongest_far == null || i.influence > strongest_far.influence)
            strongest_far = i;
        }
      }
    }
    if (strongest_close != null) {
      rc.expose(strongest_close.location);
    } else if (strongest_far != null) {
      // Move towards any slanderers that aren't in range yet
      rc.target = strongest_far.location;
    }

    // Wander around constantly
    rc.target_move(true);

    rc.finish();
  }
}
