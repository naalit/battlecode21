package redarmy;

import battlecode.common.*;
import static battlecode.common.RobotType.*;

public class ECenter {
  static RobotController rc;

  /**
   * Returns the next direction where we can spawn something.
   */
  static Direction openDirection() throws GameActionException {
    for (Direction dir : Direction.values()) {
      MapLocation l = rc.getLocation().add(dir);
      if (rc.onTheMap(l) && !rc.isLocationOccupied(l))
        return dir;
    }

    return Direction.NORTH;
  }

  static void turn() throws GameActionException {
    Comms.update();

    // Spawn whatever there's less of near the EC, pols or slanderers
    int npols = 0, nslans = 0;
    for (RobotInfo i : Comms.nearby) {
      if (i.team == rc.getTeam()) {
        if (i.type == POLITICIAN)
          npols++;
        else if (i.type == SLANDERER)
          nslans++;
      }
    }

    RobotType type = npols < nslans ? POLITICIAN : SLANDERER;
    Direction dir = openDirection();
    int inf = npols < nslans ? 50 : 107;
    if (rc.canBuildRobot(type, dir, inf)) {
      rc.buildRobot(type, dir, inf);
    }

    Comms.finish();
  }

  public static void run(RobotController rc) {
    Comms.start(rc);
    ECenter.rc = rc;

    while (true) {
      try {
        turn();

        Clock.yield();
      } catch (GameActionException e) {
        e.printStackTrace();
      }
    }
  }
}
