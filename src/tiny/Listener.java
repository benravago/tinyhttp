package tiny;

import java.nio.ByteBuffer;
import java.util.function.Consumer;
import java.util.function.BiConsumer;
import tiny.util.function.TriConsumer;

class Listener {

  @SuppressWarnings("unchecked")
  static <T extends Throwable,V> V uncheck(Throwable t) throws T { throw (T)t; }

  Consumer<Throwable> onError = (thrown) -> uncheck(thrown);

  TriConsumer<CharSequence, CharSequence , CharSequence> onStart = (key,code,value) -> {};
  BiConsumer<CharSequence, CharSequence> onHeader = (key,value) -> {};
  Runnable onBody = () -> {};
  Consumer<ByteBuffer> onContent = (data) -> {};

  Listener onError(Consumer<Throwable> _onError) {
    onError = _onError; return this;
  }
  Listener onStart(TriConsumer<CharSequence, CharSequence , CharSequence> _onStart) {
    onStart = _onStart; return this;
  }
  Listener onHeader(BiConsumer<CharSequence, CharSequence> _onHeader) {
    onHeader = _onHeader; return this;
  }
  Listener onBody(Runnable _onBody) {
    onBody = _onBody; return this;
  }
  Listener onContent(Consumer<ByteBuffer> _onContent) {
    onContent = _onContent; return this;
  }

  Listener reset() { return this; }
}
