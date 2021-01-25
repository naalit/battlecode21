package starfleet;

import java.util.ArrayDeque;
import java.util.ArrayList;
import battlecode.common.*;
import static battlecode.common.RobotType.*;

public class Robot {
  static RobotController rc;
  static MapLocation target = null;
  static int retarget_acc;

  // -- PATHFINDING -- //

  static boolean targetMove() {
    return targetMove(false);
  }

  static boolean targetMove(boolean exploring) {
    try {

      MapLocation loc = rc.getLocation();

      if (target == null || loc.equals(target) || (exploring && !Model.isOnMap(target))) {
        if (exploring)
          target = retarget();
        else
          return false;
      }

      rc.setIndicatorLine(loc, target, 0, 255, 0);

      Direction dir = loc.directionTo(target);
      boolean too_close = ec != null && loc.isWithinDistanceSquared(ec, 2);

      // Try going around obstacles, first left, then right
      MapLocation[] options = { loc.add(dir), loc.add(dir.rotateLeft()), loc.add(dir.rotateRight()),
          loc.add(dir.rotateLeft().rotateLeft()), loc.add(dir.rotateRight().rotateRight()) };
      MapLocation best = null;
      double best_pass = -1;
      int prev_dist2 = loc.distanceSquaredTo(target);
      for (MapLocation i : options) {
        // Don't occupy EC spawning spaces
        if ((ec != null && i.isWithinDistanceSquared(ec, 2)) || !rc.canMove(loc.directionTo(i)))
          continue;

        if (i.equals(target)) {
          best = i;
          best_pass = 100;
        } else if (rc.sensePassability(i) > best_pass
            && (too_close || i.isWithinDistanceSquared(target, prev_dist2 - 1))) {
          best = i;
          best_pass = rc.sensePassability(i);
        }
      }
      // It's better to move in a bad direction than not move at all
      if (best == null && rc.isReady()) {
        if (exploring) {
          target = retarget();
          return targetMove(false);
        } else {
          MapLocation[] others = { loc.add(dir.rotateLeft().rotateLeft()), loc.add(dir.rotateRight().rotateRight()) };
          for (MapLocation i : others) {
            // Don't occupy EC spawning spaces
            if ((ec != null && i.isWithinDistanceSquared(ec, 2)) || !rc.canMove(loc.directionTo(i)))
              continue;

            if (rc.sensePassability(i) > best_pass) {
              best = i;
              best_pass = rc.sensePassability(i);
            }
          }
        }
      }
      if (best == null)
        return false;
      dir = loc.directionTo(best);

      rc.move(dir);
      return true;

    } catch (GameActionException e) {
      // This can happen if the turn changes in the middle of moving
      return false;
    }
  }

  static MapLocation retarget() {
    int width = (Model.minX != null && Model.maxX != null) ? Model.maxX - Model.minX : 64;
    int height = (Model.minY != null && Model.maxY != null) ? Model.maxY - Model.minY : 64;
    MapLocation min = new MapLocation(Model.minX != null ? Model.minX : rc.getLocation().x - width / 2,
        Model.minY != null ? Model.minY : rc.getLocation().y - height / 2);

    return retarget(min, width, height);
  }

