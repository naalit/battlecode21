package testplayer;

import battlecode.common.*;
import static battlecode.common.RobotType.*;

class Slanderer implements Unit {
  Robot rc;

  public Slanderer(Robot rc) {
    this.rc = rc;
    rc.retarget();
  }

  public void turn() {
    rc.update();

    Team enemy = rc.rc.getTeam().opponent();
    for (RobotInfo i : rc.nearby) {
      if (i.team == enemy && i.type == MUCKRAKER) {
        // Run away from muckrakers
        rc.runFrom(i.location);
      }
    }

    // Go to the target and stay there, hiding
    rc.target_move(false);

    rc.finish();
  }
}
