package redarmy;

import battlecode.common.*;

/*
 * Unlike in the other bot, flags aren't just one message, they also have static components - currently just whether it's a slanderer.
 * Also, bots don't repeat them endlessly like before - they wait for another bot to copy them, then get rid of it (well, they will eventually).
 * So, no timestamps needed.
 *
 * 0 0000 0000 0 00 000000 000000
 * ~ ~~~~      ~ ~~~~~~~~~~~~~~~~--the location, like in the other bot
 * | |            |
 * | header       aux flag, like the other bot
 * if it's a slanderer
 *
 */
public class Flag {
  public boolean is_slanderer;
  public MapLocation loc;
  public boolean aux_flag;
  public Type type;

  public Flag(boolean is_slanderer, Type type) {
    this.is_slanderer = is_slanderer;
    this.type = type;
    this.aux_flag = false;
    this.loc = null;
  }

  public Flag(boolean is_slanderer, Type type, boolean aux_flag) {
    this.is_slanderer = is_slanderer;
    this.type = type;
    this.aux_flag = aux_flag;
    this.loc = null;
  }

  public Flag(boolean is_slanderer, Type type, MapLocation loc) {
    this.is_slanderer = is_slanderer;
    this.type = type;
    this.aux_flag = false;
    this.loc = loc;
  }

  public Flag(boolean is_slanderer, Type type, boolean aux_flag, MapLocation loc) {
    this.is_slanderer = is_slanderer;
    this.type = type;
    this.aux_flag = aux_flag;
    this.loc = loc;
  }

  public enum Type {
    None,
    /**
     * We found an enemy EC at a location.
     */
    EnemyEC,
    /**
     * An EC that previously belonged to the enemy now belongs to us.
     */
    ConvertF;

    public static Type decode(int header) {
      switch (header) {
      case 0:
        return Type.None;
      case 1:
        return Type.EnemyEC;
      case 2:
        return Type.ConvertF;
      default:
        throw new RuntimeException("Can't decode header " + header);
      }
    }

    public int encode() {
      switch (this) {
      case None:
        return 0;
      case EnemyEC:
        return 1;
      case ConvertF:
        return 2;
      }
      throw new RuntimeException("That's not possible");
    }
  }

  static final int FLAG_SIZE = 24;
  static final int TYPE_BITS = 4;
  /**
   * How many we need to shift the aux_flag left, or equivalently how many bits
   * are to the right of the aux flag
   */
  static final int AUX_FLAG_SHIFT = 14;
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
  static final int FLAG_XOR_KEY = 414455;

  public int encode(MapLocation unit_loc) {
    int flag = (is_slanderer ? 1 : 0) << (FLAG_SIZE - 1);

    int sig = type.encode();
    flag |= sig << (FLAG_SIZE - TYPE_BITS - 1);

    flag |= (aux_flag ? 1 : 0) << AUX_FLAG_SHIFT;

    if (loc != null) {
      // Switch to relative coordinates (-63..63)
      // Then extract the signs so the values are in (0..63)
      int x = loc.x - unit_loc.x;
      int y = loc.y - unit_loc.y;
      int sx = x < 0 ? 0 : 1;
      int sy = y < 0 ? 0 : 1;
      x = Math.abs(x);
      y = Math.abs(y);
      // In case the math is wrong
      if (x > 63 || x < 0 || y > 63 || y < 0)
        throw new RuntimeException(
            "Math is wrong! flag = " + loc + ", unit = " + unit_loc + ", relative = (" + x + ", " + y + ")");

      flag |= sx << 13;
      flag |= sy << 12;
      flag |= x << 6;
      flag |= y;
    }

    // And finally, encrypt it
    flag ^= FLAG_XOR_KEY;

    return flag;
  }

  public static Flag decode(MapLocation unit_loc, int flag) {
    // First, decrypt the flag
    flag ^= FLAG_XOR_KEY;

    boolean is_slanderer = (flag >> (FLAG_SIZE - 1)) != 0;

    int sig = flag >> (FLAG_SIZE - TYPE_BITS - 1);
    sig &= 0b1111;
    Type type = Type.decode(sig);

    if (type == Type.None)
      return new Flag(is_slanderer, type);

    boolean aux_flag = (flag & (1 << AUX_FLAG_SHIFT)) != 0;

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
    MapLocation loc = new MapLocation(x, y);

    return new Flag(is_slanderer, type, aux_flag, loc);
  }
}
