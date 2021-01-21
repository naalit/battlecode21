package redarmy;

import battlecode.common.*;

public class Slanderer {
  static RobotController rc;
  static Team team;

  static void init(RobotController rc) {
    Slanderer.rc = rc;
    team = rc.getTeam();
  }

  static void turn() throws GameActionException {
    // Run away from enemy muckrakers
    if (Robot.muckraker != null
        || (Robot.reinforce_loc != null && Robot.reinforce_loc.isWithinDistanceSquared(rc.getLocation(), 100))) {
      MapLocation run_from = Robot.muckraker != null ? Robot.muckraker : Robot.reinforce_loc;
      Direction dir = run_from.directionTo(rc.getLocation());
      Robot.target = rc.adjacentLocation(dir).add(dir);
      Robot.targetMove(false);
      Robot.reinforce_loc = null;
    } else if (Robot.ec != null) {
      // Slanderers circle the EC, if they have one
      Robot.circleEC(3);
    } else {
      Robot.targetMove(true);
    }
  }
}
