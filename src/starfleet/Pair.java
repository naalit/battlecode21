package starfleet;

/**
 * A helper class for creating pairs of two elements, `fst` and `snd`. I'm still
 * annoyed that Java 8 doesn't have proper tuples, but this is better than
 * nothing.
 */
public class Pair<A, B> {
  public A fst;
  public B snd;

  public Pair(A a, B b) {
    fst = a;
    snd = b;
  }

  /**
   * Calls .equals() on both elements of the pair.
   */
  public boolean equals(Object o) {
    if (o instanceof Pair<?, ?>) {
      return fst.equals(((Pair<?, ?>) o).fst) && snd.equals(((Pair<?, ?>) o).snd);
    } else {
      return false;
    }
  }
}
