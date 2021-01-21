package redarmy;

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
    }

    // Wander around constantly
    Robot.targetMove(true);
  }
}
