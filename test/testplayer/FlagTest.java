package testplayer;

import battlecode.common.*;
import static org.junit.Assert.*;
import org.junit.Test;

public class FlagTest {
	@Test
	public void testConversion() {
    Flag one = new Flag(new MapLocation(8234, 3245), Flag.Type.Edge, new MapLocation(8259, 3197));
    Flag two = new Flag(new MapLocation(8234, 3245), one.encode());
    assertEquals(
      "one: " + one + ", two: " + two,
      two.encode(),
      one.encode()
    );

    // TODO add more tests
	}
}
