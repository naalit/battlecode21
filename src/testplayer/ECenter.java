package testplayer;

import battlecode.common.*;
import static battlecode.common.RobotType.*;

class ECenter implements Unit {
  Robot rc;

  // We just go through this list over and over again when spawning things
  static RobotType[] spawn_order = { SLANDERER, POLITICIAN, MUCKRAKER, SLANDERER, POLITICIAN };

  int cursor = 0;
  int last_votes;
  boolean bid_last_round;

  // We start by bidding 1, and then if we lose, we increment our bid by 1
  int current_bid = 1;

  public ECenter(Robot rc) {
    this.rc = rc;
    last_votes = rc.getTeamVotes();
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

    // Spawn the next robot, always with 50 influence
    // TODO: different starting influences per type
    // TODO: don't always spawn things to the north so we can spawn faster
    if (rc.getInfluence() > 50) {
      RobotType next = spawn_order[cursor];
      if (rc.build(next, Direction.NORTH, 50)) {
        cursor++;
        if (cursor >= spawn_order.length) {
          cursor = 0;
        }
      }
    }

    rc.finish();
  }
}
