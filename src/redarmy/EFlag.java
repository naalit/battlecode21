package redarmy;

import battlecode.common.*;

/*
 * This class represents flags sent by ECs.
 *
 * 0000 00000 0 00 000000 000000
 * ~~~~       ~ ~~~~~~~~~~~~~~~~--the location, like in the other bot, but relative to this robot's home EC
 * |          |
 * header     aux flag, like the other bot
 *
 * Alternatively, the aux flag bit + the location bit can be the ID of a unit
 * (probably EC), depending on the flag type.
 * That ID could also be a coordinate of an absolute location, used when ECs are
 * establishing a basis to communicate with each other.
 */
public class EFlag {
  public boolean aux_flag = false;
  public MapLocation loc = null;
  public Integer id = null;
  public Type type;

  public EFlag(Type type, int id) {
    this.type = type;
    this.id = id;
  }

  public EFlag(Type type) {
    this.type = type;
  }

  public EFlag(Type type, boolean aux_flag) {
    this.type = type;
    this.aux_flag = aux_flag;
  }

  public EFlag(Type type, MapLocation loc) {
    this.type = type;
    this.loc = loc;
  }

  public EFlag(Type type, boolean aux_flag, MapLocation loc) {
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
     * When we find a new EC, we need to send it our location so it can decode
     * relative locations. So this message type uses an absolute location, sent one
     * coordinate at a time in the ID slot.
     */
    MyLocationX,
    /**
     * Likewise for Y coordinate.
     */
    MyLocationY,
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
        return Type.MyLocationX;
      case 4:
        return Type.MyLocationY;
      case 5:
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
      case MyLocationX:
        return 3;
      case MyLocationY:
        return 4;
      case ConvertF:
        return 5;
      }
      throw new RuntimeException("That's not possible");
    }

    public boolean hasID() {
      return this == Type.FriendlyEC || this == Type.MyLocationX || this == Type.MyLocationY;
    }
  }

  static final int FLAG_SIZE = 24;
  static final int TYPE_BITS = 4;
  public static final int HEADER_MASK = 0b1111 << (FLAG_SIZE - TYPE_BITS);
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

  public static boolean isNone(int flag) {
    return (flag & HEADER_MASK) == 0;
  }

  public static Type getType(int flag) {
    return Type.decode(flag >> (FLAG_SIZE - TYPE_BITS));
  }

  public static EFlag decode(MapLocation ec_loc, int flag) {
    // First, decrypt the flag
    flag ^= FLAG_XOR_KEY;

    int sig = flag >> (FLAG_SIZE - TYPE_BITS);
    sig &= 0b1111;
    Type type = Type.decode(sig);

    if (type == Type.None)
      return new EFlag(type);
    else if (type.hasID()) {
      int id = flag & ID_MASK;
      return new EFlag(type, id);
    } else if (ec_loc == null) {
      return null;
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
    MapLocation loc = ec_loc.translate(x, y);

    return new EFlag(type, aux_flag, loc);
  }

  public int encode(MapLocation home_ec) {
    int sig = type.encode();
    int flag = sig << (FLAG_SIZE - TYPE_BITS);

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
