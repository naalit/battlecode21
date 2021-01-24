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
    MapLocation loc = rc.getLocation();

    // Run away from enemy muckrakers
    if (Robot.closest_muck != null) {
      rc.setIndicatorLine(rc.getLocation(), Robot.closest_muck, 100, 0, 150);
      Direction dir = Robot.closest_muck.directionTo(loc);
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
      // Priority number 1 is to not go towards the muck, even if we might be in a
      // suboptimal location
      while (Robot.target.isWithinDistanceSquared(Robot.closest_muck, RobotType.MUCKRAKER.actionRadiusSquared)) {
        Robot.target = Robot.target.add(Robot.closest_muck.directionTo(Robot.target));
      }
      Robot.targetMove(false);
    } else if (Robot.ec != null) {
      // Slanderers circle the EC, if they have one
      Robot.circleEC(4);
    } else {
      MapLocation closest = null;
      int closest_d = 100000;
      for (ECInfo i : Model.friendly_ecs) {
        if (i.loc != null) {
          int dist2 = i.loc.distanceSquaredTo(loc);
          if (dist2 < closest_d) {
            closest = i.loc;
            closest_d = dist2;
          }
        }
      }
      if (closest != null) {
        Robot.target = closest;
        Robot.targetMove();
      } else {
        // Hopefully we find an EC soon!
        Robot.targetMove(true);
      }
    }
  }
}
