package starfleet;

import java.util.ArrayList;
import battlecode.common.*;
import static battlecode.common.RobotType.*;

public class Politician {
  static RobotController rc;
  static Team team;
  // static RobotInfo[] affected = new RobotInfo[128];
  // static int aff_len = 0;
  static int nfpols = 0;
  static boolean is_anti_buffraker = false;

  static void init(RobotController rc) {
    Politician.rc = rc;
    team = rc.getTeam();
    try {
      if (rc.getType() == POLITICIAN && rc.getInfluence() > 100 && rc.getInfluence() % 2 == 1) {
        for (Direction d : Direction.values()) {
          MapLocation l = rc.adjacentLocation(d);
          if (rc.canSenseLocation(l)) {
            RobotInfo r = rc.senseRobotAtLocation(l);
            if (r != null && r.team == team && r.type == ENLIGHTENMENT_CENTER) {
              is_anti_buffraker = true;
              break;
            }
          }
        }
      }
    } catch (GameActionException e) {
      e.printStackTrace();
    }
  }

  static void lattice() throws GameActionException {
    if (slanderer != null && Robot.ec != null) {
      // If there are slanderers nearby, we want to be able to protect them, so stay
      // close. Unless there are too many politicians already here.
      // We want to be about 2 units away.
      // So, for a target, we circle the EC 2 units farther out than the slanderer we
      // picked earlier.
      double r = Math.sqrt(slanderer.location.distanceSquaredTo(Robot.ec));
      // Offset by 2.7 to make sure it rounds right
      Robot.circleEC(r + 2.7);
      return;
    } else if (is_anti_buffraker)
      return;

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

    int NDIRS = 9;//Direction.cardinalDirections().length + 1;
    MapLocation[] locs = new MapLocation[NDIRS];
    {
      locs[0] = loc;
      int i = 1;
      for (Direction dir : ECenter.directions) {//Direction.cardinalDirections()) {
        MapLocation l = loc.add(dir);
        if (rc.onTheMap(l) && !rc.isLocationOccupied(l)) {
          locs[i++] = l;
        }
      }
      NDIRS = i;
    }

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
    int[][] rcounts = new int[NDIRS][4];
    // We also keep track of how many politicians are near here, and their average
    // conviction
    // We'll see slanderers as politicians, so subtract those first
    nfpols = -Robot.friendly_slanderers.size();
    // aff_len = 0;
    boolean any_enemy = false;

    // Anything in radius 20 might be in the action radius of one of the locations
    RobotInfo[] affected = rc.senseNearbyRobots(16);
    for (RobotInfo i : affected) {

      if (i.team == team && i.type == POLITICIAN) {
        nfpols++;
      } else if (!any_enemy && i.team != team) {
        any_enemy = true;
      }

      for (int li = 0; li < NDIRS; li++) {
        int dist2 = i.location.distanceSquaredTo(locs[li]);
        for (int r = 3; r >= 0; r--) {
          if (dist2 <= radii[r])
            rcounts[li][r]++;
          else
            break;
        }
      }
    }
    // We'll never empower if there aren't enemies around
    if (!any_enemy)
      return false;

    // An extra unit is probably worth about one turn's income, or at least that's
    // what wololo does and it seems to work.
    // Except, wololo doesn't spawn many slanderers and we do, so our price gets too
    // high.
    // We reduce it with a logistic function.
    double N = 20;
    int unit_price = (int) (N * Math.atan(Model.home_ec_income / N));
    // So this is how much it will cost to explode
    int emp_cost = rc.getConviction() + unit_price;

    // Calculate total damage done for each possible radius
    double useful_conv = rc.getConviction() - 10;
    double buff = rc.getEmpowerFactor(team, 0);
    int[][] totals = new int[NDIRS][4];
    for (int j = 0; j < affected.length; j++) {
      RobotInfo i = affected[j];
      rc.setIndicatorDot(i.location, 0, 0, 255);

      // TODO keep or remove?
      // Make sure pols don't crowd enemy ECs and not do anything
      if (i.team != team && i.type == ENLIGHTENMENT_CENTER && i.location.isWithinDistanceSquared(loc, radii[0]))
        totals[0][0] += emp_cost;


      if (i.type == ENLIGHTENMENT_CENTER) {
        if (i.team == team) {
          for (int li = 0; li < NDIRS; li++) {
            int dist2 = i.location.distanceSquaredTo(locs[li]);

            for (int r = 3; r >= 0; r--) {
              if (dist2 <= radii[r]) {
                double conv = ((double) useful_conv) / rcounts[li][r];

                // No buff to friendly ECs
                totals[li][r] += (int) conv;
              } else
                break;
            }
          }
        } else {
          // Complicated stuff
          double conv_to_convert = i.conviction / buff;
          for (int li = 0; li < NDIRS; li++) {
            int dist2 = i.location.distanceSquaredTo(locs[li]);

            for (int r = 3; r >= 0; r--) {
              if (dist2 <= radii[r]) {
                double conv = ((double) useful_conv) / rcounts[li][r];

                if (conv <= conv_to_convert) {
                  totals[li][r] += (int) (conv * buff);
                } else {
                  // Enough to convert
                  totals[li][r] += i.conviction
                      // And we converted it, so that's two units we've gained relative to the
                      // opponent
                      + unit_price * 2
                      // Plus the rest, non-buffed
                      + (int) (conv - conv_to_convert);
                }
              } else
                break;
            }
          }
        }
      } else {
        // Full buff
        if (i.team == team) {
          // Our units can only heal upto their cap, and for muckrakers that's 0.7inf
          int cap = i.type == MUCKRAKER ? (int) (i.influence * 0.7) : i.influence;
          if (i.conviction == cap)
            continue;
          for (int li = 0; li < NDIRS; li++) {
            int dist2 = i.location.distanceSquaredTo(locs[li]);
            int[] rc = rcounts[li];
            int[] tot = totals[li];

            for (int r = 3; r >= 0; r--) {
              if (dist2 <= radii[r]) {
                double conv = useful_conv / rc[r];

                tot[r] += Math.min(conv * buff, cap - i.conviction);
              } else
                break;
            }
          }
        } else {
          // If it's a politician, we can kill it then heal it; otherwise, we can just
          // kill it. Also, sometimes conviction is 0, but the unit can still be killed.
          if (i.type == POLITICIAN) {
            for (int li = 0; li < NDIRS; li++) {
              int dist2 = i.location.distanceSquaredTo(locs[li]);
              int[] rc = rcounts[li];
              int[] tot = totals[li];

              for (int r = 3; r >= 0; r--) {
                if (dist2 <= radii[r]) {
                  double conv = (useful_conv) / rc[r];

                  // We can transfer up to this much to make it a friendly pol of max health
                  tot[r] += Math.min(conv * buff, i.influence + i.conviction)
                      // And we gain two units relative to the opponent
                      + conv * buff > i.conviction + 1 ? unit_price * 2 : 0;
                } else
                  break;
              }
            }
          } else {
            for (int li = 0; li < NDIRS; li++) {
              int dist2 = i.location.distanceSquaredTo(locs[li]);
              int[] rcs = rcounts[li];
              int[] tot = totals[li];

              for (int r = 3; r >= 0; r--) {
                if (dist2 <= radii[r]) {
                  double conv = (useful_conv) / rcs[r];

                  // We can kill it (note the +1, because it needs to go negative to die)
                  tot[r] += Math.min(conv * buff, i.conviction);
                  if (conv * buff >= i.conviction + 1) {
                    // We gained one unit relative to the opponent, since they lost one
                    tot[r] += unit_price;

                    // Either the muckraker is in kill distance now, or will be in after moving once
                    // and can move
                    if (i.type == MUCKRAKER && i.location.equals(Robot.muckraker) && slanderer != null
                        && (Robot.muckraker.isWithinDistanceSquared(slanderer.location, MUCKRAKER.actionRadiusSquared)
                            || (mucknext.isWithinDistanceSquared(slanderer.location, MUCKRAKER.actionRadiusSquared)
                                && !rc.isLocationOccupied(mucknext)))) {
                      // We'll have saved the slanderer
                      tot[r] += slanderer.getInfluence() + unit_price;
                    }
                  }
                } else
                  break;
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
    int max_li = 0;
    int max_damage = 0;
    for (int li = 0; li < NDIRS; li++) {
      for (int r = 3; r >= 0; r--) {
        int r2 = radii[r];
        int damage = totals[li][r] - emp_cost;
        if (damage > max_damage) {
          max_li = li;
          max_r2 = r2;
          max_damage = damage;
        }
      }
    }

    // Empower if we'd be using at least 2/3 of our conviction, not counting tax;
    // or if there are a ton of politicians around and this one is at or below
    // average, so its life isn't worth much
    if (max_li == 0 && max_damage > 0 && rc.canEmpower(max_r2)) {
      rc.empower(max_r2);
      return true;
    } else if (max_li != 0 && max_damage > 0) {
      Robot.target = locs[max_li];
      Robot.targetMove(false);
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

    // If there's a muckraker threatening a slanderer, but we're not going to blow
    // it up right now, we should at least try to block it
    MapLocation mucknextA = null;
    MapLocation mucknextB = null;
    if (Robot.muckraker != null && slanderer != null) {
      Direction mdir = Robot.muckraker.directionTo(slanderer.location);
      mucknext = Robot.muckraker.add(mdir);
      mucknextA = Robot.muckraker.add(mdir.rotateLeft());
      mucknextB = Robot.muckraker.add(mdir.rotateRight());

      if (loc.equals(mucknext) || loc.equals(mucknextA) || loc.equals(mucknextB)) {
        Robot.target = null;
        return true;
      }

      if (rc.canSenseLocation(mucknext) && !rc.isLocationOccupied(mucknext)) {
        Robot.target = mucknext;
        return true;
      } else if (rc.canSenseLocation(mucknextA) && !rc.isLocationOccupied(mucknextA)) {
        Robot.target = mucknextA;
        return true;
      } else if (rc.canSenseLocation(mucknextB) && !rc.isLocationOccupied(mucknextB)) {
        Robot.target = mucknextB;
        return true;
      }
    }

    return false;
  }

  static void attackOrExplore() throws GameActionException {
    // Find the closest neutral EC
    MapLocation target_ec = null;
    int ec_dist2 = 1000000;
    for (ECInfo eec : Model.neutral_ecs) {
      // Only go for it if we'll kill it
      if (eec.influence >= (rc.getConviction() - 10))
        continue;
      int dist2 = eec.loc.distanceSquaredTo(rc.getLocation());
      if (target_ec == null || dist2 < ec_dist2) {
        target_ec = eec.loc;
        ec_dist2 = dist2;
      }
    }
    // If there aren't any neutral ECs, and we have >= 100 conv, target an enemy EC
    if (target_ec == null && rc.getConviction() - 10 >= 100) {
      for (ECInfo eec : Model.enemy_ecs) {
        int dist2 = eec.loc.distanceSquaredTo(rc.getLocation());
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
        for (int r = 3; r >= 0; r--) {
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
    for (int r = 3; r >= 0; r--) {
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
        && Robot.reinforce_loc.isWithinDistanceSquared(rc.getLocation(), 144)) {
      Robot.target = Robot.reinforce_loc;
      if (Robot.targetMove(false))
        return;
    }

    if (rc.getConviction() < 100 || is_anti_buffraker)
      lattice();
    else
      attackOrExplore();
  }
}
