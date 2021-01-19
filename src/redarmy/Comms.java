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

  static boolean has_reinforced = false;
  static MapLocation reinforce_loc = null;
  static MapLocation muckraker = null;

  /**
   * Puts nearby units into `nearby`, reads flags, and updates the list of enemy
   * ECs.
   */
  public static void update() throws GameActionException {
    int start_round = rc.getRoundNum();

    if (ec_id != null) {
      rc.setIndicatorLine(rc.getLocation(), ec, 128, 128, 0);
      try {
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

        case Edge:
          setEdge(flag.aux_flag, flag.loc, ec);
          break;

        case ConvertF:
          enemy_ecs.remove(flag.loc);
          break;

        case Reinforcements:
        case Reinforce2:
          reinforce_loc = flag.loc;
          has_reinforced = true;
          break;

        // As a robot, we know our home EC's location already
        case MyLocationX:
        case MyLocationY:
        case None:
          break;
        }
      } catch (GameActionException e) {
        // Our EC is dead
        ec = null;
        ec_id = null;
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
          if (!friendly_ecs.contains(i.ID)) {
            friendly_ecs.add(i.ID);
            if (ec_id != null && ec_id != i.ID) {
              queue.add(new RFlag(RFlag.Type.FriendlyEC, i.ID));
            }
          }

          if (ec == null) {
            ec = iloc;
            ec_id = i.ID;
          } else if (ec_id != i.ID) {
            queue.add(new RFlag(RFlag.Type.HelloEC, ec_id));
            ec = iloc;
            ec_id = i.ID;
          }

          for (int n = 0; n < enemy_ecs.size(); n++) {
            if (enemy_ecs.get(n).equals(iloc)) {
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
        if (muckraker == null || i.location.isWithinDistanceSquared(rc.getLocation(), muckraker.distanceSquaredTo(rc.getLocation())))
          muckraker = i.location;
      }
    }

    // Check for edges ourselves
    int sensor_radius = (int) Math.sqrt(rc.getType().sensorRadiusSquared);
    MapLocation loc = rc.getLocation();
    if (minX == null && !rc.onTheMap(loc.translate(-sensor_radius, 0))) {
      findEdge(loc.translate(-sensor_radius, 0), 1, 0, false);
    }
    if (maxX == null && !rc.onTheMap(loc.translate(sensor_radius, 0))) {
      findEdge(loc.translate(sensor_radius, 0), -1, 0, false);
    }
    if (minY == null && !rc.onTheMap(loc.translate(0, -sensor_radius))) {
      findEdge(loc.translate(0, -sensor_radius), 0, 1, true);
    }
    if (maxY == null && !rc.onTheMap(loc.translate(0, sensor_radius))) {
      findEdge(loc.translate(0, sensor_radius), 0, -1, true);
    }
  }

  public static Integer minX = null;
  public static Integer minY = null;
  public static Integer maxX = null;
  public static Integer maxY = null;

  /**
   * Checks if the location is on the map, returning `true` if unsure. Uses the
   * edges we've found if possible.
   */
  static boolean isOnMap(MapLocation loc) {
    try {
      if ((minY != null && loc.y < minY) || (maxY != null && loc.y > maxY) || (minX != null && loc.x < minX)
          || (maxX != null && loc.x > maxX))
        return false;
      if (minY == null || maxY == null || minX == null || maxX == null) {
        return (!loc.isWithinDistanceSquared(rc.getLocation(), rc.getType().sensorRadiusSquared) || rc.onTheMap(loc));
      } else {
        // We know the edges, no need to consult `rc`.
        return true;
      }
    } catch (GameActionException e) {
      e.printStackTrace();
      return true;
    }
  }

  /**
   * Processes an edge location that was found or recieved from a teammate. If we
   * already know about it, does nothing. If not, saves it and queues a message to
   * tell others about it.
   */
  static boolean setEdge(boolean is_y, MapLocation flag_loc, MapLocation unit_loc) {
    if (is_y) {
      // It's possible both units are on the edge; if so, we can see which direction
      // is on the map
      if (flag_loc.y == unit_loc.y && flag_loc.y == rc.getLocation().y) {
        MapLocation alt = rc.getLocation().translate(0, 1);
        if (isOnMap(alt))
          unit_loc = alt;
        else
          unit_loc = rc.getLocation().translate(0, -1);
      }

      // It's possible that unit is *at* the edge, so flag_loc.y = unit_loc.y;
      // but if so, this unit *isn't* at the edge, so we use that instead.
      if (flag_loc.y < unit_loc.y || flag_loc.y < rc.getLocation().y) {
        // If we've already seen this edge, don't relay it further; we don't want
        // infinite loops.
        if (minY != null)
          return false;
        minY = flag_loc.y;
      } else {
        if (maxY != null)
          return false;
        maxY = flag_loc.y;
      }
    } else {
      if (flag_loc.x == unit_loc.x && flag_loc.x == rc.getLocation().x) {
        MapLocation alt = rc.getLocation().translate(1, 0);
        if (isOnMap(alt))
          unit_loc = alt;
        else
          unit_loc = rc.getLocation().translate(-1, 0);
      }

      if (flag_loc.x < unit_loc.x || flag_loc.x < rc.getLocation().x) {
        if (minX != null)
          return false;
        minX = flag_loc.x;
      } else {
        if (maxX != null)
          return false;
        maxX = flag_loc.x;
      }
    }

    rc.setIndicatorLine(rc.getLocation(), flag_loc, 255, 0, 0);
    return true;
  }

  /**
   * Given a location we know is off the map, moves `(dx, dy)` by `(dx, dy)` until
   * we get to a location that's on the map, then calls setEdge() on that
   * location.
   */
  static void findEdge(MapLocation start, int dx, int dy, boolean is_y) throws GameActionException {
    while (!rc.onTheMap(start)) {
      start = start.translate(dx, dy);
    }
    // Go one more to make sure it's not on the edge
    if (setEdge(is_y, start, start.translate(dx, dy)))
      queue.add(new RFlag(RFlag.Type.Edge, is_y, start));
  }

  static int counter = 0;
  static boolean scary_muk = false;

  public static void finish() throws GameActionException {
    // TODO a way for newly converted politicians to be adopted by ECs
    if (ec == null)
      return;

    // If we're a slanderer, tell the EC about nearby muckrakers
    if (muckraker != null && (rc.getType() == SLANDERER || !friendly_slanderers.isEmpty())) {
      scary_muk = true;
      rc.setFlag(new RFlag(RFlag.Type.ScaryMuk, muckraker).encode(ec, true));
      rc.setIndicatorLine(rc.getLocation(), muckraker, 0, 0, 255);
      return;
    }

    if (scary_muk) {
      scary_muk = false;
      rc.setFlag(new RFlag(RFlag.Type.None).encode(ec, rc.getType() == SLANDERER));
    }

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
      case ScaryMuk:
        break;
      default:
        counter = 0;
      }
    } else {
      rc.setFlag(new RFlag(RFlag.Type.None).encode(ec, rc.getType() == SLANDERER));
    }
  }
}
