package starfleet;

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
 *
 * For neutral ECs, we also report the influence, using the four bits after the header and the aux flag, and multiplying that number by 25.
 */
public class Flag {
  public boolean aux_flag = false;
  public MapLocation loc = null;
  public Integer id = null;
  public Type type;
  public Integer influence = null;

  public static Flag neutralEC(MapLocation loc, int influence) {
    Flag flag = new Flag(Type.NeutralEC, loc);
    flag.influence = influence;
    return flag;
  }

  public Flag(Type type, int id) {
    this.type = type;
    this.id = id;
  }

  public Flag(Type type) {
    this.type = type;
  }

  public Flag(Type type, boolean aux_flag) {
    this.type = type;
    this.aux_flag = aux_flag;
  }

  public Flag(Type type, MapLocation loc) {
    this.type = type;
    this.loc = loc;
  }

  public Flag(Type type, boolean aux_flag, MapLocation loc) {
    this.type = type;
    this.aux_flag = aux_flag;
    this.loc = loc;
  }

  public enum Type {
    None,
    /**
     * We found an enemy EC at a location. If the aux flag isn't set, we're only
     * guessing that there's an enemy EC at this location based on symmetry.
     */
    EnemyEC,
    /**
     * We found a neutral EC, and attach its location and influence.
     */
    NeutralEC,
    /**
     * We found a new friendly EC, and attach its ID.
     */
    FriendlyEC,
    /**
     * We're registering that an EC has converted from enemy or neutral to friendly,
     * with location.
     */
    ConvertF,
    /**
     * A message meant to be read by a friendly EC in range, identified by its ID.
     * That EC should take control of this unit.
     */
    AdoptMe,
    /**
     * There's a muckraker at a location, and the aux flag tells whether there's a
     * nearby slanderer or not.
     */
    Muckraker,
    /**
     * The same as Muckraker, but we're copying it from another EC, so further ECs
     * shouldn't copy it from us.
     */
    Muckraker2,
    /**
     * We found an edge, and enclose its location and, in the aux flag, whether it's
     * a Y edge.
     */
    Edge,
    /**
     * EC: Sending the X coordinate of our location as an absolute number in the ID
     * slot.
     */
    MyLocationX,
    /**
     * EC: Sending the Y coordinate of our location as an absolute number in the ID
     * slot.
     */
    MyLocationY,
    /**
     * While we didn't actually see an EC, there are lots of enemies nearby
     * concentrated somewhere around this location.
     */
    EnemyCenter,
    /**
     * We came across a location where we guessed there was an enemy EC, but it's
     * not there. If nonzero, the ID slot is used to indicate which symmetry we know
     * is wrong.
     */
    WrongSymmetry,
    /**
     * EC: sending out our income last turn (in the ID slot), so pols can use it to
     * calculate things.
     */
    Income;

    public static Type decode(int header) {
      switch (header) {
      case 0:
        return None;
      case 1:
        return EnemyEC;
      case 2:
        return NeutralEC;
      case 3:
        return FriendlyEC;
      case 4:
        return ConvertF;
      case 5:
        return AdoptMe;
      case 6:
        return Muckraker;
      case 7:
        return Muckraker2;
      case 8:
        return Edge;
      case 9:
        return MyLocationX;
      case 10:
        return MyLocationY;
      case 11:
        return EnemyCenter;
      case 12:
        return WrongSymmetry;
      case 13:
        return Income;
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
      case NeutralEC:
        return 2;
      case FriendlyEC:
        return 3;
      case ConvertF:
        return 4;
      case AdoptMe:
        return 5;
      case Muckraker:
        return 6;
      case Muckraker2:
        return 7;
      case Edge:
        return 8;
      case MyLocationX:
        return 9;
      case MyLocationY:
        return 10;
      case EnemyCenter:
        return 11;
      case WrongSymmetry:
        return 12;
      case Income:
        return 13;
      }
      throw new RuntimeException("That's not possible");
    }

    public boolean hasID() {
      switch (this) {
      case FriendlyEC:
      case AdoptMe:
      case MyLocationX:
      case MyLocationY:
      case WrongSymmetry:
      case Income:
        return true;
      default:
        return false;
      }
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

  public static Flag decode(MapLocation home_ec, int flag) {
    // First, decrypt the flag
    flag ^= FLAG_XOR_KEY;

    // Ignore the slanderer flag here

    int sig = flag >> (FLAG_SIZE - TYPE_BITS - 1);
    sig &= 0b1111;
    Type type = Type.decode(sig);

    if (type == Type.None)
      return new Flag(type);
    else if (type.hasID()) {
      int id = flag & ID_MASK;
      return new Flag(type, id);
    }

    // At this point, we know it has a location, so if our reference point is null
    // we won't be able to decode it
    if (home_ec == null)
      return null;

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

    if (type == Type.NeutralEC) {
      int influence = (flag >> AUX_FLAG_SHIFT) & 0b11111;
      influence *= 25;
      return neutralEC(loc, influence);
    }

    return new Flag(type, aux_flag, loc);
  }

  public int encode(MapLocation home_ec, boolean is_slanderer) {
    int flag = (is_slanderer ? 1 : 0) << (FLAG_SIZE - 1);

    int sig = type.encode();
    flag |= sig << (FLAG_SIZE - TYPE_BITS - 1);

    if (type == Type.NeutralEC) {
      flag |= (influence / 25) << AUX_FLAG_SHIFT;
    } else {
      flag |= (aux_flag ? 1 : 0) << AUX_FLAG_SHIFT;
    }

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
