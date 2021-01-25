package starfleet;

import java.util.ArrayList;
import battlecode.common.*;

class ECInfo {
  MapLocation loc;
  Integer influence;
  Integer id;
  int pendingX, pendingY;
  Symmetry guessed = null;
  boolean attacked = false;

  public static ECInfo guess(MapLocation loc, Symmetry sym) {
    ECInfo i = new ECInfo(loc);
    i.guessed = sym;
    return i;
  }

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

enum Symmetry {
  Horizontal, Vertical, Rotational;

  public MapLocation swap(MapLocation loc) {
    if (loc == null)
      return null;
    switch (this) {
    case Horizontal:
      if (Model.minX != null && Model.maxX != null)
        return new MapLocation(Model.maxX + (Model.minX - loc.x), loc.y);
      break;
    case Vertical:
      if (Model.minY != null && Model.maxY != null)
        return new MapLocation(loc.x, Model.maxY + (Model.minY - loc.y));
      break;
    case Rotational:
      if (Model.minX != null && Model.maxX != null && Model.minY != null && Model.maxY != null)
        return new MapLocation(Model.maxX + (Model.minX - loc.x), Model.maxY + (Model.minY - loc.y));
      break;
    }
    return null;
  }

  public static Symmetry decode(int i) {
    switch (i) {
    case 0:
      return null;
    case 1:
      return Horizontal;
    case 2:
      return Vertical;
    case 3:
      return Rotational;
    default:
      throw new RuntimeException("Unknown symmetry " + i);
    }
  }

