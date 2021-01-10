package testplayer;

import battlecode.common.*;
import static battlecode.common.RobotType.*;

class Politician implements Unit {
  Robot rc;

  public Politician(Robot rc) {
    this.rc = rc;
    rc.retarget();
  }

  public void turn() {
    rc.update();

    // Target enemy ECs if possible
    MapLocation target_ec = null;
    int ec_dist2 = 1000000;
    for (TimeLoc l : rc.enemy_ecs) {
      MapLocation ec = l.loc;
      int dist2 = ec.distanceSquaredTo(rc.getLocation());
      if (target_ec == null || dist2 < ec_dist2) {
        target_ec = ec;
        ec_dist2 = dist2;
      }
    }
    if (target_ec != null)
      rc.target = target_ec;

    Team team = rc.rc.getTeam();
    RobotInfo strongest = null;
    int num_enemies = 0;
    for (RobotInfo i : rc.nearby) {
      if (i.team != team && i.location.isWithinDistanceSquared(rc.getLocation(), POLITICIAN.actionRadiusSquared)) {
        num_enemies++;
      } else if (i.team != team) {
        if (strongest == null || i.conviction > strongest.conviction)
          strongest = i;
      }
    }
    // Don't empower if it wouldn't do anything
    if (rc.rc.getConviction() > 10 && num_enemies > 0) {
      rc.empower(POLITICIAN.actionRadiusSquared);
    } else if (strongest != null) {
      // Move towards the unit with the highest conviction to get it in empowering
      // range
      rc.target = strongest.location;
    }

    // Wander around constantly
    rc.target_move(true);

    rc.finish();
  }
}
