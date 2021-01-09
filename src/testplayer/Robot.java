package testplayer;

import battlecode.common.*;

/**
 * An extension of RobotController with more functionality
 */
public class Robot {
  RobotController rc;
  /**
   * How far away we can sense in a straight line
   */
  int sensor_radius;

  public Robot(RobotController rc) {
    this.rc = rc;
    sensor_radius = (int) Math.sqrt((double) rc.getType().sensorRadiusSquared);
  }

  int getEnemyVotes() {
    // This works, as long as we bid at least 1 every round
    return getTurn() - getTeamVotes();
  }

  /** How many votes does this robot's team have? */
  int getTeamVotes() {
    return rc.getTeamVotes();
  }

  /**
   * How many turns have passed, so 0 on the first turn and increasing by one
   * after that.
   */
  int getTurn() {
    return rc.getRoundNum() - 1;
  }

  MapLocation getLocation() {
    return rc.getLocation();
  }

  int getID() {
    return rc.getID();
  }

  int getInfluence() {
    return rc.getInfluence();
  }

  // -- COMMUNICATION, ETC. -- //

  MessageQueue queue = new MessageQueue();
  public MapLocation[] enemy_ecs = {};
  public RobotInfo[] nearby;
  public Integer minX = null;
  public Integer minY = null;
  public Integer maxX = null;
  public Integer maxY = null;

  void setEdge(boolean is_y, MapLocation flag_loc, MapLocation unit_loc) {
    if (is_y) {
      // It's possible that unit is *at* the edge, so flag_loc.y = unit_loc.y;
      // but if so, this unit *isn't* at the edge, so we use that instead.
      if (flag_loc.y < unit_loc.y || flag_loc.y < getLocation().y) {
        // If we've already seen this edge, don't relay it further; we don't want
        // infinite loops.
        if (minY != null)
          return;
        minY = flag_loc.y;
      } else {
        if (maxY != null)
          return;
        maxY = flag_loc.y;
      }
    } else {
      if (flag_loc.x < unit_loc.x || flag_loc.x < getLocation().x) {
        if (minX != null)
          return;
        minX = flag_loc.x;
      } else {
        if (maxX != null)
          return;
        maxX = flag_loc.x;
      }
    }

    // System.out.println("Found edge " + (is_y ? "y" : "x") + " at " + flag_loc);

    // Relay the message on so all units hear about the edge
    queue.enqueue(new Flag(getLocation(), Flag.Type.Edge, is_y, flag_loc), MessageQueue.Priority.Low);
  }

  void findEdge(MapLocation start, int dx, int dy, boolean is_y) throws GameActionException {
    while (!rc.onTheMap(start)) {
      start = start.translate(dx, dy);
    }
    setEdge(is_y, start, getLocation());
  }

  void addEC(MapLocation ec) {
    MapLocation[] new_arr = new MapLocation[enemy_ecs.length + 1];
    for (int i = 0; i < enemy_ecs.length; i++) {
      MapLocation l = enemy_ecs[i];
      if (l.equals(ec))
        return;
      new_arr[i] = l;
    }
    new_arr[enemy_ecs.length] = ec;
    enemy_ecs = new_arr;

    System.out.println("Enemy EC at " + ec);

    queue.enqueue(new Flag(getLocation(), Flag.Type.EnemyEC, ec), MessageQueue.Priority.Medium);
  }

  /**
   * Should generally be called every turn - updates `nearby`, reads unit flags,
   * checks for the edge of the map, etc. Call `finish` at the end of the turn,
   * too.
   */
  void update() {
    try {

      nearby = rc.senseNearbyRobots();
      for (RobotInfo i : nearby) {
        if (i.team == rc.getTeam()) {
          Flag flag = new Flag(i.location, rc.getFlag(i.ID));
          switch (flag.type) {
          case Edge:
            setEdge(flag.aux_flag, flag.flag_loc, i.location);
            break;

          case EnemyEC:
            addEC(flag.flag_loc);
            break;

          case None:
            break;
          }
        } else if (i.type == RobotType.ENLIGHTENMENT_CENTER) {
          addEC(i.location);
        }
      }

      // Check for edges ourselves
      MapLocation loc = getLocation();
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

    } catch (GameActionException e) {
      e.printStackTrace();
    }
  }

  /**
   * Finishes the turn, sending the next message in the queue. Must be called
   * *after* moving, or the relative coordinates in the message will be wrong.
   */
  void finish() {
    try {

      // Don't send a message unless there's somebody around to hear it
      boolean should_send = false;
      for (RobotInfo i : nearby) {
        if (i.team == rc.getTeam() && i.location.isWithinDistanceSquared(getLocation(), i.type.sensorRadiusSquared)) {
          should_send = true;
        }
      }

      if (should_send) {
        // Send the next message
        Flag flag = queue.next();
        if (flag == null)
          flag = new Flag(getLocation(), Flag.Type.None);
        // Our location might have changed since we enqueued it
        flag.unit_loc = getLocation();
        rc.setFlag(flag.encode());
      }

    } catch (GameActionException e) {
      e.printStackTrace();
    }
  }

