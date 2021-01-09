package testplayer;

import battlecode.common.*;
import static battlecode.common.RobotType.*;

class Slanderer implements Unit {
  Robot rc;

  public Slanderer(Robot rc) {
    this.rc = rc;
    rc.retarget();
  }

  public void turn() {
    rc.update();

    MapLocation loc = rc.getLocation();
    Team enemy = rc.rc.getTeam().opponent();
    for (RobotInfo i : rc.nearby) {
      if (i.team == enemy && i.type == MUCKRAKER) {
        if (i.location.isWithinDistanceSquared(loc, MUCKRAKER.actionRadiusSquared)) {
          rc.expose(i.location);
        } else {
          // Make sure we're moving away from the muckraker
          // This flips the target's components if they're pointing towards the muckraker
          // (i.e. target is closer to muckraker than we are)
          int x = rc.target.x;
          int y = rc.target.y;
          if (Math.abs(x - i.location.x) < Math.abs(loc.x - i.location.x)) {
            x = loc.x - (x - loc.x);
          }
          if (Math.abs(y - i.location.y) < Math.abs(loc.y - i.location.y)) {
            y = loc.y - (y - loc.y);
          }
          rc.target = new MapLocation(x, y);
        }
      }
    }

    // Go to the target and stay there, hiding
    rc.target_move(false);

    rc.finish();
  }
}
