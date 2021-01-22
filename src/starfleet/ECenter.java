package starfleet;

import java.util.Arrays;
import battlecode.common.*;
import static battlecode.common.RobotType.*;
import static battlecode.common.Direction.*;

class NeutralEC {
  int influence;
  MapLocation loc;

  NeutralEC(MapLocation loc, int influence) {
    this.loc = loc;
    this.influence = influence;
  }
}

public class ECenter {
  static RobotController rc;

  static int last_votes;
  static boolean bid_last_round = false;
  // We start by bidding 1, and then if we lose, we increment our bid by 1
  static int current_bid = 1;

  static void doBid() throws GameActionException {
    // No reason to bid for votes past a majority
    // Also, if we bid too early our economy gets set up slower, and if we bid when
    // we have low conviction we never spawn slanderers
    if (rc.getTeamVotes() > 750 || rc.getRoundNum() < 50 || rc.getConviction() < 200)
      return;
    // If our votes didn't change, we lost, and need to bid higher.
    boolean lost_last_round = rc.getTeamVotes() == last_votes;
    last_votes = rc.getTeamVotes();
    if (bid_last_round && lost_last_round) {
      current_bid += 1;
    } else if (!bid_last_round && !lost_last_round) {
      rc.bid(2);
      return;
    }
    // We focus on destroying them instead of winning votes, so we only bid if it's
    // 10% or less of our influence
    if (current_bid <= rc.getInfluence() / 10) {
      bid_last_round = true;
      rc.bid(current_bid);
    } else {
      bid_last_round = false;
      rc.bid(2);
    }
  }

  // -- SPAWNING -- //

  static final Direction[] directions = { NORTH, SOUTH, WEST, EAST, NORTHWEST, SOUTHEAST, NORTHEAST, SOUTHWEST };
  static int dir_cursor = -1;

  /**
   * Returns the next direction where we can spawn something.
   */
  static Direction openDirection() throws GameActionException {
    for (Direction dir : directions) {// int i = 0; i < directions.length; i++) {
      // dir_cursor++;
      // if (dir_cursor >= directions.length) {
      // dir_cursor = 0;
      // }
      // Direction dir = directions[dir_cursor];
      MapLocation l = rc.getLocation().add(dir);
      if (rc.onTheMap(l) && !rc.isLocationOccupied(l))
        return dir;
    }

    return Direction.NORTH;
  }

  /**
   * Since there's a floor function involved in the slanderer income function, we
   * only pick the lowest starting influence in each income bracket
   */
  final static int[] slanderer_infs = { 949, 902, 855, 810, 766, 724, 683, 643, 605, 568, 532, 497, 463, 431, 399, 368,
      339, 310, 282, 255, 228, 203, 178, 154, 130, 107 };// , 85, 63, 41, 21 };

  static int pol_inf_cursor = 0;

  static int cursor = 0;

  static class Spawn {
    RobotType type;
    int influence;

    Spawn(RobotType type, int influence) {
      this.type = type;
      this.influence = influence;
    }
  }

  static int slanInf(int spend) {
    for (int i : slanderer_infs) {
      if (spend >= i)
        return i;
    }
    return 0;
  }

  static Spawn nextSpawn() {
    // Calculate the amount we're willing to spend this turn.
    // If there are enemies nearby, we don't want them to take our EC, so the amount
    // we want to keep in the EC is higher.
    int total = rc.getInfluence();
    // Keep enough so that we won't die to all the enemy pols blowing up
    int keep = Math.max(total_epol_conv + 1, 40);
    int spend = total - keep;

    if (!is_muckraker_nearby && nslans == 0 && spend >= 107)
      return new Spawn(SLANDERER, slanInf(spend));

    if (nmuks < 2)
      return new Spawn(MUCKRAKER, spend > 500 ? 10 : 1);

    // Stop spawning slanderers if the EC is surrounded by them already.
    // This is useful when pols are dying faster than slanderers, so slanderers
    // build up to the point where we can't defend them anymore.
    if (!is_muckraker_nearby && nslans < 30 && cursor % 2 == 0)
      return new Spawn(SLANDERER, slanInf(spend));

    return new Spawn(POLITICIAN,
        (pol_inf_cursor % 2 == 0 && spend > 50) ? Math.min(spend, Math.max(200, rc.getConviction() / 10)) : 17);
  }

