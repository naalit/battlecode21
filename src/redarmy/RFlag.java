package redarmy;

import battlecode.common.*;

/*
 * This class represents flags *as sent by a robot*, not an EC.
 *
 * Unlike in the other bot, flags aren't just one message, they also have static components - currently just whether it's a slanderer.
 * That's currently the only component read by robots around them, the rest being read by the EC that spawned the robot.
 *
 * 0 0000 0000 0 00 000000 000000
 * ~ ~~~~      ~ ~~~~~~~~~~~~~~~~--the location, like in the other bot, but relative to this robot's home EC
 * | |         |
 * | header    aux flag, like the other bot
 * if it's a slanderer
 *
 * Alternatively, the aux flag bit + the location bit can be the ID of a unit (probably EC), depending on the flag type.
 */
public class RFlag {
  public boolean aux_flag = false;
  public MapLocation loc = null;
  public Integer id = null;
  public Type type;

  public RFlag(Type type, int id) {
    this.type = type;
    this.id = id;
  }

  public RFlag(Type type) {
    this.type = type;
  }

  public RFlag(Type type, boolean aux_flag) {
    this.type = type;
    this.aux_flag = aux_flag;
  }

  public RFlag(Type type, MapLocation loc) {
    this.type = type;
    this.loc = loc;
  }

  public RFlag(Type type, boolean aux_flag, MapLocation loc) {
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
     * We found a new friendly EC, and attach its ID.
     */
    FriendlyEC,
    /**
     * A message meant to be read by a friendly EC in range, attaching this robot's
     * home EC's ID.
     */
    HelloEC,
    /**
     * We're registering that an EC has converted from enemy to friendly.
     */
    ConvertF;

    public static Type decode(int header) {
      switch (header) {
      case 0:
        return Type.None;
      case 1:
        return Type.EnemyEC;
      case 2:
        return Type.FriendlyEC;
      case 3:
        return Type.HelloEC;
      case 4:
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
      case FriendlyEC:
        return 2;
      case HelloEC:
        return 3;
      case ConvertF:
        return 4;
      }
      throw new RuntimeException("That's not possible");
    }

    public boolean hasID() {
      return this == Type.FriendlyEC || this == Type.HelloEC;
    }
  }

  static final int FLAG_SIZE = 24;
  static final int TYPE_BITS = 4;
  public static final int HEADER_MASK = 0b1111 << (FLAG_SIZE - TYPE_BITS - 1);
  static final int ID_MASK = (1 << 15) - 1;
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

  public static boolean isSlanderer(int flag) {
    return (flag >> (FLAG_SIZE - 1)) != 0;
  }

  public static boolean isNone(int flag) {
    return (flag & HEADER_MASK) == 0;
  }

  public static Type getType(int flag) {
    return Type.decode((flag >> (FLAG_SIZE - TYPE_BITS - 1)) & 0b1111);
  }

  public static RFlag decode(MapLocation home_ec, int flag) {
    // First, decrypt the flag
    flag ^= FLAG_XOR_KEY;

    // Ignore the slanderer flag here

    int sig = flag >> (FLAG_SIZE - TYPE_BITS - 1);
    sig &= 0b1111;
    Type type = Type.decode(sig);

    if (type == Type.None)
      return new RFlag(type);
    else if (type.hasID()) {
      int id = flag & ID_MASK;
      return new RFlag(type, id);
    }

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
    MapLocation loc = home_ec.translate(x, y);

    return new RFlag(type, aux_flag, loc);
  }

  public int encode(MapLocation home_ec, boolean is_slanderer) {
    int flag = (is_slanderer ? 1 : 0) << (FLAG_SIZE - 1);

    int sig = type.encode();
    flag |= sig << (FLAG_SIZE - TYPE_BITS - 1);

    flag |= (aux_flag ? 1 : 0) << AUX_FLAG_SHIFT;

    if (loc != null) {
      // Switch to relative coordinates (-63..63)
      // Then extract the signs so the values are in (0..63)
      int x = loc.x - home_ec.x;
      int y = loc.y - home_ec.y;
      int sx = x < 0 ? 0 : 1;
      int sy = y < 0 ? 0 : 1;
      x = Math.abs(x);
      y = Math.abs(y);
      // In case the math is wrong
      if (x > 63 || x < 0 || y > 63 || y < 0)
        throw new RuntimeException(
            "Math is wrong! flag = " + loc + ", home = " + home_ec + ", relative = (" + x + ", " + y + ")");

      flag |= sx << 13;
      flag |= sy << 12;
      flag |= x << 6;
      flag |= y;
    } else if (id != null) {
      // It's the last 15 bits, so just OR it
      flag |= id;
    }

    // And finally, encrypt it
    flag ^= FLAG_XOR_KEY;

    return flag;
  }
}
