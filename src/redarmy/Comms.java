package redarmy;

import java.util.ArrayDeque;
import java.util.ArrayList;
import battlecode.common.*;
import static battlecode.common.RobotType.*;

public class Comms {
  static MessageQueue queue = new MessageQueue();
  public static ArrayList<MapLocation> enemy_ecs = new ArrayList<MapLocation>();
  public static ArrayList<RobotInfo> friendly_slanderers = new ArrayList<RobotInfo>();
  /**
   * Keeps track of the total amount of conviction by friendly slanderers in range,
   * used to calculate average politician conviction.
   */
  public static int total_fslan_conv = 0;
  static RobotController rc;
  static Team team;
  static RobotInfo[] nearby = {};
  static MapLocation ec;
  static boolean was_empty = true;
  static int id;

  public static void start(RobotController rc) {
    Comms.rc = rc;
    team = rc.getTeam();
  }

  /**
   * Sets the flag to None, which must be done when moving so an outdated relative
   * location isn't shown.
   */
  public static void setNeutralFlag() throws GameActionException {
    rc.setFlag(new Flag(rc.getType() == SLANDERER, Flag.Type.None).encode(rc.getLocation()));
  }

  /**
   * Puts nearby units into `nearby`, reads flags, and updates the list of enemy
   * ECs.
   */
  public static void update() throws GameActionException {
    int start_round = rc.getRoundNum();

    // Sense nearby robots and store them in `nearby`
    nearby = rc.senseNearbyRobots();
    MapLocation loc = rc.getLocation();
    int radius = rc.getType().sensorRadiusSquared;
    friendly_slanderers.clear();
    total_fslan_conv = 0;

    for (RobotInfo i : nearby) {
      MapLocation iloc = i.location;
      if (i.team == team) {
        // If it's a friendly EC, it might have been converted and needs to be removed
        // from enemy_ecs and sent to others
        if (i.type == ENLIGHTENMENT_CENTER) {
          ec = iloc;
          for (int n = 0; n < enemy_ecs.size(); n++) {
            // We send it to others if it's new, but also every 20 rounds in case others
            // didn't hear
            if (enemy_ecs.get(n).equals(iloc) || (id + start_round) % 20 == 0) {
              enemy_ecs.remove(n);
              queue.enqueue(new Flag(rc.getType() == SLANDERER, Flag.Type.ConvertF, iloc), Priority.Medium);
              break;
            }
          }
        }

        // If we're out of time, give up so we don't try to read flags of a unit that's
        // now out of range
        if (rc.getRoundNum() != start_round || Clock.getBytecodesLeft() < 1000)
          break;

        Flag flag = Flag.decode(iloc, rc.getFlag(i.ID));

        // RobotInfo isn't mutable, but politicians would ideally like to know which
        // units are slanderers, so we should store this somewhere at some point
        if (flag.is_slanderer) {
          friendly_slanderers.add(i);
          total_fslan_conv += i.conviction;
        }

        // Process the flag
        switch (flag.type) {
        case EnemyEC:
          // We'll find it on our own
          if (flag.loc.isWithinDistanceSquared(loc, radius))
            break;
          boolean is_new = true;
          for (MapLocation l : enemy_ecs) {
            if (flag.loc.equals(l)) {
              is_new = false;
              break;
            }
          }
          if (is_new) {
            enemy_ecs.add(flag.loc);
            // Clear the queue to avoid endless messages about conversions back and forth
            // If we have more messages in the queue in the future, we might want to be more
            // selective about what we clear
            queue.clear();
            queue.enqueue(flag, Priority.Medium);
          }
          break;

        case ConvertF:
          // We'll find it on our own
          if (flag.loc.isWithinDistanceSquared(loc, radius))
            break;
          for (int n = 0; n < enemy_ecs.size(); n++) {
            if (enemy_ecs.get(n).equals(flag.loc)) {
              enemy_ecs.remove(n);
              // Clear the queue to avoid endless messages about conversions back and forth
              // If we have more messages in the queue in the future, we might want to be more
              // selective about what we clear
              queue.clear();
              queue.enqueue(flag, Priority.Medium);
              break;
            }
          }
          break;

        case None:
          break;
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
        // We send it to others if it's new, but also every 20 rounds in case others
        // didn't hear
        if (is_new || (id + start_round) % 40 == 0) {
          enemy_ecs.add(iloc);
          queue.enqueue(new Flag(rc.getType() == SLANDERER, Flag.Type.EnemyEC, iloc), Priority.Medium);
        }
      }
    }
  }

  static int counter = 0;

  public static void finish() throws GameActionException {
    counter++;
    if (counter < 4) {
      setNeutralFlag();
      return;
    }
    if (!was_empty) {
      counter = 0;
      Flag next = queue.next();
      if (next != null) {
        rc.setFlag(next.encode(rc.getLocation()));
      }
    }
    was_empty = queue.isEmpty();
  }
}

enum Priority {
  /**
   * High priority: the unit should disregard everything else and send the
   * message.
   */
  High,
  /**
   * Medium priority: it's time-sensitive, so sooner is better than later, but
   * it's not essential it arrives as soon as possible.
   */
  Medium,
  /**
   * Low priority: send whenever we happen to have a free turn.
   */
  Low,
}

/**
 * A priority queue for messages sent as flags.
 */
class MessageQueue {
  private ArrayDeque<Flag> high = new ArrayDeque<Flag>();
  private ArrayDeque<Flag> med = new ArrayDeque<Flag>();
  private ArrayDeque<Flag> low = new ArrayDeque<Flag>();

  public void clear() {
    high.clear();
    med.clear();
    low.clear();
  }

  public boolean isEmpty() {
    return high.isEmpty() && med.isEmpty() && low.isEmpty();
  }

  /**
   * Removes and returns the flag we should send next, returning `null` if there
   * aren't any pending.
   */
  public Flag next() {
    if (!high.isEmpty())
      return high.remove();
    if (!med.isEmpty())
      return med.remove();
    if (!low.isEmpty())
      return low.remove();
    return null;
  }

  public void enqueue(Flag flag, Priority priority) {
    switch (priority) {
    case High:
      high.add(flag);
      break;
    case Medium:
      med.add(flag);
      break;
    case Low:
      low.add(flag);
      break;
    }
  }
}