  static void spawn() throws GameActionException {
    Spawn spawn = nextSpawn();
    if (spawn == null)
      return;

    Direction dir = openDirection();
    if (rc.canBuildRobot(spawn.type, dir, spawn.influence)) {
      if (spawn.type != MUCKRAKER)
        cursor++;
      if (spawn.type == POLITICIAN)
        pol_inf_cursor += 1;
      rc.buildRobot(spawn.type, dir, spawn.influence);
      MapLocation l = rc.adjacentLocation(dir);
      addID(rc.senseRobotAtLocation(l).ID);
    }
  }

  // -- COMMUNICATION -- //

  static int[] ids = new int[300];
  static int nempty = 300;
  static int firstempty = 0;
  static int endidx = 0;

  static RobotInfo[] nearby;
  static int total_epol_conv = 0;
  static boolean is_muckraker_nearby = false;
  static int npols = 0;
  static int nslans = 0;
  static int nmuks = 0;

  static void addID(int id) {
    int len = ids.length;
    if (endidx < len) {
      if (firstempty == endidx)
        firstempty++;
      if (firstempty == len) {
        // Find the first empty index
        for (int i = 0; i < len; i++) {
          if (ids[i] == 0) {
            firstempty = i;
            break;
          }
        }
      }
      ids[endidx] = id;
      endidx++;
      nempty--;
    } else if (nempty > 0) {
      nempty--;
      ids[firstempty] = id;
      if (nempty > 0) {
        for (int i = firstempty + 1; i < len; i++) {
          if (ids[i] == 0) {
            firstempty = i;
            break;
          }
        }
      }
    } else {
      // Resize ids list; this costs `len * 3` bytecode, which isn't that bad!
      // That's because Arrays.copyOf calls `newarray(newlen)` and then
      // `System.arraycopy(len)`, both of which take bytecode equal to the length.
      int new_len = len * 2;
      ids = Arrays.copyOf(ids, new_len);
      ids[len] = id;
      nempty = len - 1;
      firstempty = endidx = len + 1;
    }
  }

  static boolean rpriority = false;

  /**
   * Returns whether to keep the unit sending the flag
   */
  static boolean processFlag(int iflag) {
    Flag flag = Flag.decode(rc.getLocation(), iflag);

    switch (flag.type) {
    case FriendlyEC:
      Model.addFriendlyEC(new ECInfo(flag.id));
      break;
    case NeutralEC:
      Model.addNeutralEC(new ECInfo(flag.loc, flag.influence));
      break;
    case EnemyEC: {
      ECInfo ecif = new ECInfo(flag.loc);
      Model.neutral_ecs.remove(ecif);
      Model.addEnemyEC(ecif);
      break;
    }
    case ConvertF: {
      ECInfo ecif = new ECInfo(flag.loc);
      if (Model.enemy_ecs.remove(ecif) || Model.neutral_ecs.remove(ecif))
        if (cvt_pending == null)
          cvt_pending = flag.loc;
      break;
    }
    case Edge:
      Model.setEdge(flag.aux_flag, flag.loc, rc.getLocation());
      break;

    // If a slanderer is scared, ask for reinforcements
    case Reinforce:
      if ((flag.aux_flag || !rpriority) && (reinforce == null
          || flag.loc.isWithinDistanceSquared(rc.getLocation(), reinforce.distanceSquaredTo(rc.getLocation()))))
        if (Model.addMuck(flag.loc)) {
          if (flag.aux_flag) {
            reinforce = flag.loc;
            rpriority = flag.aux_flag;
          } else {
            reinforce2 = flag.loc;
          }
        }
      break;

    // Remove the unit if it's saying goodbye to us
    case HelloEC:
      return flag.id != rc.getID();

    // These aren't possible for a robot
    case Reinforce2:
    case MyLocationX:
    case MyLocationY:
    case None:
      break;
    }
    return true;
  }

  static int left_off = 0;

  static void updateDistantFlags() {
    // Bytecode costs:
    // For a known-dead unit: 11
    // For a newly-dead unit: 20 (I'm pretty sure getFlag throwing doesn't count)
    // For an alive unit: 20+5
    // For a flagging unit: 20+5 + flag processing cost
    // If we assume on average, one unit died, 1/4 are dead already, and 1/20 are
    // flagging, then it takes `20 + 0.25n*11 + 0.75n*25 + 0.75*0.05n*<processing>`
    // If we say processing takes 200, then it's `20 + 29n`
    // Which is around `29n`, so we can do 300 units in about 8700 bytecodes
    // That's what we'll do each turn.
    int end = Math.min(left_off + 300, endidx);
    for (int i = left_off; i < end; i++) {
      int id = ids[i];
      if (id != 0) {
        try {
          int flag = rc.getFlag(id);
          if ((flag & Flag.HEADER_MASK) != 0) {
            if (!processFlag(flag)) {
              nempty++;
              ids[i] = 0;
            }
          }
        } catch (GameActionException e) {
          // The unit is dead, so add that
          nempty++;
          ids[i] = 0;
        }
      }
    }
    // Go back to the start next turn if we finished
    left_off = end == endidx ? 0 : end;
  }

