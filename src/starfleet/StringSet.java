package starfleet;

/**
 * A hashset of strings, implemented with a large string, since string
 * operations take only 1 bytecode.
 *
 * The strings in the set must not use the characters '<' and '>', since those
 * are used for keeping track of entries.
 */
public class StringSet {
  StringBuilder values = new StringBuilder();

  public boolean contains(String key) {
    return values.indexOf("<" + key + ">") == -1;
  }

  /**
   * Returns whether it was new.
   */
  public boolean add(String key) {
    key = "<" + key + ">";
    if (values.indexOf(key) == -1) {
      values.append(key);
      return true;
    } else {
      return false;
    }
  }

  /**
   * Returns whether anything was actually removed.
   */
  public boolean remove(String key) {
    key = "<" + key + ">";
    int idx = values.indexOf(key);
    if (idx != -1) {
      values.delete(idx, idx + key.length());
      return true;
    } else {
      return false;
    }
  }
}
