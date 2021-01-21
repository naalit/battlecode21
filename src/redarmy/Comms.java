package redarmy;

import java.util.ArrayDeque;
import java.util.ArrayList;
import battlecode.common.*;
import static battlecode.common.RobotType.*;

public class Comms {
  static ArrayDeque<Flag> queue = new ArrayDeque<>();
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
  static MapLocation pending_ec;
  static Integer pending_id;
  static boolean was_empty = true;
  static int id;
  static double avg_sin = 0, avg_cos = 0;
  static boolean has_seen_enemy = false;

  public static void start(RobotController rc) {
    Model.init(rc);
    Comms.rc = rc;
    team = rc.getTeam();
    try {
      if (rc.getType() == SLANDERER)
        rc.setFlag(new Flag(Flag.Type.None).encode(rc.getLocation(), true));
    } catch (GameActionException e) {
      e.printStackTrace();
    }
  }

  static boolean has_reinforced = false;
  static MapLocation reinforce_loc = null;
  static MapLocation muckraker = null;
  static MapLocation cvt_loc = null;

  /**
   * Puts nearby units into `nearby`, reads flags, and updates the list of enemy
   * ECs.
   */
  public static void update() throws GameActionException {
    int start_round = rc.getRoundNum();

    if (ec_id != null) {
      rc.setIndicatorLine(rc.getLocation(), ec, 128, 128, 0);
      try {
        Flag flag = Flag.decode(ec, rc.getFlag(ec_id));
        switch (flag.type) {
        case EnemyEC: {
          ECInfo ecif = new ECInfo(flag.loc);
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
        case Reinforce:
        case Reinforce2:
          reinforce_loc = flag.loc;
          has_reinforced = true;
          break;

        // As a robot, we know our home EC's location already
        case MyLocationX:
        case MyLocationY:
        case HelloEC:
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
          ECInfo ecif = new ECInfo(i.ID);
          ecif.loc = iloc;

          if (Model.addFriendlyEC(ecif)) {
            if (ec_id != null && ec_id != i.ID) {
              queue.add(new Flag(Flag.Type.FriendlyEC, i.ID));
            }
          }

          if (ec == null) {
            ec = iloc;
            ec_id = i.ID;
          } else if (ec_id != i.ID) {
            queue.add(new Flag(Flag.Type.HelloEC, ec_id));
            pending_ec = iloc;
            pending_id = i.ID;
            cvt_loc = null;
          }

          if (Model.enemy_ecs.remove(ecif) || Model.neutral_ecs.remove(ecif)) {
            rc.setIndicatorLine(rc.getLocation(), iloc, 255, 0, 255);
            cvt_loc = iloc;
            queue.addFirst(new Flag(Flag.Type.ConvertF, iloc));
          }
        }

        // If we're out of time, give up so we don't try to read flags of a unit that's
        // now out of range
        if (rc.getRoundNum() != start_round || Clock.getBytecodesLeft() < 1000)
          break;

        if (Flag.isSlanderer(rc.getFlag(i.ID))) {
          friendly_slanderers.add(i);
          total_fslan_conv += i.conviction;
        }

      } else if (i.type == ENLIGHTENMENT_CENTER) {
        ECInfo ecif = new ECInfo(iloc, i.influence);
        ecif.id = i.ID;

        if (i.team == team.opponent()) {
          // It's an enemy EC
          Model.neutral_ecs.remove(ecif);
          if (Model.addEnemyEC(ecif)) {
            queue.add(new Flag(Flag.Type.EnemyEC, iloc));
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

    // Check for edges ourselves
    int sensor_radius = (int) Math.sqrt(rc.getType().sensorRadiusSquared);
    MapLocation loc = rc.getLocation();
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
  }

  /**
   * Adds the given flag to the queue only if it isn't *null*.
   */
  static void tryQueue(Flag flag) {
    if (flag != null)
      queue.add(flag);
  }

  static int counter = 0;
  static boolean scary_muk = false;

  public static void finish() throws GameActionException {
    if (ec == null)
      return;

    // If we're a slanderer, tell the EC about nearby muckrakers
    if (muckraker != null && (rc.getType() == SLANDERER || !friendly_slanderers.isEmpty())) {
      scary_muk = true;
      rc.setFlag(new Flag(Flag.Type.Reinforce, muckraker).encode(ec, rc.getType() == SLANDERER));
      rc.setIndicatorLine(rc.getLocation(), muckraker, 0, 0, 255);
      return;
    }

    if (scary_muk) {
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
      rc.setFlag(next.encode(ec, rc.getType() == SLANDERER));

      switch (next.type) {
      case HelloEC:
        ec = pending_ec;
        ec_id = pending_id;
        break;
      case None:
      case Reinforce:
        break;
      default:
        counter = 0;
      }
    } else {
      rc.setFlag(new Flag(Flag.Type.None).encode(ec, rc.getType() == SLANDERER));
    }
  }
}
