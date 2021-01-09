package testplayer;

import battlecode.common.*;
import static battlecode.common.RobotType.*;

interface Unit {
  void turn();
}

public strictfp class RobotPlayer {
  static RobotController rc;

  static Unit unit;

  static final RobotType[] spawnableRobot = { RobotType.POLITICIAN, RobotType.POLITICIAN, RobotType.POLITICIAN,
      RobotType.SLANDERER, RobotType.SLANDERER, RobotType.MUCKRAKER, };

  static final Direction[] directions = { Direction.NORTH, Direction.NORTHEAST, Direction.EAST, Direction.SOUTHEAST,
      Direction.SOUTH, Direction.SOUTHWEST, Direction.WEST, Direction.NORTHWEST, };

  static int turnCount;

  /**
   * run() is the method that is called when a robot is instantiated in the
   * Battlecode world. If this method returns, the robot dies!
   **/
  @SuppressWarnings("unused")
  public static void run(RobotController rc) throws GameActionException {

    // This is the RobotController object. You use it to perform actions from this
    // robot,
    // and to get information on its current status.
    RobotPlayer.rc = rc;

    Robot r = new Robot(rc);

    boolean is_slanderer = false;

    switch (rc.getType()) {
    case ENLIGHTENMENT_CENTER:
      unit = new ECenter(r);
      break;
    case POLITICIAN:
      unit = new Politician(r);
      break;
    case SLANDERER:
      unit = new Slanderer(r);
      is_slanderer = true;
      break;
    case MUCKRAKER:
      unit = new Muckraker(r);
      break;
    }

    turnCount = 0;

    while (true) {
      turnCount += 1;
      if (is_slanderer && rc.getType() == POLITICIAN) {
        // It just converted, so switch the Unit
        // We're keeping the same Robot, so any information the slanderer knew, like
        // edges and enemy EC locations, the politician will as well
        unit = new Politician(r);
        is_slanderer = false;
      }

      // Run the unit-type-specific logic
      unit.turn();

      // Clock.yield() makes the robot wait until the next turn, then it will perform
      // this loop again
      Clock.yield();
    }
  }

  /**
   * Returns a random Direction.
   *
   * @return a random Direction
   */
  static Direction randomDirection() {
    return directions[(int) (Math.random() * directions.length)];
  }

  /**
   * Returns a random spawnable RobotType
   *
   * @return a random RobotType
   */
  static RobotType randomSpawnableRobotType() {
    return spawnableRobot[(int) (Math.random() * spawnableRobot.length)];
  }

  /**
   * Attempts to move in a given direction.
   *
   * @param dir The intended direction of movement
   * @return true if a move was performed
   * @throws GameActionException
   */
  static boolean tryMove(Direction dir) throws GameActionException {
    // System.out.println("I am trying to move " + dir + "; " + rc.isReady() + " " +
    // rc.getCooldownTurns() + " " + rc.canMove(dir));
    if (rc.canMove(dir)) {
      rc.move(dir);
      return true;
    } else
      return false;
  }
}
