package testplayer;

import battlecode.common.*;
import static battlecode.common.RobotType.*;

class ECenter implements Unit {
  Robot rc;

  // We just go through this list over and over again when spawning things
  static RobotType[] spawn_order = { SLANDERER, POLITICIAN, MUCKRAKER, SLANDERER, POLITICIAN };
  static Direction[] directions = { Direction.NORTH, Direction.NORTHEAST, Direction.NORTHWEST, Direction.EAST,
      Direction.WEST, Direction.SOUTHWEST, Direction.SOUTHEAST, Direction.SOUTH };

  int cursor = 0;
  int last_votes;
  boolean bid_last_round;

  // We start by bidding 1, and then if we lose, we increment our bid by 1
  int current_bid = 1;

  public ECenter(Robot rc) {
    this.rc = rc;
    last_votes = rc.getTeamVotes();
  }

  RobotType spawnType() {
    RobotType next = spawn_order[cursor];

    // Don't spawn another muckraker if there are too many, i.e. we can see two
    // right now
    if (next == MUCKRAKER) {
      int muck_count = 0;
      for (RobotInfo i : rc.nearby) {
        if (i.type == MUCKRAKER) {
          muck_count++;
          if (muck_count >= 2)
            break;
        }
      }
      // Spawn a politician instead
      if (muck_count >= 2)
        next = POLITICIAN;
    }

    return next;
  }

  int god_pol_counter = 0;

  int influenceFor(RobotType type) {
    switch (type) {
    case MUCKRAKER:
      // There isn't much reason to spawn a muckraker with more than 1 influence,
      // since it can still expose just as well
      return 1;
    case SLANDERER:
      // Since there's a floor function involved in the slanderer income function, we
      // only pick the lowest starting influence in each income bracket
      // Here, we use the maximum of 130, but we might want to go up to 207 or beyond
      int inf = rc.getInfluence();
      if (inf > 150)
        return 130;
      if (inf > 120)
        return 107;
      if (inf > 100)
        return 85;
      return 63;
    case POLITICIAN:
      // Every 10 pols, spawn a "god pol" with 200hp
      god_pol_counter++;
      if (god_pol_counter > 10 && rc.getInfluence() > 500)
        return 200;
      return 50;
    default:
      System.out.println("Not a spawnable type: " + type);
      return 0;
    }
  }

  Direction openDirection() {
    try {

      for (Direction dir : directions) {
        MapLocation l = rc.getLocation().add(dir);
        if (rc.isOnMap(l) && !rc.rc.isLocationOccupied(l))
          return dir;
      }

    } catch (GameActionException e) {
      e.printStackTrace();
    }

    return Direction.NORTH;
  }

  public void turn() {
    rc.update();

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

    // Spawn the next robot, with different starting influences per type
    if (rc.getInfluence() > 50) {
      RobotType next = spawnType();
      if (rc.build(next, openDirection(), influenceFor(next))) {
        cursor++;
        if (cursor >= spawn_order.length) {
          cursor = 0;
        }
      }
    }

    rc.finish();
  }
}
