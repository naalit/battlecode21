package starfleet;

import java.util.ArrayList;
import battlecode.common.*;

class ECInfo {
  MapLocation loc;
  Integer influence;
  Integer id;
  int pendingX, pendingY;

  public ECInfo(MapLocation loc) {
    this.loc = loc;
  }

  public ECInfo(MapLocation loc, int influence) {
    this.loc = loc;
    this.influence = influence;
  }

  public ECInfo(int id) {
    this.id = id;
  }

  public boolean equals(Object o) {
    if (o instanceof ECInfo) {
      ECInfo ec = (ECInfo) o;
      return (id != null && this.id.equals(ec.id)) || (loc != null && this.loc.equals(ec.loc));
    } else {
      return false;
    }
  }
}

/**
 * A model of the board, shared by robots and ECs.
 */
public class Model {
  public static RobotController rc;
  public static Integer minX = null;
  public static Integer minY = null;
  public static Integer maxX = null;
  public static Integer maxY = null;
  public static ArrayList<ECInfo> friendly_ecs = new ArrayList<>(8);
  public static ArrayList<ECInfo> neutral_ecs = new ArrayList<>(8);
  public static ArrayList<ECInfo> enemy_ecs = new ArrayList<>(8);

  public static void init(RobotController rc) {
    Model.rc = rc;
  }

  public static boolean isNextToEC(MapLocation loc) throws GameActionException {
    for (Direction dir : Direction.values()) {
      MapLocation l = loc.add(dir);
      if (rc.canSenseLocation(l)) {
        RobotInfo r = rc.senseRobotAtLocation(l);
        if (r != null && r.type == RobotType.ENLIGHTENMENT_CENTER && r.team == rc.getTeam())
          return true;
      }
    }
    return false;
  }

  static ArrayList<Pair<MapLocation, Integer>> mucks = new ArrayList<>();

  static MapLocation updateMucks() {
    MapLocation loc = rc.getLocation();
    int closest_d = 100000;
    MapLocation closest = null;
    for (int i = 0; i < mucks.size(); i++) {
      Pair<MapLocation, Integer> m = mucks.get(i);
      if (rc.getRoundNum() - m.snd > 5) {
        mucks.remove(i);
        i--;
      } else {
        int dist2 = m.fst.distanceSquaredTo(loc);
        if (dist2 < closest_d) {
          closest = m.fst;
          closest_d = dist2;
        }
      }
    }

    return closest;
  }

  static boolean addMuck(MapLocation muck) {
    if (mucks.stream().anyMatch(x -> x.fst.equals(muck)))
      return false;

    mucks.add(new Pair<>(muck, rc.getRoundNum()));
    return true;
  }

  static MapLocation muck_epicenter() {
    int tx = 0, ty = 0;
    for (Pair<MapLocation, Integer> p : mucks) {
      tx += p.fst.x;
      ty += p.fst.y;
    }
    return new MapLocation(tx / mucks.size(), ty / mucks.size());
  }

  /**
   * Checks if the location is on the map, returning `true` if unsure. Uses the
   * edges we've found if possible.
   */
  public static boolean isOnMap(MapLocation loc) {
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
  public static boolean setEdge(boolean is_y, MapLocation flag_loc, MapLocation unit_loc) {
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
  public static Flag findEdge(MapLocation start, int dx, int dy, boolean is_y) throws GameActionException {
    while (!rc.onTheMap(start)) {
      start = start.translate(dx, dy);
    }
    // Go one more to make sure it's not on the edge
    if (setEdge(is_y, start, start.translate(dx, dy)))
      return new Flag(Flag.Type.Edge, is_y, start);
    else
      return null;
  }

  public static boolean addFriendlyEC(ECInfo e) {
    // If we're an EC, we don't want to add ourselves!
    if (e.id != null && rc.getID() == e.id)
      return false;

    if (!friendly_ecs.contains(e)) {
      friendly_ecs.add(e);
      return true;
    } else {
      return false;
    }
  }

  public static boolean addEnemyEC(ECInfo e) {
    if (!enemy_ecs.contains(e)) {
      enemy_ecs.add(e);
      if (e.loc != null)
        rc.setIndicatorLine(rc.getLocation(), e.loc, 0, 0, 0);
      return true;
    } else {
      return false;
    }
  }

  public static boolean addNeutralEC(ECInfo e) {
    if (!neutral_ecs.contains(e)) {
      neutral_ecs.add(e);
      if (e.loc != null)
        rc.setIndicatorLine(rc.getLocation(), e.loc, 127, 127, 127);
      return true;
    } else {
      return false;
    }
  }
}