  public int encode() {
    switch (this) {
    case Horizontal:
      return 1;
    case Vertical:
      return 2;
    case Rotational:
      return 3;
    }
    throw new RuntimeException("Unreachable");
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

  static boolean maybe_horiz = true;
  static boolean maybe_vert = true;
  static boolean maybe_rot = true;
  static Symmetry guessed = null;

  static MapLocation last_converted = null;
  static MapLocation reinforce = null;
  static MapLocation reinforce2 = null;
  static boolean rpriority = false;
  static int wrong_sym_turn = 0;
  static Symmetry unreported_wrong = null;
  static Integer home_ec_id = null;
  static int home_ec_income = 1;
  static boolean home_ec_died = false;

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
              addFriendlyEC(new ECInfo(f.id));
              break;
            case NeutralEC:
              addNeutralEC(new ECInfo(f.loc, f.influence));
              break;
            // If it's only a guess, and we're an EC, we'll make our own guesses
            case EnemyEC:
              if (rc.getType() != RobotType.ENLIGHTENMENT_CENTER || f.sym == null) {
                ECInfo ecif = new ECInfo(f.loc);
                ecif.guessed = f.sym;
                friendly_ecs.remove(ecif);
                neutral_ecs.remove(ecif);
                addEnemyEC(ecif);
              }
              break;
            case ConvertF: {
              ECInfo ecif = new ECInfo(f.loc);
              if (enemy_ecs.remove(ecif) || neutral_ecs.remove(ecif))
                last_converted = f.loc;
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
                Model.guessEC(ec.loc);
                if (enemy_ecs.remove(ec) || neutral_ecs.remove(ec))
                  last_converted = ec.loc;
                rc.setIndicatorLine(rc.getLocation(), ec.loc, 255, 255, 255);
              }
              break;
            case MyLocationY:
              if (ec.loc != null)
                break;
              ec.pendingY = f.id;
              if (ec.pendingX != 0) {
                ec.loc = new MapLocation(ec.pendingX, ec.pendingY);
                Model.guessEC(ec.loc);
                if (Model.enemy_ecs.remove(ec) || Model.neutral_ecs.remove(ec))
                  last_converted = ec.loc;
                rc.setIndicatorLine(rc.getLocation(), ec.loc, 255, 255, 255);
              }
              break;
            case Muckraker2:
              if (rc.getType() == RobotType.ENLIGHTENMENT_CENTER)
                break;
            case Muckraker:
              if (reinforce2 == null
                  || f.loc.isWithinDistanceSquared(rc.getLocation(), reinforce2.distanceSquaredTo(rc.getLocation())))
                if (addMuck(f.loc)) {
                  reinforce2 = f.loc;
                  if (!rpriority)
                    rpriority = f.aux_flag;
                }
              break;

            case WrongSymmetry:
              Symmetry sym = Symmetry.decode(f.id);
              if (sym != null && sym == Model.guessed) {
                wrongSymmetry(sym, true);
                wrong_sym_turn = rc.getRoundNum();
                unreported_wrong = sym;
              }
              break;

            case CleanupMode:
              if (enemy_ecs.isEmpty())
                cleanup_mode = true;
              break;

            case Income:
              if (home_ec_id == ec.id) {
                home_ec_income = f.id;
              }
              break;

            case EnemyCenter:
            case AdoptMe:
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
        if (ec.id == home_ec_id) {
          home_ec_died = true;
        }
        // Don't go past the end of the list
        i--;
      }
    }
  }

  public static void wrongSymmetry(Symmetry sym, boolean reguess) {
    if (sym == null)
      sym = guessed;
    if (sym != null)
      switch (sym) {
      case Horizontal:
        maybe_horiz = false;
      case Vertical:
        maybe_vert = false;
      case Rotational:
        maybe_rot = false;
      }
    if (sym != null && (guessed == null || guessed == sym)) {
      // Remove and reguess
      final Symmetry sym2 = sym;
      enemy_ecs.removeIf(x -> x.guessed == sym2);
      if (reguess)
        guessSymmetry();
    }
  }

  /**
   * Should only be called when enemy_ecs is empty of guessed ECs.
   */
  public static void guessSymmetry() {
    guessSymmetry(null);
  }

  /**
   * Should only be called when enemy_ecs is empty of guessed ECs.
   */
  public static void guessSymmetry(MapLocation center) {
    int[] evidence = { 0, 0, 0 };
    if (center != null) {
      MapLocation loc = rc.getLocation();
      int dx = Math.abs(center.x - loc.x);
      int dy = Math.abs(center.y - loc.y);
      if (dx > 15 && dy < 5) {
        // Horizontal
        evidence[0]++;
      } else if (dy > 15 && dx < 5) {
        // Vertical
        evidence[1]++;
      } else if (dx > 15 && dy > 15) {
        // Rotational
        evidence[2]++;
      }
    }
    for (ECInfo i : friendly_ecs) {
      for (int s = 1; s < 4; s++) {
        Symmetry sym = Symmetry.decode(s);
        ECInfo ec = ECInfo.guess(sym.swap(i.loc), sym);
        if (friendly_ecs.contains(ec) || enemy_ecs.contains(ec) || neutral_ecs.contains(ec))
          evidence[s - 1]++;
      }
    }
    Symmetry sym = null;
    if (maybe_rot && evidence[2] >= evidence[1] && evidence[2] >= evidence[0])
      sym = Symmetry.Rotational;
    else if (maybe_vert && evidence[1] >= evidence[0])
      sym = Symmetry.Vertical;
    else if (maybe_horiz)
      sym = Symmetry.Horizontal;
    else if (maybe_vert)
      sym = Symmetry.Vertical;
    else if (maybe_rot)
      sym = Symmetry.Rotational;
    else
      throw new RuntimeException("All symmetries are impossible!");
    setSymmetry(sym);
  }

  /**
   * Should only be called when enemy_ecs is empty of guessed ECs.
   */
  static void setSymmetry(Symmetry sym) {
    guessed = sym;
    if (sym == null)
      return;

    for (ECInfo i : friendly_ecs) {
      guessEC(i.loc);
    }
    // Assume neutral ECs have fallen to the enemy
    for (ECInfo i : neutral_ecs) {
      guessEC(i.loc);
    }
    if (rc.getType() == RobotType.ENLIGHTENMENT_CENTER)
      guessEC(rc.getLocation());
  }

  static void guessEC(MapLocation original) {
    if (guessed == null || original == null)
      return;
    ECInfo ec = ECInfo.guess(guessed.swap(original), guessed);
    if (ec.loc == null)
      return;
    if (rc.getLocation().equals(ec) || friendly_ecs.contains(ec) || enemy_ecs.contains(ec) || neutral_ecs.contains(ec))
      return;
    else {
      rc.setIndicatorLine(rc.getLocation(), ec.loc, 255, 255, 0);
      enemy_ecs.add(ec);
    }
  }

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
  static StringSet muck_set = new StringSet();

  static MapLocation updateMucks() {
    MapLocation loc = rc.getLocation();
    int closest_d = 100000;
    MapLocation closest = null;
    for (int i = 0; i < mucks.size(); i++) {
      Pair<MapLocation, Integer> m = mucks.get(i);
      if (rc.getRoundNum() - m.snd > 5) {
        muck_set.remove(m.fst.toString());
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
    String str = muck.toString();
    if (!muck_set.add(str))
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

  public static boolean cleanup_mode = false;

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
    // Recalculate symmetry if edges changed
    setSymmetry(guessed);
    return true;
  }

  public static boolean knowsEdges() {
    return minX != null && minY != null && maxX != null && maxY != null;
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
      guessEC(e.loc);
      return true;
    } else {
      return false;
    }
  }

  public static boolean addEnemyEC(ECInfo e) {
    // Confirm it if we only guessed
    for (ECInfo i : enemy_ecs) {
      if (i.loc.equals(e.loc)) {
        if (i.guessed != null && e.guessed == null) {
          i.guessed = null;
          return true;
        } else {
          return false;
        }
      }
    }
    // If there are any enemy ECs, we're no longer in cleanup mode
    cleanup_mode = false;
    enemy_ecs.add(e);
    if (e.loc != null)
      rc.setIndicatorLine(rc.getLocation(), e.loc, 0, 0, 0);
    return true;
  }

  public static boolean addNeutralEC(ECInfo e) {
    if (!neutral_ecs.contains(e)) {
      neutral_ecs.add(e);
      if (e.loc != null) {
        guessEC(e.loc);
        rc.setIndicatorLine(rc.getLocation(), e.loc, 127, 127, 127);
      }
      return true;
    } else {
      return false;
    }
  }
}