  static void updateECFlags() {
    for (int i = 0; i < Model.friendly_ecs.size(); i++) {
      ECInfo ec = Model.friendly_ecs.get(i);
      try {
        int flag = rc.getFlag(ec.id);
        if ((flag & Flag.HEADER_MASK) != 0) {
          Flag f = Flag.decode(ec.loc, flag);
          // f will be null if we needed the location of the EC but haven't got it yet
          if (f != null) {

            switch (f.type) {
            case FriendlyEC:
              Model.addFriendlyEC(new ECInfo(f.id));
              break;
            case NeutralEC:
              Model.addNeutralEC(new ECInfo(f.loc, f.influence));
              break;
            case EnemyEC: {
              ECInfo ecif = new ECInfo(f.loc);
              Model.neutral_ecs.remove(ecif);
              Model.addEnemyEC(ecif);
              break;
            }
            case ConvertF: {
              ECInfo ecif = new ECInfo(f.loc);
              if (Model.enemy_ecs.remove(ecif) || Model.neutral_ecs.remove(ecif))
                if (cvt_pending == null)
                  cvt_pending = f.loc;
              break;
            }
            case Edge:
              Model.setEdge(f.aux_flag, f.loc, ec.loc);
              break;
            case MyLocationX:
              if (ec.loc != null)
                break;
              ec.pendingX = f.id;
              if (ec.pendingY != 0) {
                ec.loc = new MapLocation(ec.pendingX, ec.pendingY);
                if (Model.enemy_ecs.remove(ec) || Model.neutral_ecs.remove(ec))
                  cvt_pending = ec.loc;
                rc.setIndicatorLine(rc.getLocation(), ec.loc, 255, 255, 255);
              }
              break;
            case MyLocationY:
              if (ec.loc != null)
                break;
              ec.pendingY = f.id;
              if (ec.pendingX != 0) {
                ec.loc = new MapLocation(ec.pendingX, ec.pendingY);
                if (Model.enemy_ecs.remove(ec) || Model.neutral_ecs.remove(ec))
                  cvt_pending = ec.loc;
                rc.setIndicatorLine(rc.getLocation(), ec.loc, 255, 255, 255);
              }
              break;
            case Reinforce:
              if (reinforce2 == null
                  || f.loc.isWithinDistanceSquared(rc.getLocation(), reinforce2.distanceSquaredTo(rc.getLocation())))
                if (Model.addMuck(f.loc))
                  reinforce2 = f.loc;
              break;
            case Reinforce2:
            case HelloEC:
            case None:
              break;
            }

          }
        }
      } catch (GameActionException e) {
        // The EC is dead, so remove it
        Model.friendly_ecs.remove(i);
        // If it's dead, it now belongs to the enemy
        if (ec.loc != null) {
          ec.id = null;
          Model.enemy_ecs.add(ec);
        }
        // Don't go past the end of the list
        i--;
      }
    }
  }

  /**
   * 0: sending x, 1: sending y, 2: done.
   */
  static int loc_send_stage = 0;
  static MapLocation cvt_pending = null;

  static boolean neutral_ec_changed = false;

  static int enemy_ec_cursor = 0;
  static int friendly_ec_cursor = 0;
  static int neutral_ec_cursor = 0;
  static MapLocation reinforce = null;
  static MapLocation reinforce2 = null;
  static int edge_cursor = 0;

