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
    if (rc.enemy_ecs.length > 0) {
      rc.target = rc.enemy_ecs[0];
    }

    Team enemy = rc.rc.getTeam().opponent();
    int num_enemies = 0;
    for (RobotInfo i : rc.nearby) {
      if (i.team == enemy && i.location.isWithinDistanceSquared(rc.getLocation(), POLITICIAN.actionRadiusSquared)) {
        num_enemies++;
      }
    }
    // Don't empower if it wouldn't do anything
    if (rc.rc.getConviction() > 10 && num_enemies > 0) {
      rc.empower(POLITICIAN.actionRadiusSquared);
    }

    // Wander around constantly
    rc.target_move(true);

    rc.finish();
  }
}
