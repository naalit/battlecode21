package redarmy;

import java.util.ArrayList;
import battlecode.common.*;
import static battlecode.common.RobotType.*;

public class Robot {
  static RobotController rc;
  static ArrayList<RobotInfo> enemies = new ArrayList<RobotInfo>();
  static Team team;
  static MapLocation ec;
  static boolean moved_yet = false;
  static boolean exploring = false;
  static MapLocation target = null;

  static boolean targetMove() {
    try {

      MapLocation loc = rc.getLocation();

      if (target == null || loc.equals(target)) {
        if (exploring)
          target = retarget();
        else
          return false;
      }

      rc.setIndicatorLine(loc, target, 0, 255, 0);

      Direction to_dir = loc.directionTo(target);
      Direction dir = to_dir;

      // Try going around obstacles, first left, then right
      MapLocation[] options = { loc.add(dir), loc.add(dir.rotateLeft()), loc.add(dir.rotateRight()) };
      MapLocation best = null;
      double best_pass = -1;
      for (MapLocation i : options) {
        // Don't occupy EC spawning spaces
        if ((ec != null && i.isWithinDistanceSquared(ec, 2)) || !rc.canMove(loc.directionTo(i)))
          continue;

        if (i.equals(target)) {
          best = i;
          best_pass = 100;
        } else if (rc.sensePassability(i) > best_pass) {
          best = i;
          best_pass = rc.sensePassability(i);
        }
      }
      if (best == null) {
        // If we can't move, and it's not because of cooldown, it must be blocked
        if (exploring && rc.isReady())
          target = retarget();
        return false;
      }
      dir = loc.directionTo(best);

      Comms.setNeutralFlag();
      rc.move(dir);
      return true;

    } catch (GameActionException e) {
      // This can happen if the turn changes in the middle of moving
      return false;
    }
  }

