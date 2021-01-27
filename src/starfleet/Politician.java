package starfleet;

import java.util.ArrayList;
import battlecode.common.*;
import static battlecode.common.RobotType.*;

public class Politician {
  static RobotController rc;
  static Team team;
  static ArrayList<RobotInfo> affected = new ArrayList<>();
  static int nfpols = 0;

  static void init(RobotController rc) {
    Politician.rc = rc;
    team = rc.getTeam();
  }

  static void lattice() throws GameActionException {

    MapLocation loc = rc.getLocation();
    if (loc.x % 3 == 0 && loc.y % 3 == 0 && !Model.isNextToEC(loc))
      return;
    else {
      if (Robot.target == null || Robot.target.x % 3 != 0 || Robot.target.y % 3 != 0) {
        // Find the nearest unoccupied lattice point
        int lx = loc.x / 3, ly = loc.y / 3;
        int mind = 10000;
        for (int dx = -1; dx <= 1; dx++) {
          for (int dy = -1; dy <= 1; dy++) {
            int x = (lx + dx) * 3, y = (ly + dy) * 3;
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

      Robot.targetMove(true);
    }
  }

  static boolean tradeEmpower() throws GameActionException {

    MapLocation loc = rc.getLocation();

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
    nfpols = -Robot.friendly_slanderers.size();
    affected.clear();
    for (RobotInfo i : Robot.nearby) {
      int dist2 = i.location.distanceSquaredTo(loc);

      if (i.team == team && i.type == POLITICIAN) {
        nfpols++;
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

    // An extra unit is probably worth about one turn's income, or at least that's
    // what wololo does and it seems to work.
    // Except, wololo doesn't spawn many slanderers and we do, so our price gets too
    // high.
    // We reduce it with a logistic function.
    double N = 20;
    int unit_price = (int) (N * Math.atan(Model.home_ec_income / N));
    // So this is how much it will cost to explode
    int emp_cost = -rc.getConviction() - unit_price;

    // Calculate total damage done for each possible radius
    int useful_conv = rc.getConviction() - 10;
    double buff = rc.getEmpowerFactor(team, 0);
    int[] totals = { emp_cost, emp_cost, emp_cost, emp_cost };
    boolean[] hits_enemy = { false, false, false, false };
    for (RobotInfo i : affected) {
      int dist2 = i.location.distanceSquaredTo(loc);

      // TODO keep or remove?
      // Make sure pols don't crowd enemy ECs and not do anything
      if (i.team != team && i.type == ENLIGHTENMENT_CENTER && dist2 <= radii[0])
        totals[0] += -emp_cost;

      for (int r = 0; r < rcounts.length; r++) {
        int r2 = radii[r];
        if (dist2 <= r2) {
          hits_enemy[r] |= i.team != team;
          double conv = ((double) useful_conv) / rcounts[r];
          if (i.type == ENLIGHTENMENT_CENTER) {
            if (i.team == team) {
              // No buff to friendly ECs
              totals[r] += (int) conv;
            } else {
              // Complicated stuff
              double conv_to_convert = i.conviction / buff;
              if (conv <= conv_to_convert) {
                totals[r] += (int) (conv * buff);
              } else {
                // Enough to convert
                totals[r] += i.conviction;
                // And we converted it, so that's two units we've gained relative to the
                // opponent
                totals[r] += unit_price * 2;
                // Plus the rest, non-buffed
                totals[r] += (int) (conv - conv_to_convert);
              }
            }
          } else {
            // Full buff
            if (i.team == team) {
              // Our units can only heal upto their cap
              totals[r] += Math.min(conv * buff, i.influence - i.conviction);
            } else {
              // If it's a politician, we can kill it then heal it; otherwise, we can just
              // kill it. Also, sometimes conviction is 0, but the unit can still be killed.
              // Last, we want it to be worth it for 17hp pols to kill 2 1hp muckrakers.
              if (i.type == POLITICIAN && conv * buff > i.conviction) {
                // We can transfer up to this much to make it a friendly pol of max health
                totals[r] += Math.min(conv * buff, i.influence + i.conviction);
                // And we gain two units relative to the opponent
                totals[r] += unit_price * 2;
              } else if (conv * buff > i.conviction) {
                // We can kill it (note the >, because it needs to go negative to die)
                totals[r] += Math.min(conv * buff, i.conviction);
                // We gained one unit relative to the opponent, since they lost one
                totals[r] += unit_price;

                // Either the muckraker is in kill distance now, or will be in after moving once
                // and can move
                if (i.type == MUCKRAKER && i.location.equals(Robot.muckraker) && slanderer != null
                    && (Robot.muckraker.isWithinDistanceSquared(slanderer.location, MUCKRAKER.actionRadiusSquared)
                        || (mucknext.isWithinDistanceSquared(slanderer.location, MUCKRAKER.actionRadiusSquared)
                            && !rc.isLocationOccupied(mucknext)))) {
                  // We'll have saved the slanderer
                  totals[r] += slanderer.getInfluence() + unit_price;
                }
              } else {
                // We can't kill it, just do damage
                totals[r] += conv * buff;
              }
            }
          }
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
      if ((hits_enemy[r] || damage > rc.getConviction()) && damage > max_damage) {
        max_r2 = r2;
        max_damage = damage;
      }
    }

    // Empower if we'd be using at least 2/3 of our conviction, not counting tax;
    // or if there are a ton of politicians around and this one is at or below
    // average, so its life isn't worth much
    if (max_damage > 0 && rc.canEmpower(max_r2)) {
      rc.empower(max_r2);
      return true;
    } else {
      return false;
    }
  }

  static RobotInfo slanderer = null;
  static MapLocation mucknext = null;

  static boolean protectSlanderers() throws GameActionException {
    MapLocation loc = rc.getLocation();

    // We also pick a slanderer to protect.
    // If we've found a muckraker, we pick the closest one to the muckraker, since
    // it's in the most danger.
    // Otherwise, we pick the closest one to us.
    slanderer = null;
    MapLocation rloc = (Robot.muckraker == null) ? loc : Robot.muckraker;
    for (RobotInfo i : Robot.friendly_slanderers) {
      if (slanderer == null || i.location.distanceSquaredTo(rloc) < slanderer.location.distanceSquaredTo(rloc))
        slanderer = i;
    }

    // If the muckraker will be able to kill the slanderer after moving, and we can
    // kill the muckraker, and the slanderer has higher influence than we have
    // conviction (which is usually true), then we need to kill the muckraker so it
    // doesn't kill the slanderer.
    mucknext = Robot.muckraker == null || slanderer == null ? null
        : Robot.muckraker.add(Robot.muckraker.directionTo(slanderer.location));

    // If there's a muckraker threatening a slanderer, but we're not going to blow
    // it up right now, we should at least try to block it
    if (Robot.muckraker != null && slanderer != null
    // If there are a lot of politicians and we're fairly far away, it's another
    // politician's job
        && (nfpols < 10 || loc.isWithinDistanceSquared(Robot.muckraker, 36))) {
      Robot.target = mucknext;
      return true;
    }

    return false;
  }

  static void attackOrExplore() throws GameActionException {
    // Find the closest neutral EC
    MapLocation target_ec = null;
    int ec_dist2 = 1000000;
    for (ECInfo eec : Model.neutral_ecs) {
      // Only go for it if we'll do at least half damage
      if (eec.influence >= (rc.getConviction() - 10) * 2)
        continue;
      int dist2 = eec.loc.distanceSquaredTo(rc.getLocation());// ec != null ? ec : rc.getLocation());
      if (target_ec == null || dist2 < ec_dist2) {
        target_ec = eec.loc;
        ec_dist2 = dist2;
      }
    }
    // If there aren't any neutral ECs, and we have >= 100 conv, target an enemy EC
    if (target_ec == null && rc.getConviction() - 10 >= 100) {
      for (ECInfo eec : Model.enemy_ecs) {
        int dist2 = eec.loc.distanceSquaredTo(rc.getLocation());// ec != null ? ec : rc.getLocation());
        if (target_ec == null || dist2 < ec_dist2) {
          target_ec = eec.loc;
          ec_dist2 = dist2;
        }
      }
    }

    if (target_ec != null)
      Robot.target = target_ec;
    else if (Model.last_converted != null)
      Robot.target = Model.last_converted;
    Robot.targetMove(true);
  }

  /**
   * If there are enemies in range, empowers, otherwise looks for enemies to kill.
   * Used when in cleanup mode.
   */
  static void cleanup() {
    MapLocation loc = rc.getLocation();
    RobotInfo strongest = null;
    final int[] radii = { 1, 2, 4, 9 }; // These are squared
    int[] nhits = { 0, 0, 0, 0 };
    int[] total = { 0, 0, 0, 0 };
    for (RobotInfo i : Robot.nearby) {
      int dist2 = i.location.distanceSquaredTo(loc);
      if (dist2 <= POLITICIAN.actionRadiusSquared) {
        for (int r = 0; r < radii.length; r++) {
          int r2 = radii[r];
          if (dist2 <= r2) {
            if (i.team != team)
              nhits[r]++;
            total[r]++;
          }
        }
      } else if (i.team != team && (strongest == null || i.conviction > strongest.conviction)) {
        strongest = i;
      }
    }

    double best_ratio = 0;
    int best_r = 1;
    for (int r = 0; r < radii.length; r++) {
      double ratio = ((double) nhits[r]) / total[r];
      if (ratio > best_ratio) {
        best_ratio = ratio;
        best_r = radii[r];
      }
    }

    try {
      if (best_ratio > 0) {
        rc.empower(best_r);
      } else if (strongest != null) {
        Robot.target = strongest.location;
        Robot.targetMove(true);
      } else {
        Robot.targetMove(true);
      }
    } catch (GameActionException e) {
      // This just means we have cooldown turns left
    }
  }

  /**
   * Runs most of a politician's turn, after updating Robot. Figures out whether
   * to empower and where to move.
   */
  static void turn() throws GameActionException {
    if (Model.cleanup_mode) {
      rc.setIndicatorDot(rc.getLocation(), 0, 255, 255);
    }

    if (protectSlanderers() || tradeEmpower())
    boolean moving = protectSlanderers();
    if (tradeEmpower())
      return;
    else if (moving && Robot.targetMove(false))
      return;

    if (Model.cleanup_mode) {
      cleanup();
      return;
    }

    if (Robot.rpriority && Robot.reinforce_loc != null
        && Robot.reinforce_loc.isWithinDistanceSquared(rc.getLocation(), 49)) {
      Robot.target = Robot.reinforce_loc;
      Robot.targetMove(true);
    }

    if (rc.getConviction() < 100 || rc.getConviction() % 2 == 1)
      lattice();
    else
      attackOrExplore();
  }
}
