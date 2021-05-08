package tiny.util;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class FiFoTest extends TestCase {

  @Test
  void test() {

    var list = new FIFO<String>();
    list.add("A");
    list.add("B");
    list.add("C");
    assertTrue(equ(list,"A","B","C"));

    var r = list.iterator();
    while (r.hasNext()) {
      var e = r.next();
      if (e.equals("B")) r.remove();
    }
    assertTrue(equ(list,"A","C"));

  }
}
