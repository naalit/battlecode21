package redarmy;

import java.util.ArrayList;
import battlecode.common.*;
import static battlecode.common.RobotType.*;

public class Robot {
  static RobotController rc;
  static Team team;
  static MapLocation ec;
  static boolean moved_yet = false;
  static MapLocation target = null;

  static boolean targetMove() {
    return targetMove(false);
  }

  static boolean targetMove(boolean exploring) {
    try {

      MapLocation loc = rc.getLocation();

      if (target == null || loc.equals(target)) {
        if (exploring)
          target = retarget();
        else
          return false;
      }

      rc.setIndicatorLine(loc, target, 0, 255, 0);

      Direction dir = loc.directionTo(target);

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
      // It's better to move in a bad direction than not move at all
      if (best == null && rc.isReady()) {
        if (exploring) {
          target = retarget();
          return false;
        } else {
          MapLocation[] others = { loc.add(dir.rotateLeft().rotateLeft()), loc.add(dir.rotateRight().rotateRight()) };
          for (MapLocation i : others) {
            // Don't occupy EC spawning spaces
            if ((ec != null && i.isWithinDistanceSquared(ec, 2)) || !rc.canMove(loc.directionTo(i)))
              continue;

            if (rc.sensePassability(i) > best_pass) {
              best = i;
              best_pass = rc.sensePassability(i);
            }
          }
        }
      }
      if (best == null)
        return false;
      dir = loc.directionTo(best);

      Comms.setNeutralFlag();
      rc.move(dir);
      return true;

    } catch (GameActionException e) {
      // This can happen if the turn changes in the middle of moving
      return false;
    }
  }

  static void circleEC(double dist) {
    int r2 = (int) (dist * dist);
    Direction closest = null;
    int closest_d = 10000;
    for (Direction dir : Direction.values()) {
      if (rc.canMove(dir)) {
        MapLocation l = rc.getLocation().add(dir);
        int d = Math.abs(l.distanceSquaredTo(ec) - r2);
        if (d < closest_d) {
          closest = dir;
          closest_d = d;
        }
      }
    }
    if (closest != null)
      target = rc.getLocation().add(closest);
    targetMove();
  }

  static ArrayList<RobotInfo> affected = new ArrayList<>();

