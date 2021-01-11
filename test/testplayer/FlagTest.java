package testplayer;

import battlecode.common.*;
import static org.junit.Assert.*;
import org.junit.Test;

public class FlagTest {
	@Test
	public void testConversion() {
    Flag one = new Flag(new MapLocation(8234, 3245), Flag.Type.Edge, new MapLocation(8259, 3197), 0);
    Flag two = new Flag(new MapLocation(8234, 3245), 0, one.encode(0));
    assertEquals(
      "one: " + one + ", two: " + two,
      two.encode(0),
      one.encode(0)
    );

    // TODO add more tests
	}
}
