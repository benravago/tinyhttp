package tiny.util;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class LiFoTest extends TestCase {

  @Test
  void test() {

    var stack = new LIFO<String>();
    stack.push("A");
    stack.push("B");
    stack.push("C");
    assertTrue(equ(stack, "C","B","A"));

    var r = stack.iterator();
    while (r.hasNext()) {
      var e = r.next();
      if (e.equals("B")) r.remove();
    }
    assertTrue(equ(stack, "C","A"));
  }

}