  /**
   * Runs most of a politician's turn, after updating comms. Figures out whether
   * to empower and where to move.
   */
  static void polTurn() throws GameActionException {
    MapLocation loc = rc.getLocation();
    Team team = rc.getTeam();

    // A politician has 2 goals:
    // 1. Deal damage to enemy units, especially ECs
    // 2. Protect friendly slanderers, and our ECs

    // When it's worth it to empower depends on how much conviction this unit has.
    // If it's <=10, empowering won't do anything, so we should just block things
    // and wait for others to possibly heal us to >10 conviction.

    // We'll start by looking at what's around us.
    // We want to figure out how many units total are in each possible empowering
    // radius, which damage would be divided between.
    final int[] radii = { 1, 2, 4, 9 }; // These are squared
    int[] rcounts = { 0, 0, 0, 0 };
    // We also keep track of how many politicians are near here, and their average
    // conviction
    // We'll see slanderers as politicians, so subtract those first
    int nfpols = -Comms.friendly_slanderers.size();
    int fpol_conv = -Comms.total_fslan_conv;
    affected.clear();
    // We also want to know where enemy muckrakers are, so we can protect slanderers
    // We just look at the closest muckraker
    RobotInfo muckraker = null;
    for (RobotInfo i : Comms.nearby) {
      int dist2 = i.location.distanceSquaredTo(loc);

      if (i.team != team && i.type == MUCKRAKER
          && (muckraker == null || dist2 < muckraker.location.distanceSquaredTo(loc)))
        muckraker = i;
      else if (i.team == team && i.type == POLITICIAN) {
        nfpols++;
        fpol_conv += i.conviction;
      }

      if (dist2 <= POLITICIAN.actionRadiusSquared) {
        // If it could recieve damage or healing from this politician, add it to
        // `affected` so we can process it later
        if (i.team != team || i.conviction < i.influence || i.type == ENLIGHTENMENT_CENTER)
          affected.add(i);

        for (int r = 0; r < rcounts.length; r++) {
          int r2 = radii[r];
          if (dist2 <= r2)
            rcounts[r]++;
        }
      }
    }

    // We also pick a slanderer to protect.
    // If we've found a muckraker, we pick the closest one to the muckraker, since
    // it's in the most danger.
    // Otherwise, we pick the closest one to us.
    RobotInfo slanderer = null;
    MapLocation rloc = (muckraker == null) ? loc : muckraker.location;
    for (RobotInfo i : Comms.friendly_slanderers) {
      if (slanderer == null || i.location.distanceSquaredTo(rloc) < slanderer.location.distanceSquaredTo(rloc))
        slanderer = i;
    }

    // If the muckraker will be able to kill the slanderer after moving, and we can
    // kill the muckraker, and the slanderer has higher influence than we have
    // conviction (which is usually true), then we need to kill the muckraker so it
    // doesn't kill the slanderer.
    if (muckraker != null && slanderer != null
    // Either the muckraker is in kill distance now, or will be in after moving once
    // and can move
        && (muckraker.location.isWithinDistanceSquared(slanderer.location, MUCKRAKER.actionRadiusSquared)
            || (muckraker.location.add(muckraker.location.directionTo(slanderer.location))
                .isWithinDistanceSquared(slanderer.location, MUCKRAKER.actionRadiusSquared)
                && !rc.isLocationOccupied(muckraker.location.add(muckraker.location.directionTo(slanderer.location)))))
        // We're in kill distance of the muckraker now (radii[3] is the maximum radius
        // for empowering)
        && muckraker.location.isWithinDistanceSquared(loc, radii[3])
        // We can do damage
        && rc.getConviction() > 10
        // The slanderer is worth saving
        && slanderer.influence >= rc.getConviction()) {
      if (rc.canEmpower(radii[3])) {
        rc.empower(radii[3]);
        return;
      }
    }

    // If there's a muckraker threatening a slanderer, but we're not going to blow
    // it up right now, we should at least try to block it
    if (muckraker != null && slanderer != null
    // If there are a lot of politicians and we're fairly far away, it's another
    // politician's job
        && (nfpols < 10 || loc.isWithinDistanceSquared(muckraker.location, 18))) {
      // Aim for a point in between the muckraker and the slanderer
      // We want to keep the muckraker more than sqrt(12) ~= 3.5 squares away, so we
      // aim for three squares towards the muckraker from the slanderer

      // Start by creating a unit vector
      double x = muckraker.location.x - slanderer.location.x;
      double y = muckraker.location.y - slanderer.location.y;
      double r = Math.sqrt(x * x + y * y);
      x = x / r;
      y = y / r;

      // Then offset by three and add to the slanderer location, and move there
      x *= 3;
      y *= 3;
      target = slanderer.location.translate((int) x, (int) y);
      targetMove();
      return;
    }

    // Otherwise, we're not immediately worrying about protecting slanderers, so
    // figure out whether it's worth it to empower.

    // Calculate total damage done for each possible radius
    int useful_conv = rc.getConviction() - 10;
    int[] totals = { 0, 0, 0, 0 };
    boolean[] hits_enemy = { false, false, false, false };
    for (RobotInfo i : affected) {
      int dist2 = i.location.distanceSquaredTo(loc);

      // Make sure pols don't crowd enemy ECs and not do anything
      if (i.team != team && i.type == ENLIGHTENMENT_CENTER && dist2 <= radii[0])
        totals[0] += useful_conv / 2;

      for (int r = 0; r < rcounts.length; r++) {
        int r2 = radii[r];
        if (dist2 <= r2) {
          hits_enemy[r] |= i.team != team;
          // Sometimes units end up with 0 conviction, but still need to be hit
          // Also, we assume remainder damage (if rcounts[r] > useful_conv) is given to
          // everyone, not in the order it actually is, for efficiency
          totals[r] += Math.max(1, Math.min(useful_conv / rcounts[r],
              // We can contribute as much as we want to friendly ECs, and extra damage done
              // to enemy ECs rolls over into when it's friendly
              (i.type == ENLIGHTENMENT_CENTER) ? 10000 :
              // Our units can only heal upto their cap
                  (i.team == team ? i.influence - i.conviction : i.conviction)));
        }
      }
    }

    // Find the radius with the highest total damage, but only count when we do
    // damage to enemies
    // Otherwise we'd be net losing money unless we have a big muckraker buff
    int max_r2 = 9;
    int max_damage = 0;
    for (int r = 0; r < totals.length; r++) {
      int r2 = radii[r];
      int damage = totals[r];
      if (hits_enemy[r] && damage > max_damage) {
        max_r2 = r2;
        max_damage = damage;
      }
    }

    // Empower if we'd be using at least half of our conviction, not counting tax;
    // or if there are a ton of politicians around and this one is at or below
    // average,
    // so its life isn't worth much
    int avg_conv = nfpols == 0 ? 0 : fpol_conv / nfpols;
    if ((useful_conv <= 2 * max_damage || (max_damage > 0 && nfpols > 12 && rc.getConviction() <= avg_conv))
        && rc.canEmpower(max_r2)) {
      rc.empower(max_r2);
      return;
    }

    // Now we've decided we're not empowering, so we can move.
    // If there are slanderers nearby, we want to be able to protect them, so stay
    // close. Unless there are too many politicians already here.
    // We want to be about 2 units away.
    // So, for a target, we circle the EC 2 units farther out than the slanderer we
    // picked earlier.
    if (ec != null && slanderer != null && nfpols < 20 && (nfpols < 8 || rc.getConviction() < 100)) {
      double r = Math.sqrt(slanderer.location.distanceSquaredTo(Comms.ec));
      // Offset by 2.7 to make sure it rounds right
      circleEC(r + 2.7);
    } else {
      // If there aren't any slanderers nearby (or we just don't have an EC, which is
      // rare), we explore, targeting enemy ECs if available

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

      targetMove(true);
    }
  }

  static void slanTurn() throws GameActionException {
    if (ec != null) {
      // Slanderers circle the EC, if they have one
      circleEC(3);
    } else {
      targetMove(true);
    }
  }

  static void mukTurn() throws GameActionException {
    // Target the slanderer with the highest influence (which generates the most
    // money) in range
    Team enemy = rc.getTeam().opponent();
    RobotInfo strongest_close = null;
    RobotInfo strongest_far = null;
    for (RobotInfo i : Comms.nearby) {
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
    if (strongest_close != null) {
      rc.expose(strongest_close.location);
    } else if (strongest_far != null) {
      // Move towards any slanderers that aren't in range yet
      target = strongest_far.location;
    }

    // Wander around constantly
    targetMove(true);
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
      slanTurn();
      break;
    case POLITICIAN:
      polTurn();
      break;
    case MUCKRAKER:
      mukTurn();
      break;
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
