package testplayer;

import java.util.ArrayDeque;

/**
 * A priority queue for messages sent as flags.
 */
public class MessageQueue {
  public enum Priority {
    /**
     * High priority: the unit should disregard everything else and send the
     * message.
     */
    High,
    /**
     * Medium priority: it's time-sensitive, so sooner is better than later, but
     * it's not essential it arrives as soon as possible.
     */
    Medium,
    /**
     * Low priority: send whenever we happen to have a free turn.
     */
    Low,
  }

  private ArrayDeque<Flag> high = new ArrayDeque<Flag>();
  private ArrayDeque<Flag> med = new ArrayDeque<Flag>();
  private ArrayDeque<Flag> low = new ArrayDeque<Flag>();

  public boolean isEmpty() {
    return high.isEmpty() && med.isEmpty() && low.isEmpty();
  }

  /**
   * Removes and returns the flag we should send next, returning `null` if there
   * aren't any pending.
   */
  public Flag next() {
    if (!high.isEmpty())
      return high.remove();
    if (!med.isEmpty())
      return med.remove();
    if (!low.isEmpty())
      return low.remove();
    return null;
  }

  public void enqueue(Flag flag, Priority priority) {
    switch (priority) {
    case High:
      high.add(flag);
      break;
    case Medium:
      med.add(flag);
      break;
    case Low:
      low.add(flag);
      break;
    }
  }
}