  // -- TARGETING & PATHFINDING -- //

  MapLocation target;

  /**
   * Checks if the location is on the map, returning `true` if unsure. Uses the
   * edges we've found if possible.
   */
  boolean isOnMap(MapLocation loc) {
    try {
      if ((minY != null && loc.y < minY) || (maxY != null && loc.y > maxY) || (minX != null && loc.x < minX)
          || (maxX != null && loc.x > maxX))
        return false;
      if (minY == null || maxY == null || minX == null || maxX == null) {
        return (!loc.isWithinDistanceSquared(getLocation(), rc.getType().sensorRadiusSquared) || rc.onTheMap(loc));
      } else {
        // We know the edges, no need to consult `rc`.
        return false;
      }
    } catch (GameActionException e) {
      e.printStackTrace();
      return true;
    }
  }

  void retarget() {
    // ID is probably 10,000..20,000
    // This should be fairly random
    int id = getID() ^ getTurn() ^ (getTurn() * 5);
    // Use the last 10 bits
    int sig = id & 1023;
    // Split into two, each in 0..31
    int x = sig & 31;
    int y = sig >> 5;
    // Now switch to -16..15
    x = x - 16;
    y = y - 16;
    target = getLocation().translate(x, y);
    rc.setIndicatorDot(target, 0, 255, 0);
  }

  int blocked_turns = 0;

  boolean target_move(boolean retarget_if_there) {
    try {
      MapLocation loc = getLocation();

      if (loc.equals(target)) {
        if (retarget_if_there) {
          retarget();
        } else {
          return false;
        }
      }

      // Retarget if:
      // 1. it's not on the map
      if (!isOnMap(target)) {
        retarget();
      } else if (loc.isWithinDistanceSquared(target, rc.getType().sensorRadiusSquared)) {
        // 2. it's blocked, and has been blocked for long enough that it probably won't
        // be unblocked soon.
        // If it's an enemy robot, go there anyway, we're probably trying to kill it.
        RobotInfo robot = rc.senseRobotAtLocation(target);
        if (robot != null && robot.team == rc.getTeam()) {
          blocked_turns += 1;
          if (blocked_turns > 10) {
            retarget();
          }
        } else {
          blocked_turns = 0;

          // 3. it's North of a friendly Enlightenment Center (so would block spawns)
          MapLocation south_p = target.translate(0, -1);
          if (rc.canSenseLocation(south_p)) {
            RobotInfo south = rc.senseRobotAtLocation(south_p);
            if (south != null && south.team == rc.getTeam() && south.type == RobotType.ENLIGHTENMENT_CENTER) {
              retarget();
            }
          }
        }
      }

      Direction to_dir = loc.directionTo(target);
      Direction dir = to_dir;

      // Try going around obstacles, first left, then right
      MapLocation next = loc.add(dir);
      // If the next location isn't on the map, don't move that way
      if (!rc.onTheMap(next)) {
        retarget();
        return false;
      }
      if (rc.isLocationOccupied(next)) {
        dir = to_dir.rotateLeft();
      }
      next = loc.add(dir);
      // If the next location isn't on the map, don't move that way
      if (!rc.onTheMap(next)) {
        return false;
      }
      if (rc.isLocationOccupied(next)) {
        dir = to_dir.rotateRight();
      }

      return move(dir);

    } catch (GameActionException e) {
      System.out.println("Unreachable!");
      e.printStackTrace();
      return false;
    }
  }

  // -- ACTIONS -- //

  boolean expose(MapLocation loc) {
    if (rc.canExpose(loc)) {
      try {
        rc.expose(loc);
      } catch (GameActionException e) {
        System.out.println("Unreachable");
      }
      return true;
    } else {
      return false;
    }
  }

  boolean empower(int rad2) {
    if (rc.canEmpower(rad2)) {
      try {
        rc.empower(rad2);
      } catch (GameActionException e) {
        System.out.println("Unreachable");
      }
      return true;
    } else {
      return false;
    }
  }

  boolean bid(int bid) {
    if (rc.canBid(bid)) {
      try {
        rc.bid(bid);
      } catch (GameActionException e) {
        System.out.println("Unreachable");
      }
      return true;
    } else {
      return false;
    }
  }

  boolean move(Direction dir) {
    if (rc.canMove(dir)) {
      try {
        rc.move(dir);
      } catch (GameActionException e) {
        System.out.println("Unreachable");
      }
      return true;
    } else {
      return false;
    }
  }

  boolean build(RobotType type, Direction dir, int influence) {
    if (rc.canBuildRobot(type, dir, influence)) {
      try {
        rc.buildRobot(type, dir, influence);
      } catch (GameActionException e) {
        System.out.println("Unreachable");
      }
      return true;
    } else {
      return false;
    }
  }
}