  static Flag nextFlag() throws GameActionException {
    if (reinforce != null) {
      Flag flag = new Flag(Flag.Type.Reinforce, reinforce);
      rc.setIndicatorLine(rc.getLocation(), reinforce, 0, 0, 255);
      rpriority = false;
      reinforce = null;
      reinforce2 = null;
      return flag;
    } else if (reinforce2 != null) {
      Flag flag = new Flag(Flag.Type.Reinforce2, reinforce2);
      rc.setIndicatorLine(rc.getLocation(), reinforce2, 0, 127, 255);
      reinforce2 = null;
      return flag;
    }

    if (cvt_pending != null) {
      rc.setIndicatorLine(rc.getLocation(), cvt_pending, 255, 0, 255);
      Flag flag = new Flag(Flag.Type.ConvertF, cvt_pending);
      cvt_pending = null;
      return flag;
    }

    // If we need to share our location, do that
    if (loc_send_stage < 2) {
      loc_send_stage++;
      if (loc_send_stage == 1)
        return new Flag(Flag.Type.MyLocationX, rc.getLocation().x);
      else if (loc_send_stage == 2)
        return new Flag(Flag.Type.MyLocationY, rc.getLocation().y);
    }

    // Then share friendly ECs
    if (friendly_ec_cursor < Model.friendly_ecs.size()) {
      ECInfo ec = Model.friendly_ecs.get(friendly_ec_cursor++);
      return new Flag(Flag.Type.FriendlyEC, ec.id);
    }

    // Otherwise, if we have enemy ECs stored, share those
    if (neutral_ec_cursor < Model.neutral_ecs.size()) {
      ECInfo ec = Model.neutral_ecs.get(neutral_ec_cursor++);
      return Flag.neutralEC(ec.loc, ec.influence);
    }

    // Otherwise, if we have enemy ECs stored, share those
    if (enemy_ec_cursor < Model.enemy_ecs.size()) {
      ECInfo ec = Model.enemy_ecs.get(enemy_ec_cursor++);
      return new Flag(Flag.Type.EnemyEC, ec.loc);
    }

    // And send edges
    edge_cursor++;
    MapLocation loc = rc.getLocation();
    if (Model.minX != null && edge_cursor <= 1) {
      return new Flag(Flag.Type.Edge, false, new MapLocation(Model.minX, loc.y));
    } else if (Model.maxX != null && edge_cursor <= 2) {
      return new Flag(Flag.Type.Edge, false, new MapLocation(Model.maxX, loc.y));
    } else if (Model.minY != null && edge_cursor <= 3) {
      return new Flag(Flag.Type.Edge, true, new MapLocation(loc.x, Model.minY));
    } else {
      friendly_ec_cursor = 0;
      neutral_ec_cursor = 0;
      enemy_ec_cursor = 0;
      loc_send_stage = 0;
      edge_cursor = 0;

      if (Model.maxY != null) {
        return new Flag(Flag.Type.Edge, true, new MapLocation(loc.x, Model.maxY));
      }
    }

    // Nothing else to send
    return new Flag(Flag.Type.None);
  }

  static void showFlag() throws GameActionException {
    rc.setFlag(nextFlag().encode(rc.getLocation(), false));
  }

  static void update() throws GameActionException {
    nearby = rc.senseNearbyRobots();
    Model.updateMucks();

    total_epol_conv = 0;
    is_muckraker_nearby = false;
    boolean is_slanderer_nearby = false;
    npols = 0;
    nslans = 0;
    nmuks = 0;
    Team team = rc.getTeam();
    Team enemy = team.opponent();
    MapLocation loc = rc.getLocation();
    for (RobotInfo i : nearby) {
      if (i.team == enemy) {
        if (i.type == POLITICIAN)
          total_epol_conv += i.conviction;

        if (i.type == MUCKRAKER && (reinforce == null
            || i.location.isWithinDistanceSquared(loc, reinforce.distanceSquaredTo(rc.getLocation()))))
          if (Model.addMuck(i.location))
            reinforce = i.location;

        if (!is_muckraker_nearby && i.type == MUCKRAKER) {
          is_muckraker_nearby = true;
        }
      } else {
        if (i.type == SLANDERER)
          is_slanderer_nearby = true;
        // Read the flag; we only care if it's HelloEC, otherwise we'll see it anyway
        int flag = rc.getFlag(i.ID);
        switch (Flag.getType(flag)) {
        case HelloEC:
          Flag f = Flag.decode(null, flag);
          Model.addFriendlyEC(new ECInfo(f.id));
          addID(i.ID);
          break;
        default:
          break;
        }

        // Count the number of politicians and slanderers around
        if (i.type == POLITICIAN)
          npols++;
        else if (i.type == SLANDERER)
          nslans++;
        else if (i.type == MUCKRAKER)
          nmuks++;
      }
    }

    if (is_muckraker_nearby && is_slanderer_nearby && reinforce != null) {
    } else {
      reinforce2 = reinforce;
      reinforce = null;
    }

    updateECFlags();
    updateDistantFlags();

    showFlag();
  }

  static void turn() throws GameActionException {
    update();

    spawn();

    doBid();
  }

  public static void run(RobotController rc) {
    Model.init(rc);
    ECenter.rc = rc;
    last_votes = rc.getTeamVotes();

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
