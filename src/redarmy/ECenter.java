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

  static boolean is_enemy_nearby = false;
  static boolean is_muckraker_nearby = false;
  static int npols = 0;
  static int nslans = 0;
  static int nmuks = 0;

  static void update() {
    is_enemy_nearby = false;
    is_muckraker_nearby = false;
    npols = 0;
    nslans = 0;
    nmuks = 0;
    Team team = rc.getTeam();
    MapLocation loc = rc.getLocation();
    // A muckraker within this squared radius could instantly kill any slanderer we
    // spawn, so we shouldn't spawn slanderers
    int radius = 16;
    for (RobotInfo i : Comms.nearby) {
      if (i.team != team) {
        is_enemy_nearby = true;
        if (!is_muckraker_nearby && i.type == MUCKRAKER && i.location.isWithinDistanceSquared(loc, radius)) {
          is_muckraker_nearby = true;
        }
      } else {
        // Count the number of politicians and slanderers around
        if (i.type == POLITICIAN)
          npols++;
        else if (i.type == SLANDERER)
          nslans++;
        else if (i.type == MUCKRAKER)
          nmuks++;
      }
    }
  }

  /**
   * Since there's a floor function involved in the slanderer income function, we
   * only pick the lowest starting influence in each income bracket
   */
  final static int[] slanderer_infs = { 949, 902, 855, 810, 766, 724, 683, 643, 605, 568, 532, 497, 463, 431, 399, 368,
      339, 310, 282, 255, 228, 203, 178, 154, 130, 107 };// , 85, 63, 41, 21 };

  final static int[] pol_infs = { 25, 25, 50, 25, 25, 50, 25, 25, 200 };
  static int pol_inf_cursor = 0;

  static int influenceFor(RobotType type) {
    // Calculate the amount we're willing to spend this turn.
    // If there are enemies nearby, we don't want them to take our EC, so the amount
    // we want to keep in the EC is higher.
    int total = rc.getInfluence();
    int keep = is_enemy_nearby ? Math.min(total / 2, 500) : 40;
    int spend = total - keep;

    switch (type) {
    case MUCKRAKER:
      // There isn't much reason to spawn a muckraker with more than 1 influence,
      // since it can still expose just as well
      return 1;
    case SLANDERER: {
      for (int i : slanderer_infs) {
        if (spend >= i)
          return i;
      }
      // Returning 0 means canBuildRobot will always return false
      return 0;
    }
    case POLITICIAN: {
      if (npols > 10 && spend >= 20 && pol_inf_cursor % 2 == 0)
        return Math.min(spend, 200);
      else if (spend >= 25)
        return 25;
      else
        return 0;
    }
    default:
      System.out.println("Not a spawnable type: " + type);
      return 0;
    }
  }

  static void turn() throws GameActionException {
    Comms.update();
    update();

    RobotType type = nmuks == 0 && npols > 1 && nslans > 1 ? MUCKRAKER
        : (is_muckraker_nearby ? POLITICIAN : (npols < nslans ? POLITICIAN : SLANDERER));
    Direction dir = openDirection();
    int inf = influenceFor(type);
    if (rc.canBuildRobot(type, dir, inf)) {
      if (type == POLITICIAN)
        pol_inf_cursor += 1;
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
