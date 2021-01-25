package starfleet;

import battlecode.common.*;
import static battlecode.common.RobotType.*;

public class Muckraker {
  static RobotController rc;
  static Team team;

  static void init(RobotController rc) {
    Muckraker.rc = rc;
    team = rc.getTeam();
  }

  static void turn() throws GameActionException {
    int tx = 0, ty = 0;
    int tt = 0;
    for (RobotInfo r : Robot.nearby) {
      if (r.team != team) {
        tx += r.location.x;
        ty += r.location.y;
        tt++;
      }
    }
    if (tt >= 3) {
      MapLocation m = new MapLocation(tx / tt, ty / tt);
      rc.setIndicatorDot(m, 255, 0, 0);
      Robot.queue.add(new Flag(Flag.Type.EnemyCenter, m));
    }

    // Target the slanderer with the highest influence (which generates the most
    // money) in range
    Team enemy = team.opponent();
    RobotInfo strongest_close = null;
    RobotInfo strongest_far = null;
    for (RobotInfo i : Robot.nearby) {
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
    if (strongest_close != null && rc.canExpose(strongest_close.location)) {
      rc.expose(strongest_close.location);
    } else if (strongest_far != null) {
      // Move towards any slanderers that aren't in range yet
      Robot.target = strongest_far.location;
    }

    if (Robot.target == null && Robot.ec != null && Robot.ec.isWithinDistanceSquared(rc.getLocation(), 30)) {
      Direction dir = Robot.ec.directionTo(rc.getLocation());
      Robot.target = Robot.ec.translate(dir.dx * 100, dir.dy * 100);
    } else if (Robot.target == null || rc.getLocation().equals(Robot.target)) {
      MapLocation target_ec = null;
      int ec_dist2 = 100000;
      // Go towards the closest enemy EC
      if (target_ec == null) {
        for (ECInfo eec : Model.enemy_ecs) {
          int dist2 = eec.loc.distanceSquaredTo(rc.getLocation());
          if (target_ec == null || dist2 < ec_dist2) {
            target_ec = eec.loc;
            ec_dist2 = dist2;
          }
        }
      }

      if (target_ec != null && ec_dist2 > 49)
        Robot.target = target_ec;
    }

    if (rc.getConviction() < 20 || rc.getLocation().equals(Robot.target) || Robot.target == null
        || !Model.isOnMap(Robot.target)) {
      // Wander around constantly
      Robot.targetMove(true);
    } else {
      Robot.targetMove(false);
    }
  }
}
