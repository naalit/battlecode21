package starfleet;

import battlecode.common.*;

public class Slanderer {
  static RobotController rc;
  static Team team;

  static void init(RobotController rc) {
    Slanderer.rc = rc;
    team = rc.getTeam();
  }

  static void turn() throws GameActionException {
    if (Robot.muckraker != null)
      Model.addMuck(Robot.muckraker);
    if (Robot.reinforce_loc != null) {
      Model.addMuck(Robot.reinforce_loc);
      Robot.reinforce_loc = null;
    }
    MapLocation muck = Model.updateMucks();
    MapLocation loc = rc.getLocation();

    // Run away from enemy muckrakers
    if (muck != null) {
      rc.setIndicatorLine(rc.getLocation(), muck, 100, 0, 150);
      Direction dir = muck.directionTo(loc);
      Robot.target = rc.adjacentLocation(dir).add(dir);
      if (Robot.seen_pol) {
        rc.setIndicatorLine(loc, new MapLocation(Robot.pol_min_x, Robot.pol_min_y), 255, 255, 255);
        rc.setIndicatorLine(loc, new MapLocation(Robot.pol_max_x, Robot.pol_max_y), 0, 0, 0);
        Robot.target = new MapLocation(Math.max(Robot.pol_min_x, Math.min(Robot.pol_max_x, Robot.target.x)),
            Math.max(Robot.pol_min_y, Math.min(Robot.pol_max_y, Robot.target.y)));
      }
      // Don't crowd the EC when running from muckrakers
      if (Robot.ec != null && Robot.target.isWithinDistanceSquared(Robot.ec, 9)) {
        Robot.target = Robot.target.add(Robot.ec.directionTo(Robot.target));
      }
      Robot.targetMove(false);
    } else if (Robot.ec != null) {
      // Slanderers circle the EC, if they have one
      Robot.circleEC(4);
    } else {
      Robot.targetMove(true);
    }
  }
}
