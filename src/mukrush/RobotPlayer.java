package mukrush;

import java.util.ArrayList;
import battlecode.common.*;
import static battlecode.common.RobotType.*;

public strictfp class RobotPlayer {
  static RobotController rc;
  static MapLocation target;

  static boolean targetMove() {
    try {
      MapLocation loc = rc.getLocation();

      if (loc.equals(target)) {
        target = retarget();
      } else if (loc.isWithinDistanceSquared(target, rc.getType().sensorRadiusSquared) && (!rc.onTheMap(target)/* || rc.isLocationOccupied(target)*/)) {
        target = retarget();
      }

      rc.setIndicatorLine(loc, target, 0, 255, 0);

      Direction to_dir = loc.directionTo(target);
      Direction dir = to_dir;

      // Try going around obstacles, first left, then right
      MapLocation[] options = { loc.add(dir), loc.add(dir.rotateLeft()), loc.add(dir.rotateRight()) };
      MapLocation best = null;
      double best_pass = -1;
      for (MapLocation i : options) {
        if (!rc.canMove(loc.directionTo(i)))
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
        if (rc.isReady())
          target = retarget();
        return false;
      }
      dir = loc.directionTo(best);

      rc.move(dir);
      return true;

    } catch (GameActionException e) {
      System.out.println("Unreachable!");
      e.printStackTrace();
      return false;
    }
  }

  static int retarget_acc;

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



  static int last_votes;
  static boolean bid_last_round = false;
  // We start by bidding 1, and then if we lose, we increment our bid by 1
  static int current_bid = 1;

  static void doBid() throws GameActionException {
    // No reason to bid for votes past a majority
    // Also, if we bid too early our economy gets set up slower, and if we bid when
    // we have low conviction we never spawn slanderers
    if (last_votes > 750 || rc.getRoundNum() < 50 || rc.getConviction() < 200)
      return;
    // If our votes didn't change, we lost, and need to bid higher.
    boolean lost_last_round = bid_last_round && rc.getTeamVotes() == last_votes;
    last_votes = rc.getTeamVotes();
    if (lost_last_round) {
      current_bid += 1;
    }
    // We focus on destroying them instead of winning votes, so we only bid if it's
    // 10% or less of our influence
    if (current_bid <= rc.getInfluence() / 10) {
      bid_last_round = true;
      rc.bid(current_bid);
    } else {
      bid_last_round = false;
    }
  }

  static ArrayList<RobotInfo> enemies = new ArrayList<RobotInfo>();

  public static void run(RobotController rc) throws GameActionException {
    RobotPlayer.rc = rc;
    retarget_acc = rc.getID();

    int spawn_count = 0;
    target = retarget();

    while (true) {
      try {

        switch (rc.getType()) {

        case ENLIGHTENMENT_CENTER:
          for (Direction dir : Direction.values()) {
            MapLocation l = rc.getLocation().add(dir);
            if (rc.onTheMap(l) && !rc.isLocationOccupied(l)) {
              if (rc.getInfluence() > 100 && rc.getInfluence() < 50000000 && rc.getEmpowerFactor(rc.getTeam(), 11) > 1.5 && rc.canBuildRobot(POLITICIAN, dir, rc.getInfluence() - 10)) {
                rc.buildRobot(POLITICIAN, dir, rc.getInfluence() - 10);
              }
              if (spawn_count > 20 && rc.canBuildRobot(POLITICIAN, dir, rc.getInfluence() / 10)) {
                rc.buildRobot(POLITICIAN, dir, rc.getInfluence() / 10);
                spawn_count = 0;
              }
              if (rc.canBuildRobot(MUCKRAKER, dir, 1)) {
                rc.buildRobot(MUCKRAKER, dir, 1);
                spawn_count++;
              }
              break;
            }
          }
          doBid();
          break;

        case MUCKRAKER:
          {
              // Target the slanderer with the highest influence (which generates the most
              // money) in range
              Team enemy = rc.getTeam().opponent();
              RobotInfo strongest_close = null;
              RobotInfo strongest_far = null;
              for (RobotInfo i : rc.senseNearbyRobots()) {
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
          }
          targetMove();
          break;

        case POLITICIAN:
          {

              RobotInfo strongest = null;
              enemies.clear();
              // radii^2: 1, 2, 4, 9
              int[] rcounts = { 0, 0, 0, 0 };
              int[] damages = { 0, 0, 0, 0 };
              // boolean lower_hp_friendly_pol_in_range = false;
              // boolean enemy_muckraker_in_range = false;
              MapLocation loc = rc.getLocation();
              for (RobotInfo i : rc.senseNearbyRobots()) {
                int dist2 = i.location.distanceSquaredTo(loc);
                if (dist2 < POLITICIAN.actionRadiusSquared) {
                  if (i.team != rc.getTeam()) {
                    // if (i.type == MUCKRAKER)
                    //   enemy_muckraker_in_range = true;
                    enemies.add(i);
                  }
                  if (i.type == ENLIGHTENMENT_CENTER && rc.getEmpowerFactor(rc.getTeam(), 0) > 1.5 && rc.canEmpower(dist2))
                    rc.empower(dist2);
                   // else if (i.type == RobotType.POLITICIAN && i.conviction < rc.getConviction())
                   //  lower_hp_friendly_pol_in_range = true;

                  for (int r = 0; r < rcounts.length; r++) {
                    int r2 = (r <= 1) ? r + 1 : r * r;
                    if (i.location.isWithinDistanceSquared(loc, r2))
                      rcounts[r]++;
                  }
                } else if (i.team != rc.getTeam() && (i.type == RobotType.MUCKRAKER || i.conviction > 10)
                    && (strongest == null || i.conviction > strongest.conviction))
                  strongest = i;
              }
              for (RobotInfo i : enemies) {
                for (int r = 0; r < rcounts.length; r++) {
                  int r2 = (r <= 1) ? r + 1 : r * r;
                  if (rcounts[r] > 0) {
                    int damage_per = (int) (rc.getConviction() * rc.getEmpowerFactor(rc.getTeam(), 0) - 10) / rcounts[r];
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
              if (rc.getConviction() <= 3 * max_damage && rc.canEmpower(max_r2)) {
                rc.empower(max_r2);
              } else {
                if (strongest != null) {
                  // Move towards the unit with the highest conviction to get it in empowering
                  // range
                  target = strongest.location;
                }
                // else if (enemy_muckraker_in_range && !lower_hp_friendly_pol_in_range && rc.canEmpower(max_r2)) {
                //   // Without this, enemy 1inf muckrakers could wander around and kill all our
                //   // slanderers without our pols doing anything
                //   // This way, it's the pol with the lowest conviction's job to kill small units
                //   rc.empower(max_r2);
                // }
              }
          }
          targetMove();
          break;

        case SLANDERER:
          System.out.println("We don't spawn slanderers!");
          rc.resign();
          break;
        }

        Clock.yield();

      } catch (GameActionException e) {
        e.printStackTrace();
      }
    }
  }
}
