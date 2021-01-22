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

    // Run away from enemy muckrakers
    if (muck != null) {
      rc.setIndicatorLine(rc.getLocation(), muck, 100, 0, 150);
      Direction dir = muck.directionTo(rc.getLocation());
      Robot.target = rc.adjacentLocation(dir).add(dir);
      Robot.targetMove(false);
    } else if (Robot.ec != null) {
      // Slanderers circle the EC, if they have one
      Robot.circleEC(3);
    } else {
      Robot.targetMove(true);
    }
  }
}
