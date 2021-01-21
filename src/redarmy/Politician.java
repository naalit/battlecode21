package redarmy;

import java.util.ArrayList;
import battlecode.common.*;
import static battlecode.common.RobotType.*;

public class Politician {
  static RobotController rc;
  static Team team;
  static ArrayList<RobotInfo> affected = new ArrayList<>();

  static void init(RobotController rc) {
    Politician.rc = rc;
    team = rc.getTeam();
  }

  /**
   * Runs most of a politician's turn, after updating Robot. Figures out whether
   * to empower and where to move.
   */
  static void turn() throws GameActionException {
    MapLocation loc = rc.getLocation();

    // A politician has 2 goals:
    // 1. Deal damage to enemy units, especially ECs
    // 2. Protect friendly slanderers, and our ECs

    // When it's worth it to empower depends on how much conviction this unit has.
    // If it's <=10, empowering won't do anything, so we should just block things
    // and wait for others to possibly heal us to >10 conviction.

    // We'll start by looking at what's around us.
    // We want to figure out how many units total are in each possible empowering
    // radius, which damage would be divided between.
    final int[] radii = { 1, 2, 4, 9 }; // These are squared
    int[] rcounts = { 0, 0, 0, 0 };
    // We also keep track of how many politicians are near here, and their average
    // conviction
    // We'll see slanderers as politicians, so subtract those first
    int nfpols = -Robot.friendly_slanderers.size();
    int fpol_conv = -Robot.total_fslan_conv;
    affected.clear();
    // We also want to know where enemy muckrakers are, so we can protect slanderers
    // We just look at the closest muckraker
    RobotInfo muckraker = null;
    for (RobotInfo i : Robot.nearby) {
      int dist2 = i.location.distanceSquaredTo(loc);

      if (i.team != team && i.type == MUCKRAKER
          && (muckraker == null || dist2 < muckraker.location.distanceSquaredTo(loc)))
        muckraker = i;
      else if (i.team == team && i.type == POLITICIAN) {
        nfpols++;
        fpol_conv += i.conviction;
      }

      if (dist2 <= POLITICIAN.actionRadiusSquared) {
        // If it could recieve damage or healing from this politician, add it to
        // `affected` so we can process it later
        if (i.team != team || i.conviction < i.influence || i.type == ENLIGHTENMENT_CENTER)
          affected.add(i);

        for (int r = 0; r < rcounts.length; r++) {
          int r2 = radii[r];
          if (dist2 <= r2)
            rcounts[r]++;
        }
      }
    }

    // We also pick a slanderer to protect.
    // If we've found a muckraker, we pick the closest one to the muckraker, since
    // it's in the most danger.
    // Otherwise, we pick the closest one to us.
    RobotInfo slanderer = null;
    MapLocation rloc = (muckraker == null) ? loc : muckraker.location;
    for (RobotInfo i : Robot.friendly_slanderers) {
      if (slanderer == null || i.location.distanceSquaredTo(rloc) < slanderer.location.distanceSquaredTo(rloc))
        slanderer = i;
    }

    // If the muckraker will be able to kill the slanderer after moving, and we can
    // kill the muckraker, and the slanderer has higher influence than we have
    // conviction (which is usually true), then we need to kill the muckraker so it
    // doesn't kill the slanderer.
    MapLocation mucknext = muckraker == null || slanderer == null ? null
        : muckraker.location.add(muckraker.location.directionTo(slanderer.location));
    if (muckraker != null && slanderer != null
    // Either the muckraker is in kill distance now, or will be in after moving once
    // and can move
        && (muckraker.location.isWithinDistanceSquared(slanderer.location, MUCKRAKER.actionRadiusSquared)
            || (mucknext.isWithinDistanceSquared(slanderer.location, MUCKRAKER.actionRadiusSquared)
                && !rc.isLocationOccupied(mucknext)))
        // We're in kill distance of the muckraker now (radii[3] is the maximum radius
        // for empowering)
        && muckraker.location.isWithinDistanceSquared(loc, radii[3])
        // We can do damage
        && rc.getConviction() > 10
        // The slanderer is worth saving
        && slanderer.influence >= rc.getConviction()) {
      if (rc.canEmpower(radii[3])) {
        rc.empower(radii[3]);
        return;
      }
    }

    // If there's a muckraker threatening a slanderer, but we're not going to blow
    // it up right now, we should at least try to block it
    if (muckraker != null && slanderer != null
    // If there are a lot of politicians and we're fairly far away, it's another
    // politician's job
        && (nfpols < 10 || loc.isWithinDistanceSquared(muckraker.location, 36))) {
      Robot.target = mucknext;
      Robot.targetMove();
      return;
    }

    // Otherwise, we're not immediately worrying about protecting slanderers, so
    // figure out whether it's worth it to empower.

    // Calculate total damage done for each possible radius
    int useful_conv = (int) (rc.getConviction() * rc.getEmpowerFactor(rc.getTeam(), 0)) - 10;
    int[] totals = { 0, 0, 0, 0 };
    boolean[] hits_enemy = { false, false, false, false };
    for (RobotInfo i : affected) {
      int dist2 = i.location.distanceSquaredTo(loc);

      // Make sure pols don't crowd enemy ECs and not do anything
      if (i.team != team && i.type == ENLIGHTENMENT_CENTER && dist2 <= radii[0])
        totals[0] += useful_conv / 2;

      for (int r = 0; r < rcounts.length; r++) {
        int r2 = radii[r];
        if (dist2 <= r2) {
          hits_enemy[r] |= i.team != team;
          // Sometimes units end up with 0 conviction, but still need to be hit
          // Also, we assume remainder damage (if rcounts[r] > useful_conv) is given to
          // everyone, not in the order it actually is, for efficiency
          totals[r] += Math.max(1, Math.min(useful_conv / rcounts[r],
              // We can contribute as much as we want to friendly ECs, and extra damage done
              // to enemy ECs rolls over into when it's friendly
              (i.type == ENLIGHTENMENT_CENTER) ? 10000 :
              // Our units can only heal upto their cap
                  (i.team == team ? i.influence - i.conviction : i.conviction)));
        }
      }
    }

    // Find the radius with the highest total damage, but only count when we do
    // damage to enemies
    // Otherwise we'd be net losing money unless we have a big muckraker buff
    int max_r2 = 9;
    int max_damage = 0;
    for (int r = 0; r < totals.length; r++) {
      int r2 = radii[r];
      int damage = totals[r];
      if ((hits_enemy[r] || damage > rc.getConviction()) && damage > max_damage) {
        max_r2 = r2;
        max_damage = damage;
      }
    }

    // Empower if we'd be using at least half of our conviction, not counting tax;
    // or if there are a ton of politicians around and this one is at or below
    // average,
    // so its life isn't worth much
    int avg_conv = nfpols == 0 ? 0 : fpol_conv / nfpols;
    if ((useful_conv * 2 <= 3 * max_damage || (max_damage > 0 && nfpols > 12 && rc.getConviction() <= avg_conv))
        && rc.canEmpower(max_r2)) {
      rc.empower(max_r2);
      return;
    }

    // Find the closest neutral EC
    MapLocation target_ec = null;
    int ec_dist2 = 1000000;
    for (ECInfo eec : Model.neutral_ecs) {
      // Only go for it if we'll do at least half damage
      if (eec.influence >= (rc.getConviction() - 10) * 2)
        continue;
      int dist2 = eec.loc.distanceSquaredTo(rc.getLocation());// ec != null ? ec : rc.getLocation());
      if (target_ec == null || dist2 < ec_dist2) {
        target_ec = eec.loc;
        ec_dist2 = dist2;
      }
    }
    // If there aren't any neutral ECs, and we have >= 100 conv, target an enemy EC
    if (target_ec == null) {
      for (ECInfo eec : Model.enemy_ecs) {
        if (rc.getConviction() - 10 < 100)
          continue;
        int dist2 = eec.loc.distanceSquaredTo(rc.getLocation());// ec != null ? ec : rc.getLocation());
        if (target_ec == null || dist2 < ec_dist2) {
          target_ec = eec.loc;
          ec_dist2 = dist2;
        }
      }
    }

    // Now we've decided we're not empowering, so we can move.
    // If the EC has requested reinforcements somewhere, go there.
    if (Robot.reinforce_loc != null && Robot.reinforce_loc.isWithinDistanceSquared(loc, 256)
        && (nfpols > 4 || slanderer == null) && rc.getConviction() <= 200) {
      Robot.target = Robot.reinforce_loc;
      Robot.reinforce_loc = null;
      Robot.targetMove(true);
    } else if (!Robot.has_reinforced && (muckraker == null || slanderer == null) && ec_dist2 < 512
        && rc.getConviction() > 25) {
      Robot.target = target_ec;
      Robot.targetMove();
    } else if (Robot.ec != null && slanderer != null && (nfpols < 20 || rc.getConviction() <= 25)
        && (nfpols < 8 || rc.getConviction() < 100)) {
      // System.out.println("has_reinforced = " + Robot.has_reinforced + ", muckraker
      // = " + muckraker + ", slanderer = " + slanderer + ", ec_dist2 = " + ec_dist2);
      // If there are slanderers nearby, we want to be able to protect them, so stay
      // close. Unless there are too many politicians already here.
      // We want to be about 2 units away.
      // So, for a target, we circle the EC 2 units farther out than the slanderer we
      // picked earlier.
      double r = Math.sqrt(slanderer.location.distanceSquaredTo(Robot.ec));
      // Offset by 2.7 to make sure it rounds right
      Robot.circleEC(r + 2.7);
    } else if (Robot.ec != null && Robot.ec.isWithinDistanceSquared(rc.getLocation(), 100)
        && (nfpols < 20 || rc.getConviction() <= 25) && (nfpols < 8 || rc.getConviction() < 100)) {
      Robot.circleEC(4);
    } else {
      // If there aren't any slanderers nearby (or we just don't have an EC, which is
      // rare), we explore, targeting enemy ECs if available
      if (target_ec != null)
        Robot.target = target_ec;
      else if (Robot.cvt_loc != null)
        Robot.target = Robot.cvt_loc;
      Robot.targetMove(true);
    }
  }
}
