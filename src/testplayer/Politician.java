package testplayer;

import battlecode.common.*;
import static battlecode.common.RobotType.*;
import java.util.ArrayList;

class Politician implements Unit {
  Robot rc;

  public Politician(Robot rc) {
    this.rc = rc;
    rc.retarget();
  }

  // Don't reallocate this each turn
  ArrayList<RobotInfo> enemies = new ArrayList<RobotInfo>();

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
    enemies.clear();
    // radii^2: 1, 2, 4, 9
    int[] rcounts = { 0, 0, 0, 0 };
    int[] damages = { 0, 0, 0, 0 };
    boolean lower_hp_friendly_pol_in_range = false;
    MapLocation loc = rc.getLocation();
    for (RobotInfo i : rc.nearby) {
      if (i.location.isWithinDistanceSquared(loc, POLITICIAN.actionRadiusSquared)) {
        if (i.team != team)
          enemies.add(i);
        else if (i.type == RobotType.POLITICIAN && i.conviction < rc.rc.getConviction())
          lower_hp_friendly_pol_in_range = true;

        for (int r = 0; r < rcounts.length; r++) {
          int r2 = (r <= 1) ? r + 1 : r * r;
          if (i.location.isWithinDistanceSquared(loc, r2))
            rcounts[r]++;
        }
      } else if (i.team != team && (i.type == RobotType.MUCKRAKER || i.conviction > 10)
          && (strongest == null || i.conviction > strongest.conviction))
        strongest = i;
    }
    for (RobotInfo i : enemies) {
      for (int r = 0; r < rcounts.length; r++) {
        int r2 = (r <= 1) ? r + 1 : r * r;
        if (rcounts[r] > 0) {
          int damage_per = (rc.rc.getConviction() - 10) / rcounts[r];
          if (i.location.isWithinDistanceSquared(loc, r2)) {
            // If we can kill an enemy EC, that's worth it even if it's not a great deal
            if (i.type == RobotType.ENLIGHTENMENT_CENTER && i.conviction <= damage_per)
              damages[r] += 200;
            else
              damages[r] += Math.min(i.conviction, damage_per);
            // TODO: should we consider healing or damage after conversion for politicians?
          }
        }
      }
    }
    int max_r2 = 9;
    int max_damage = 0;
    for (int r = 0; r < rcounts.length; r++) {
      int r2 = (r <= 1) ? r + 1 : r * r;
      int damage = damages[r];
      if (damage > max_damage) {
        max_r2 = r2;
        max_damage = damage;
      }
    }
    // Empower if we'd be using at least one third of our conviction
    if (rc.rc.getConviction() <= 3 * max_damage) {
      rc.empower(max_r2);
    } else {
      if (strongest != null) {
        // Move towards the unit with the highest conviction to get it in empowering
        // range
        rc.target = strongest.location;
      } else if (!enemies.isEmpty() && !lower_hp_friendly_pol_in_range) {
        // Without this, enemy 1inf muckrakers could wander around and kill all our
        // slanderers without our pols doing anything
        // This way, it's the pol with the lowest conviction's job to kill small units
        rc.empower(max_r2);
      } else if (!enemies.isEmpty() && rc.getTurn() - rc.start_turn > 500) {
        // If we've been wandering around for a while and haven't found anything, just
        // give up and empower
        // This is important so we kill ALL the enemy's units
        rc.empower(max_r2);
      }
    }

    // Wander around constantly
    rc.target_move(true);

    rc.finish();
  }
}
