package redarmy;

import java.util.ArrayDeque;
import java.util.ArrayList;
import battlecode.common.*;
import static battlecode.common.RobotType.*;

public class Comms {
  static ArrayDeque<RFlag> queue = new ArrayDeque<>();
  public static ArrayList<MapLocation> enemy_ecs = new ArrayList<>(8);
  public static ArrayList<Integer> friendly_ecs = new ArrayList<>(8);
  public static ArrayList<RobotInfo> friendly_slanderers = new ArrayList<>(20);
  /**
   * Keeps track of the total amount of conviction by friendly slanderers in
   * range, used to calculate average politician conviction.
   */
  public static int total_fslan_conv = 0;
  static RobotController rc;
  static Team team;
  static RobotInfo[] nearby = {};
  static MapLocation ec;
  static Integer ec_id;
  static boolean was_empty = true;
  static int id;
  static double avg_sin = 0, avg_cos = 0;
  static boolean has_seen_enemy = false;

  public static void start(RobotController rc) {
    Comms.rc = rc;
    team = rc.getTeam();
    try {
      if (rc.getType() == SLANDERER)
        rc.setFlag(new RFlag(RFlag.Type.None).encode(rc.getLocation(), true));
    } catch (GameActionException e) {
      e.printStackTrace();
    }
  }

  static MapLocation reinforce_loc = null;
  static MapLocation muckraker = null;

  /**
   * Puts nearby units into `nearby`, reads flags, and updates the list of enemy
   * ECs.
   */
  public static void update() throws GameActionException {
    int start_round = rc.getRoundNum();

    if (ec_id != null) {
      EFlag flag = EFlag.decode(ec, rc.getFlag(ec_id));
      switch (flag.type) {
      case EnemyEC:
        if (!enemy_ecs.contains(flag.loc))
          enemy_ecs.add(flag.loc);
        break;
      case FriendlyEC:
        if (!friendly_ecs.contains(flag.id))
          friendly_ecs.add(flag.id);
        break;

      case ConvertF:
        enemy_ecs.remove(flag.loc);
        break;

      case Reinforcements:
        reinforce_loc = flag.loc;
        break;

      // As a robot, we know our home EC's location already
      case MyLocationX:
      case MyLocationY:
      case None:
        break;
      }
    }

    // Sense nearby robots and store them in `nearby`
    nearby = rc.senseNearbyRobots();
    friendly_slanderers.clear();
    total_fslan_conv = 0;
    muckraker = null;

    for (RobotInfo i : nearby) {
      MapLocation iloc = i.location;
      if (i.team == team) {
        // If it's a friendly EC, it might have been converted and needs to be removed
        // from enemy_ecs and sent to others
        if (i.type == ENLIGHTENMENT_CENTER) {
          if (ec == null) {
            ec = iloc;
            ec_id = i.ID;
          }

          if (!friendly_ecs.contains(i.ID)) {
            friendly_ecs.add(i.ID);
            if (ec_id != i.ID) {
              queue.add(new RFlag(RFlag.Type.HelloEC, ec_id));
              queue.add(new RFlag(RFlag.Type.FriendlyEC, i.ID));
            }
          }

          for (int n = 0; n < enemy_ecs.size(); n++) {
            // We send it to others if it's new, but also every 20 rounds in case others
            // didn't hear
            if (enemy_ecs.get(n).equals(iloc) || (id + start_round) % 20 == 0) {
              enemy_ecs.remove(n);
              queue.add(new RFlag(RFlag.Type.ConvertF, iloc));
              break;
            }
          }
        }

        // If we're out of time, give up so we don't try to read flags of a unit that's
        // now out of range
        if (rc.getRoundNum() != start_round || Clock.getBytecodesLeft() < 1000)
          break;

        if (RFlag.isSlanderer(rc.getFlag(i.ID))) {
          friendly_slanderers.add(i);
          total_fslan_conv += i.conviction;
        }

      } else if (i.type == ENLIGHTENMENT_CENTER) {
        // It's an enemy EC, we need to add it to enemy_ecs and tell others about it if
        // it's new
        boolean is_new = true;
        for (MapLocation l : enemy_ecs) {
          if (iloc.equals(l)) {
            is_new = false;
            break;
          }
        }
        // We send it to others if it's new
        if (is_new) {
          enemy_ecs.add(iloc);
          queue.add(new RFlag(RFlag.Type.EnemyEC, iloc));
        }
      } else if (i.type == MUCKRAKER) {
        muckraker = i.location;
      }
    }
  }

  static int counter = 0;

  public static void finish() throws GameActionException {
    // TODO a way for newly converted politicians to be adopted by ECs
    if (ec == null)
      return;

    // Make sure to display the flag for enough turns that our home EC sees it
    int nturns = rc.getRobotCount() / 200;
    if (counter < nturns) {
      counter++;
      return;
    }

    if (!queue.isEmpty()) {
      RFlag next = queue.remove();
      rc.setFlag(next.encode(ec, rc.getType() == SLANDERER));

      switch (next.type) {
      case None:
      case HelloEC:
        break;
      default:
        counter = 0;
      }
    } else {
      rc.setFlag(new RFlag(RFlag.Type.None).encode(ec, rc.getType() == SLANDERER));
    }
  }
}