  static void polTurn() throws GameActionException {
    // Decide whether it's worth it to empower
    RobotInfo strongest = null;
    enemies.clear();
    // radii^2: 1, 2, 4, 9
    int[] rcounts = { 0, 0, 0, 0 };
    int[] damages = { 0, 0, 0, 0 };
    int nfpols = 0;
    boolean lower_hp_friendly_pol_in_range = false;
    boolean enemy_muckraker_in_range = false;
    MapLocation loc = rc.getLocation();

    // First pass: accumulate total number of affected units for each radius
    for (RobotInfo i : Comms.nearby) {
      Team team2 = i.team;
      RobotType ty2 = i.type;
      MapLocation loc2 = i.location;
      int dist2 = loc2.distanceSquaredTo(loc);
      if (team2 == team && ty2 == POLITICIAN) {
        if (!lower_hp_friendly_pol_in_range && i.conviction < rc.getConviction()
            && dist2 < POLITICIAN.actionRadiusSquared)
          lower_hp_friendly_pol_in_range = true;
        nfpols++;
      }
      if (dist2 < POLITICIAN.actionRadiusSquared) {
        if (team2 != team) {
          if (ty2 == MUCKRAKER || ty2 == ENLIGHTENMENT_CENTER)
            enemy_muckraker_in_range = true;
          enemies.add(i);
        }

        for (int r = 0; r < rcounts.length; r++) {
          int r2 = (r <= 1) ? r + 1 : r * r;
          if (dist2 < r2)
            rcounts[r]++;
        }
      } else if (team2 != team && (ty2 == RobotType.MUCKRAKER || i.conviction > 10)
          && (strongest == null || i.conviction > strongest.conviction))
        strongest = i;
    }

    // Second pass: accumulate total damage for each radius
    for (RobotInfo i : enemies) {
      for (int r = 0; r < rcounts.length; r++) {
        int r2 = (r <= 1) ? r + 1 : r * r;
        if (rcounts[r] > 0) {
          int damage_per = (rc.getConviction() - 10) / rcounts[r];
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

    // Find the radius with the highest damage
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
    if (rc.getConviction() <= 3 * max_damage && rc.canEmpower(max_r2)) {
      rc.empower(max_r2);
    } else {
      if (strongest != null) {
        // Move towards the unit with the highest conviction to get it in empowering
        // range
        target = strongest.location;
        if (targetMove())
          return;
      }
      if (enemy_muckraker_in_range && !lower_hp_friendly_pol_in_range && rc.canEmpower(max_r2)) {
        // Without this, enemy 1inf muckrakers could wander around and kill all our
        // slanderers without our pols doing anything
        // This way, it's the pol with the lowest conviction's job to kill small units
        rc.empower(max_r2);
      }
    }

    // Now moving. If we're far enough away from the EC, or we have lots of units
    // already, we're in exploring mode
    exploring = (ec == null || !ec.isWithinDistanceSquared(loc, 100) || nfpols > 24);
    if (exploring) {
      // Target enemy ECs if possible
      MapLocation target_ec = null;
      int ec_dist2 = 1000000;
      for (MapLocation ec : Comms.enemy_ecs) {
        int dist2 = ec.distanceSquaredTo(rc.getLocation());
        if (target_ec == null || dist2 < ec_dist2) {
          target_ec = ec;
          ec_dist2 = dist2;
        }
      }
      if (target_ec != null)
        target = target_ec;

      targetMove();
    } else if (ec != null) {

      // Otherwise, we're in defense mode, so circle the EC
      // This code goes in a spiral, pointing away from the EC then turning 90 degrees
      // to the left each turn
      double a = Math.toRadians(90);
      double sina = Math.sin(a);
      double cosa = Math.cos(a);

      double x = (rc.getLocation().x - ec.x) / 64.0;
      double y = (rc.getLocation().y - ec.y) / 64.0;
      double r = Math.sqrt(x * x + y * y);
      double ux = x / r;
      double uy = y / r;
      double tx = cosa * ux - sina * uy;
      double ty = sina * ux + cosa * uy;
      int dx = (int) (tx * 3);
      int dy = (int) (ty * 3);
      target = rc.getLocation().translate(dx, dy);
      targetMove();
    }
  }

  static int retarget_acc;

  // Switch to a new random target
  static MapLocation retarget() {
    int width = 64;
    int height = 64;
    MapLocation min = rc.getLocation().translate(-width / 2, -height / 2);
    int wxh = width * height;
    // This is a RNG technique I found online called Linear Congruential Generator
    // This should be a random 12 bits
    retarget_acc = (1231 * retarget_acc + 3171) % wxh;
    // Split into two, each in 0..N
    int x = retarget_acc % width;
    int y = retarget_acc / width;
    // Now switch to absolute coordinates
    return min.translate(x, y);
  }

  static void turn() throws GameActionException {
    Comms.update();
    ec = Comms.ec;

    // Move out of the spawning space we're occupying as soon as possible
    if (!moved_yet && ec != null) {
      Direction dir = ec.directionTo(rc.getLocation());
      target = rc.getLocation().add(dir).add(dir);
      if (targetMove())
        moved_yet = true;
      return;
    }

    switch (rc.getType()) {
    case SLANDERER:
      // Slanderers always circle the EC
      if (ec != null) {
        // This code goes in a spiral, pointing away from the EC then turning 110
        // degrees (a tighter spiral than the pols) to the left each turn
        double a = Math.toRadians(110);
        double sina = Math.sin(a);
        double cosa = Math.cos(a);

        double x = (rc.getLocation().x - ec.x) / 64.0;
        double y = (rc.getLocation().y - ec.y) / 64.0;
        double r = Math.sqrt(x * x + y * y);
        double ux = x / r;
        double uy = y / r;
        double tx = cosa * ux - sina * uy;
        double ty = sina * ux + cosa * uy;
        int dx = (int) (tx * 3);
        int dy = (int) (ty * 3);
        target = rc.getLocation().translate(dx, dy);
      }
      targetMove();
      break;
    case POLITICIAN: {
      polTurn();
      break;
    }
    default:
      System.out.println("NO MOVEMENT CODE FOR " + rc.getType());
      rc.resign();
    }

    Comms.finish();
  }

  public static void run(RobotController rc) {
    Comms.start(rc);
    Robot.rc = rc;
    team = rc.getTeam();
    retarget_acc = rc.getID();
    try {
      Comms.setNeutralFlag();
    } catch (GameActionException e) {
      e.printStackTrace();
    }

    while (true) {
      try {
        turn();

        Clock.yield();
      } catch (GameActionException e) {
        e.printStackTrace();
      }
    }
  }
}