  static MapLocation retarget(MapLocation min, int width, int height) {
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

  static void circleEC(double dist) {
    int r2 = (int) (dist * dist);
    Direction closest = null;
    int closest_d = 10000;
    for (Direction dir : Direction.values()) {
      if (rc.canMove(dir)) {
        MapLocation l = rc.getLocation().add(dir).add(dir);
        int d = Math.abs(l.distanceSquaredTo(ec) - r2);
        if (d < closest_d) {
          closest = dir;
          closest_d = d;
        }
      }
    }
    if (closest != null)
      target = rc.getLocation().add(closest);
    targetMove();
  }

  // -- COMMUNICATION -- //

  static ArrayDeque<Flag> queue = new ArrayDeque<>();
  public static ArrayList<RobotInfo> friendly_slanderers = new ArrayList<>(20);

  static Team team;
  static RobotInfo[] nearby = {};

  /**
   * Our home EC's location and ID.
   */
  static MapLocation ec;
  static Integer ec_id;

  /**
   * When we see a new EC, we switch to that as home EC, but first we need to tell
   * our old home EC about it. While we're doing that, we keep the new EC's
   * location and ID in these `pending_*` variables.
   */
  static MapLocation pending_ec;
  static Integer pending_id;

  /**
   * Slanderers keep track of where nearby pols are, so they don't go too far
   * away. We populate pol_min_x etc. with edges if we're close enough, so we also
   * have seen_pol to keep track of whether we've seen any politicians at all.
   */
  static boolean seen_pol = false;
  static Integer pol_min_x = null;
  static Integer pol_max_x = null;
  static Integer pol_min_y = null;
  static Integer pol_max_y = null;

  /**
   * We keep track of the location of the last muckraker message we got in
   * `reinforce_loc`, whether it was high priority (= there's a slanderer nearby),
   * and how long it's been since we received it. We only override high priority
   * messages with low priority ones if it's been 5 turns.
   */
  static MapLocation reinforce_loc = null;
  static int rturns = 5;
  static boolean rpriority = false;

  /**
   * Stores the closest muckraker to this unit.
   */
  static MapLocation muckraker = null;
  /**
   * When we hear about a neutral or enemy EC converting to friendly, we store it
   * here so that politicians can go there and defend it.
   */
  static MapLocation cvt_loc = null;
  static boolean ec_noticed_wrong_sym = false;
  static int ec_income;

  /**
   * Puts nearby units into `nearby`, reads flags, updates the list of enemy ECs,
   * etc.
   */
  public static void updateComms() throws GameActionException {
    rturns++;
    if (rturns > 5)
      rpriority = false;
    int start_round = rc.getRoundNum();

    if (ec_id != null) {
      rc.setIndicatorLine(rc.getLocation(), ec, 128, 128, 0);
      try {
        Flag flag = Flag.decode(ec, rc.getFlag(ec_id));
        switch (flag.type) {
        case EnemyEC: {
          ECInfo ecif = new ECInfo(flag.loc);
          ecif.guessed = !flag.aux_flag;
          Model.friendly_ecs.remove(ecif);
          Model.neutral_ecs.remove(ecif);
          Model.addEnemyEC(ecif);
          break;
        }
        case NeutralEC:
          Model.addNeutralEC(new ECInfo(flag.loc, flag.influence));
          break;
        case FriendlyEC:
          Model.addFriendlyEC(new ECInfo(flag.id));
          break;

        case Edge:
          Model.setEdge(flag.aux_flag, flag.loc, ec);
          break;

        case ConvertF: {
          ECInfo ecif = new ECInfo(flag.loc);
          if (Model.neutral_ecs.remove(ecif)) {
            rc.setIndicatorLine(rc.getLocation(), flag.loc, 255, 0, 255);
            cvt_loc = flag.loc;
          }
          Model.enemy_ecs.remove(ecif);
          break;
        }
        case Muckraker:
        case Muckraker2:
          if (rturns >= 5 || flag.aux_flag || !rpriority) {
            rturns = 0;
            rpriority = flag.aux_flag;
            reinforce_loc = flag.loc;
          }
          break;

        case WrongSymmetry:
          ec_noticed_wrong_sym = true;
          Model.wrongSymmetry(Symmetry.decode(flag.id), false);
          break;

        case Income:
          ec_income = flag.id;
          break;

        // As a robot, we know our home EC's location already
        case MyLocationX:
        case MyLocationY:
        case AdoptMe:
        case EnemyCenter:
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
    muckraker = null;
    pol_min_x = null;
    pol_min_y = null;
    pol_max_x = null;
    pol_max_y = null;
    seen_pol = false;
    // If we're close to an edge, allow slanderers to it
    MapLocation loc = rc.getLocation();
    // if (Model.minX != null && Math.abs(loc.x - Model.minX) < 6)
    // pol_min_x = Model.minX;
    // if (Model.minY != null && Math.abs(loc.y - Model.minY) < 6)
    // pol_min_y = Model.minY;
    // if (Model.maxX != null && Math.abs(loc.x - Model.maxX) < 6)
    // pol_max_x = Model.maxX;
    // if (Model.maxY != null && Math.abs(loc.y - Model.maxY) < 6)
    // pol_max_y = Model.maxY;

    for (RobotInfo i : nearby) {
      MapLocation iloc = i.location;
      if (i.team == team) {
        // If it's a friendly EC, it might have been converted and needs to be removed
        // from enemy_ecs and sent to others
        if (i.type == ENLIGHTENMENT_CENTER) {
          ECInfo ecif = new ECInfo(i.ID);
          ecif.loc = iloc;

          if (Model.enemy_ecs.remove(ecif) || Model.neutral_ecs.remove(ecif)) {
            rc.setIndicatorLine(rc.getLocation(), iloc, 255, 0, 255);
            cvt_loc = iloc;
            queue.add(new Flag(Flag.Type.ConvertF, iloc));
          }

          if (Model.addFriendlyEC(ecif)) {
            if (ec_id != null && ec_id != i.ID) {
              queue.add(new Flag(Flag.Type.FriendlyEC, i.ID));
            }
          }

          if (ec == null) {
            ec = iloc;
            ec_id = i.ID;
          } else if (ec_id != i.ID) {
            queue.add(new Flag(Flag.Type.AdoptMe, i.ID));
            pending_ec = iloc;
            pending_id = i.ID;
            cvt_loc = null;
          }
        }

        // If we're out of time, give up so we don't try to read flags of a unit that's
        // now out of range
        if (rc.getRoundNum() != start_round || Clock.getBytecodesLeft() < 1000)
          break;

        if (Flag.isSlanderer(rc.getFlag(i.ID))) {
          friendly_slanderers.add(i);
        } else if (i.type == POLITICIAN) {
          seen_pol = true;
          if (pol_min_x == null || iloc.x < pol_min_x)
            pol_min_x = iloc.x;
          if (pol_max_x == null || iloc.x > pol_max_x)
            pol_max_x = iloc.x;
          if (pol_min_y == null || iloc.y < pol_min_y)
            pol_min_y = iloc.y;
          if (pol_max_y == null || iloc.y > pol_max_y)
            pol_max_y = iloc.y;
        }

      } else if (i.type == ENLIGHTENMENT_CENTER) {
        ECInfo ecif = new ECInfo(iloc, i.influence);
        ecif.id = i.ID;

        if (i.team == team.opponent()) {
          // It's an enemy EC
          Model.friendly_ecs.remove(ecif);
          Model.neutral_ecs.remove(ecif);
          if (Model.addEnemyEC(ecif)) {
            queue.add(new Flag(Flag.Type.EnemyEC, true, iloc));
          }
        } else {
          // It's a neutral EC
          if (Model.addNeutralEC(ecif)) {
            queue.add(Flag.neutralEC(i.location, i.influence));
          }
        }
      } else if (i.type == MUCKRAKER) {
        if (muckraker == null
            || i.location.isWithinDistanceSquared(rc.getLocation(), muckraker.distanceSquaredTo(rc.getLocation())))
          muckraker = i.location;
      }
    }

    // Check if we can confirm or deny guessed ECs in range
    for (ECInfo i : Model.enemy_ecs) {
      if (i.guessed) {
        if (rc.canSenseLocation(i.loc)) {
          RobotInfo r = rc.senseRobotAtLocation(i.loc);
          // If it *is* an EC, we already found it and reported it above
          if (r == null || r.type != ENLIGHTENMENT_CENTER) {
            Model.wrongSymmetry(null, false);
            queue.add(new Flag(Flag.Type.WrongSymmetry, 0));
            ec_noticed_wrong_sym = false;
            break;
          }
        }
      }
    }

    // Check for edges ourselves
    int sensor_radius = (int) Math.sqrt(rc.getType().sensorRadiusSquared);
    if (Model.minX == null && !rc.onTheMap(loc.translate(-sensor_radius, 0))) {
      tryQueue(Model.findEdge(loc.translate(-sensor_radius, 0), 1, 0, false));
    }
    if (Model.maxX == null && !rc.onTheMap(loc.translate(sensor_radius, 0))) {
      tryQueue(Model.findEdge(loc.translate(sensor_radius, 0), -1, 0, false));
    }
    if (Model.minY == null && !rc.onTheMap(loc.translate(0, -sensor_radius))) {
      tryQueue(Model.findEdge(loc.translate(0, -sensor_radius), 0, 1, true));
    }
    if (Model.maxY == null && !rc.onTheMap(loc.translate(0, sensor_radius))) {
      tryQueue(Model.findEdge(loc.translate(0, sensor_radius), 0, -1, true));
    }

    if (Robot.muckraker != null)
      if (Model.addMuck(Robot.muckraker))
        needs_reporting = true;
    if (Robot.reinforce_loc != null) {
      Model.addMuck(Robot.reinforce_loc);
      Robot.reinforce_loc = null;
    }
    closest_muck = Model.updateMucks();
  }

  static MapLocation closest_muck = null;
  static boolean needs_reporting;

  /**
   * Adds the given flag to the queue only if it isn't *null*.
   */
  static void tryQueue(Flag flag) {
    if (flag != null)
      queue.add(flag);
  }

  static int counter = 0;
  static boolean scary_muk = false;

  public static void showFlag() throws GameActionException {
    if (ec == null)
      return;

    // Tell the EC about nearby muckrakers
    if (muckraker != null && needs_reporting) {
      scary_muk = true;
      needs_reporting = false;
      rc.setFlag(new Flag(Flag.Type.Muckraker, (rc.getType() == SLANDERER || !friendly_slanderers.isEmpty()), muckraker)
          .encode(ec, rc.getType() == SLANDERER));
      rc.setIndicatorLine(rc.getLocation(), muckraker, 0, 0, 255);
      return;
    } else if (muckraker == null && scary_muk) {
      // If we're currently showing a flag reporting a nearby muck, but it's gone, get
      // rid of the flag
      scary_muk = false;
      rc.setFlag(new Flag(Flag.Type.None).encode(ec, rc.getType() == SLANDERER));
    }

    // Make sure to display the flag for enough turns that our home EC sees it
    int nturns = rc.getRobotCount() / 200;
    if (counter < nturns) {
      counter++;
      return;
    }

    if (!queue.isEmpty()) {
      Flag next = queue.remove();

      switch (next.type) {
      case AdoptMe:
        if (pending_id != next.id
            || !pending_ec.isWithinDistanceSquared(rc.getLocation(), ENLIGHTENMENT_CENTER.sensorRadiusSquared)) {
          return;
        }
        ec = pending_ec;
        ec_id = pending_id;
        // Get rid of the symmetry the old EC thought so we don't tell the new one it's
        // wrong
        Model.wrongSymmetry(null, false);
        break;
      case None:
      case Muckraker:
        break;

      case WrongSymmetry:
        if (ec_noticed_wrong_sym || pending_id != ec_id)
          return;
        System.out.println("Wrong symmetry for EC " + ec_id);
      default:
        counter = 0;
      }

      rc.setFlag(next.encode(ec, rc.getType() == SLANDERER));
    } else {
      rc.setFlag(new Flag(Flag.Type.None).encode(ec, rc.getType() == SLANDERER));
    }
  }

  static void turn() throws GameActionException {
    updateComms();

    switch (rc.getType()) {
    case SLANDERER:
      Slanderer.turn();
      break;
    case POLITICIAN:
      Politician.turn();
      break;
    case MUCKRAKER:
      Muckraker.turn();
      break;
    default:
      System.out.println("NO MOVEMENT CODE FOR " + rc.getType());
      rc.resign();
    }

    showFlag();
  }

  public static void init(RobotController rc) {
    Model.init(rc);
    team = rc.getTeam();
    ec_income = (int) Math.ceil(0.2 * Math.sqrt(rc.getRoundNum()));

    switch (rc.getType()) {
    // For a slanderer, initialize both slanderer and politician code
    case SLANDERER:
      Slanderer.init(rc);
    case POLITICIAN:
      Politician.init(rc);
      break;
    case MUCKRAKER:
      Muckraker.init(rc);
      break;
    default:
      System.out.println("NO MOVEMENT CODE FOR " + rc.getType());
      rc.resign();
    }
    Robot.rc = rc;
    retarget_acc = rc.getID();

    try {
      if (rc.getType() == SLANDERER)
        rc.setFlag(new Flag(Flag.Type.None).encode(rc.getLocation(), true));
    } catch (GameActionException e) {
      e.printStackTrace();
    }
  }

  public static void run(RobotController rc) {
    init(rc);

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
