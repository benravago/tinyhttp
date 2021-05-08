package tiny.util;

class TestCase {

  @SafeVarargs
  static <E> boolean equ(Iterable<E> src, E... args) {
    var a = src.iterator();
    for (var arg:args) {
      if (a.hasNext() && ! a.next().equals(arg)) return false;
    }
    return ! a.hasNext();
  }

  static <E> void print(Iterable<E> src) {
    var i = 0;
    for (var s:src) {
      System.out.println(""+(i++)+". ["+s+']');
    }
  }

}
