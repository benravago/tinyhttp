package tiny.util;

public class LIFO<E> extends Chain<E> { // Stack

  public void push(E item) {
    head = new Object[] { head, item };
    size++;
  }

  @SuppressWarnings("unchecked")
  public E pop() {
    if (head != null) {
      var next = head;
      head = (Object[]) head[NEXT];
      size--;
      return (E) next[ITEM];
    }
    return null;
  }

}
