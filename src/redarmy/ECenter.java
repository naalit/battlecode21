package redarmy;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import battlecode.common.*;
import static battlecode.common.RobotType.*;
import static battlecode.common.Direction.*;

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

  final static int[] pol_infs = { 25, 25, 50, 25, 25, 50, 25, 25, 200 };
  static int pol_inf_cursor = 0;

  static int influenceFor(RobotType type) {
    // Calculate the amount we're willing to spend this turn.
    // If there are enemies nearby, we don't want them to take our EC, so the amount
    // we want to keep in the EC is higher.
    int total = rc.getInfluence();
    int keep = is_enemy_nearby ? Math.min(total / 2, 500) : 40;
    int spend = total - keep;

    switch (type) {
    case MUCKRAKER:
      // There isn't much reason to spawn a muckraker with more than 1 influence,
      // since it can still expose just as well
      return 1;
    case SLANDERER: {
      for (int i : slanderer_infs) {
        if (spend >= i)
          return i;
      }
      // Returning 0 means canBuildRobot will always return false
      return 0;
    }
    case POLITICIAN: {
      if (npols > 10 && spend >= 20 && pol_inf_cursor % 2 == 0)
        return Math.min(spend, 200);
      else if (spend >= 25)
        return 25;
      else
        return 0;
    }
    default:
      System.out.println("Not a spawnable type: " + type);
      return 0;
    }
  }

  static void spawn() throws GameActionException {
    RobotType type = nmuks == 0 && npols > 1 && nslans > 1 ? MUCKRAKER
        : (is_muckraker_nearby ? POLITICIAN : (npols * 2 < nslans ? POLITICIAN : SLANDERER));
    Direction dir = openDirection();
    int inf = influenceFor(type);
    if (rc.canBuildRobot(type, dir, inf)) {
      if (type == POLITICIAN)
        pol_inf_cursor += 1;
      rc.buildRobot(type, dir, inf);
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
  static boolean is_enemy_nearby = false;
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

  static void processFlag(int iflag) {
    RFlag flag = RFlag.decode(rc.getLocation(), iflag);

    switch (flag.type) {
    case FriendlyEC:
      addFriendlyEC(flag.id);
      break;
    case EnemyEC:
      addEnemyEC(flag.loc);
      break;
    case ConvertF:
      if (enemy_ecs.remove(flag.loc))
        if (cvt_pending == null)
          cvt_pending = flag.loc;
      break;

    // If it's talking to another EC, ignore it
    case HelloEC:
    case None:
      break;
    }
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
          if ((flag & RFlag.HEADER_MASK) != 0) {
            processFlag(flag);
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

  static class ECInfo {
    int id;
    MapLocation loc = null;
    int x = 0, y = 0;

    ECInfo(int id) {
      this.id = id;
    }
  }

  static ArrayList<ECInfo> friendly_ecs = new ArrayList<>(8);

  static void updateECFlags() {
    for (int i = 0; i < friendly_ecs.size(); i++) {
      try {
        ECInfo ec = friendly_ecs.get(i);
        int flag = rc.getFlag(ec.id);
        if ((flag & EFlag.HEADER_MASK) != 0) {
          EFlag f = EFlag.decode(ec.loc, flag);
          // f will be null if we needed the location of the EC but haven't got it yet
          if (f != null) {

            switch (f.type) {
            case FriendlyEC:
              addFriendlyEC(f.id);
              break;
            case EnemyEC:
              addEnemyEC(f.loc);
              break;
            case ConvertF:
              if (enemy_ecs.remove(f.loc))
                if (cvt_pending == null)
                  cvt_pending = f.loc;
              break;
            case MyLocationX:
              ec.x = f.id;
              if (ec.y != 0) {
                ec.loc = new MapLocation(ec.x, ec.y);
                if (enemy_ecs.remove(ec.loc))
                  cvt_pending = ec.loc;
                rc.setIndicatorLine(rc.getLocation(), ec.loc, 255, 255, 255);
              }
              break;
            case MyLocationY:
              ec.y = f.id;
              if (ec.x != 0) {
                ec.loc = new MapLocation(ec.x, ec.y);
                if (enemy_ecs.remove(ec.loc))
                  cvt_pending = ec.loc;
                rc.setIndicatorLine(rc.getLocation(), ec.loc, 255, 255, 255);
              }
              break;
            // Right now we don't send reinforcements to other ECs - we may later
            case Reinforcements:
            case None:
              break;
            }

          }
        }
      } catch (GameActionException e) {
        // The EC is dead, so remove it
        friendly_ecs.remove(i);
        // Don't go past the end of the list
        i--;
      }
    }
  }

  /**
   * 0: sending x, 1: sending y, 2: done.
   */
  static int loc_send_stage = 2;
  static MapLocation cvt_pending = null;

  static void addFriendlyEC(int id) {
    for (ECInfo i : friendly_ecs) {
      if (i.id == id)
        return;
    }
    friendly_ecs.add(new ECInfo(id));
    // We need to tell this new EC our location
    loc_send_stage = 0;
  }

  static ArrayList<MapLocation> enemy_ecs = new ArrayList<>(8);

  static void addEnemyEC(MapLocation loc) {
    if (!enemy_ecs.contains(loc)) {
      enemy_ecs.add(loc);
      rc.setIndicatorLine(rc.getLocation(), loc, 0, 0, 0);
    }
  }

  static int enemy_ec_cursor = 0;
  static int friendly_ec_cursor = 0;
  static MapLocation reinforce = null;

  static EFlag nextFlag() throws GameActionException {
    if (reinforce != null) {
      EFlag flag = new EFlag(EFlag.Type.Reinforcements, reinforce);
      rc.setIndicatorLine(rc.getLocation(), reinforce, 0, 0, 255);
      reinforce = null;
      return flag;
    }

    if (cvt_pending != null) {
      EFlag flag = new EFlag(EFlag.Type.ConvertF, cvt_pending);
      cvt_pending = null;
      return flag;
    }

    // If we need to share our location, do that
    if (loc_send_stage < 2) {
      loc_send_stage++;
      if (loc_send_stage == 1)
        return new EFlag(EFlag.Type.MyLocationX, rc.getLocation().x);
      else if (loc_send_stage == 2)
        return new EFlag(EFlag.Type.MyLocationY, rc.getLocation().y);
    }

    // Then share friendly ECs
    if (friendly_ec_cursor < friendly_ecs.size()) {
      ECInfo ec = friendly_ecs.get(friendly_ec_cursor++);
      return new EFlag(EFlag.Type.FriendlyEC, ec.id);
    }

    // Otherwise, if we have enemy ECs stored, share those
    if (enemy_ecs.size() > 0) {
      if (enemy_ec_cursor >= enemy_ecs.size()) {
        friendly_ec_cursor = 0;
        enemy_ec_cursor = 0;
        loc_send_stage = 0;
      }
      MapLocation loc = enemy_ecs.get(enemy_ec_cursor++);
      return new EFlag(EFlag.Type.EnemyEC, loc);
    }

    // Nothing else to send
    return new EFlag(EFlag.Type.None);
  }

  static void showFlag() throws GameActionException {
    rc.setFlag(nextFlag().encode(rc.getLocation()));
  }

  static void update() throws GameActionException {
    updateECFlags();
    updateDistantFlags();

    nearby = rc.senseNearbyRobots();

    is_enemy_nearby = false;
    is_muckraker_nearby = false;
    npols = 0;
    nslans = 0;
    nmuks = 0;
    Team team = rc.getTeam();
    MapLocation loc = rc.getLocation();
    // A muckraker within this squared radius could instantly kill any slanderer we
    // spawn, so we shouldn't spawn slanderers
    int radius = 16;
    for (RobotInfo i : nearby) {
      if (i.team != team) {
        is_enemy_nearby = true;

        if (i.type == MUCKRAKER)
          reinforce = i.location;

        if (!is_muckraker_nearby && i.type == MUCKRAKER && i.location.isWithinDistanceSquared(loc, radius)) {
          is_muckraker_nearby = true;
        }
      } else {
        // Read the flag; we only care if it's HelloEC, otherwise we'll see it anyway
        int flag = rc.getFlag(i.ID);
        switch (RFlag.getType(flag)) {
        case HelloEC:
          RFlag f = RFlag.decode(null, flag);
          addFriendlyEC(f.id);
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

    showFlag();
  }

  static void turn() throws GameActionException {
    update();

    spawn();

    doBid();
  }

  public static void run(RobotController rc) {
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
