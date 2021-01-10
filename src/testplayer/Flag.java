package testplayer;

import battlecode.common.*;

/*
 * A flag looks like this:
 * 0000 00000 0 00 000000 000000
 * ~~~~ ~~~~~ ~ ~~ ~~~~~~ ~~~~~~ y coordinate of location
 * |    |     | |     \ x coordinate of location
 * | timestamp| sign of x and y coordinates (0 = negative, 1 = positive)
 * |         aux_flag, an extra boolean
 * header, representing the flag type (4 bits = 16 types, may increase later)
 */
public class Flag {
  final int FLAG_SIZE = 24;
  final int TYPE_BITS = 4;
  /**
   * How many we need to shift the aux_flag left, or equivalently how many bits
   * are to the right of the aux flag
   */
  final int AUX_FLAG_SHIFT = 14;
  final int TIMESTAMP_SHIFT = 15;
  final int TIMESTAMP_BITS = 5;
  /**
   * The timestamp is the number of turns it's been since the information was last
   * validated. It can be zero, if we found it this turn. If it's more than 30, we
   * set it to 31.
   */
  final int MAX_TIMESTAMP = 30;
  /**
   * We XOR each flag with this constant key for a little bit of encryption. It's
   * not much, but at least we don't have raw locations in our flags. I just
   * generated a random number between 0 and 2^20=104876. That way, it doesn't
   * obscure the headers, which is important because if we don't set a flag it's
   * 0, which has the header None.
   *
   * Here it is in binary:
   *
   * 0000 01100 1 01 001011 110111
   */
  final int FLAG_XOR_KEY = 414455;

  public enum Type {
    /** Header 0. */
    None,
    /**
     * Header 1. A message saying we found a new map edge. If the aux_flag is set,
     * it's in the y coordinate, otherwise it's x. Whether it's the lower or upper
     * edge is obvious by comparing to any unit location.
     */
    Edge,
    /**
     * Header 2. A message saying we found an enemy or neutral Enlightenment Center,
     * with its location.
     */
    EnemyEC,
    /**
     * Header 3. A message saying we found an EC that belongs to us, which may have
     * previously belonged to someone else.
     */
    FriendlyEC,
  }

  /**
   * The location of the unit sending the flag, which locations are relative to
   */
  MapLocation unit_loc;
  Type type;
  /**
   * Used in some flag types, like saying whether something is a slanderer.
   */
  boolean aux_flag = false;
  /**
   * Most flag types include a location, which is stored here in absolute
   * coordinates but broadcast in relative ones.
   *
   * If this is a Type.Edge, only one of the coordinates is valid.
   */
  MapLocation flag_loc;
  /**
   * This is the turn number the information was last validated.
   */
  int timestamp;

  public int encode(int turn) {
    int sig = 0;
    switch (type) {
    case None:
      sig = 0;
      break;
    case Edge:
      sig = 1;
      break;
    case EnemyEC:
      sig = 2;
      break;
    case FriendlyEC:
      sig = 3;
      break;
    }
    int flag = sig << (FLAG_SIZE - TYPE_BITS);

    int trel = turn - timestamp;
    if (trel > MAX_TIMESTAMP)
      trel = MAX_TIMESTAMP + 1;
    flag |= trel << TIMESTAMP_SHIFT;

    flag |= (aux_flag ? 1 : 0) << AUX_FLAG_SHIFT;

    if (flag_loc != null) {
      // Switch to relative coordinates (-63..63)
      // Then extract the signs so the values are in (0..63)
      int x = flag_loc.x - unit_loc.x;
      int y = flag_loc.y - unit_loc.y;
      int sx = x < 0 ? 0 : 1;
      int sy = y < 0 ? 0 : 1;
      x = Math.abs(x);
      y = Math.abs(y);
      // In case the math is wrong
      if (x > 63 || x < 0 || y > 63 || y < 0)
        throw new RuntimeException(
            "Math is wrong! flag = " + flag_loc + ", unit = " + unit_loc + ", relative = (" + x + ", " + y + ")");

      flag |= sx << 13;
      flag |= sy << 12;
      flag |= x << 6;
      flag |= y;
    }

    // And finally, encrypt it
    flag ^= FLAG_XOR_KEY;

    return flag;
  }

  public Flag resend(MapLocation new_unit_loc) {
    return new Flag(new_unit_loc, type, aux_flag, flag_loc, timestamp);
  }

  public Flag(MapLocation unit_loc, Type type, int timestamp) {
    this(unit_loc, type, false, null, timestamp);
  }

  public Flag(MapLocation unit_loc, Type type, MapLocation flag_loc, int timestamp) {
    this(unit_loc, type, false, flag_loc, timestamp);
  }

  public Flag(MapLocation unit_loc, Type type, boolean aux_flag, MapLocation flag_loc, int timestamp) {
    this.unit_loc = unit_loc;
    this.type = type;
    this.aux_flag = aux_flag;
    this.flag_loc = flag_loc;
    this.timestamp = timestamp;
  }

  public Flag(MapLocation unit_loc, int turn, int flag) {
    // First, decrypt the flag
    flag ^= FLAG_XOR_KEY;

    this.unit_loc = unit_loc;

    int sig = flag >> (FLAG_SIZE - TYPE_BITS);
    switch (sig) {
    case 0:
      type = Type.None;
      break;
    case 1:
      type = Type.Edge;
      break;
    case 2:
      type = Type.EnemyEC;
      break;
    case 3:
      type = Type.FriendlyEC;
      break;
    default:
      throw new RuntimeException("Unknown type " + sig);
    }

    int trel = (flag >> TIMESTAMP_SHIFT) & MAX_TIMESTAMP + 1;
    if (trel == MAX_TIMESTAMP + 1)
      // Lower bound on the timestamp
      timestamp = 0;
    else
      timestamp = turn - trel;

    aux_flag = (flag & (1 << AUX_FLAG_SHIFT)) != 0;

    // Extract the x and y coordinates
    int y = flag & 0b111111;
    int x = (flag >> 6) & 0b111111;
    // Add the signs
    int sx = flag & (1 << 13);
    int sy = flag & (1 << 12);
    if (sx == 0)
      x = -x;
    if (sy == 0)
      y = -y;

    // And switch back to absolute coordinates
    y += unit_loc.y;
    x += unit_loc.x;
    flag_loc = new MapLocation(x, y);
  }

  @Override
  public String toString() {
    return "Flag { loc: " + flag_loc + ", type: " + type + ", aux_flag: " + aux_flag + " }";
  }
}
