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

  final static int[] pol_infs = { 25, 25, 50, 25, 25, 50, 25, 25, 200 };
  static int pol_inf_cursor = 0;

  static int influenceFor(RobotType type) {
    switch (type) {
    case MUCKRAKER:
      // There isn't much reason to spawn a muckraker with more than 1 influence,
      // since it can still expose just as well
      return 1;
    case SLANDERER: {
      // Since there's a floor function involved in the slanderer income function, we
      // only pick the lowest starting influence in each income bracket
      // Here, we use the maximum of 130, but we might want to go up to 207 or beyond
      int inf = rc.getInfluence();
      if (inf > 150)
        return 130;
      if (inf > 120)
        return 107;
      if (inf > 100)
        return 85;
      return 63;
    }
    case POLITICIAN: {
      int inf = pol_infs[pol_inf_cursor % pol_infs.length];
      if (rc.getInfluence() >= 2 * inf) {
        pol_inf_cursor++;
        return inf;
      } else {
        return 25;
      }
    }
    default:
      System.out.println("Not a spawnable type: " + type);
      return 0;
    }
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
    int inf = influenceFor(type);
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
