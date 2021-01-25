package starfleet;

import battlecode.common.*;

public class Slanderer {
  static RobotController rc;
  static Team team;

  static void init(RobotController rc) {
    Slanderer.rc = rc;
    team = rc.getTeam();
  }

  static void lattice() throws GameActionException {

    MapLocation loc = rc.getLocation();
    if ((loc.x + loc.y) % 2 == 0 && !Model.isNextToEC(loc))
      return;
    else {
      if (Robot.target == null || (Robot.target.x + Robot.target.y) % 2 != 0) {
        // Find the nearest unoccupied lattice point
        int lx = loc.x, ly = loc.y;
        int mind = 10000;
        for (int dx = -1; dx <= 1; dx++) {
          for (int dy = -1; dy <= 1; dy++) {
            int x = (lx + dx), y = (ly + dy) + (lx + dx) % 2 - (ly + dy) % 2;
            MapLocation candidate = new MapLocation(x, y);
            rc.setIndicatorDot(candidate, 255, 255, 255);
            if (rc.canSenseLocation(candidate) && !rc.isLocationOccupied(candidate) && !Model.isNextToEC(candidate)) {
              int dist2 = candidate.distanceSquaredTo(loc);
              if (dist2 < mind) {
                Robot.target = candidate;
                mind = dist2;
              }
            }
          }
        }
      }
      // Robot.target = stayByPols(Robot.target);

      Robot.targetMove(true);
    }
  }

  static MapLocation stayByPols(MapLocation from) {
    if (from == null)
      return null;

    if (Robot.seen_pol) {
      return new MapLocation(Math.max(Robot.pol_min_x, Math.min(Robot.pol_max_x, from.x)),
          Math.max(Robot.pol_min_y, Math.min(Robot.pol_max_y, from.y)));
    } else {
      return from;
    }
  }

  static void turn() throws GameActionException {
    MapLocation loc = rc.getLocation();

    // Run away from enemy muckrakers
    if (Robot.closest_muck != null && Robot.closest_muck.isWithinDistanceSquared(loc, 81)) {
      rc.setIndicatorLine(rc.getLocation(), Robot.closest_muck, 100, 0, 150);
      Direction dir = Robot.closest_muck.directionTo(loc);
      Robot.target = rc.adjacentLocation(dir).add(dir);
      Robot.target = stayByPols(Robot.target);
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
      lattice();
      // // Slanderers circle the EC, if they have one
      // Robot.circleEC(4);
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
        if (!Robot.targetMove())
          lattice();
      } else {
        // Hopefully we find an EC soon!
        if (Robot.targetMove(true))
          lattice();
      }
    }
  }
}
