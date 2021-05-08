package tiny;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;

import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.function.Predicate;

class TestCase {

  static byte[] subArray(byte[] src, int from) {
    return Arrays.copyOfRange(src, from, src.length);
  }

  static byte[] load(String file) {
    try {
      var bytes = Files.readAllBytes(Paths.get(file));
      var n = bytes.length;
      for (var i = 0; i < n; i++) {
        if (bytes[i] == '~') bytes[i] = '\r';
      }
      return bytes;
    }
    catch (IOException e) { throw new UncheckedIOException(e); }
  }

  static int feed(byte[] bytes, Predicate<ByteBuffer> handler) {
    var offset = 0;
    var remaining = bytes.length;
    for (var i = 8; remaining > 0; i *= 2) {
      var length = i < remaining ? i : remaining;
      var data = ByteBuffer.wrap(bytes,offset,length);
      // System.out.println("new "+data);
      while (data.hasRemaining()) {
        var mark = data.position();
        var ok = handler.test(data);
        var position = data.position();
        if (!ok || mark == position) return position;
      }
      offset += length;
      remaining -= length;
    }
    return 0;
  }

  static int find(byte[] bytes, byte...sequence) {
    var a = sequence[0];
    var n = bytes.length - sequence.length;
    SCAN:
    for (var i = 0; i < n; i++) {
      if (bytes[i] == a) {
        for (var j = 1; j < sequence.length; j++) {
          if (bytes[i+j] != sequence[j]) continue SCAN;
        }
        return i;
      }
    }
    return -1;
  }

  ByteArrayOutputStream out = new ByteArrayOutputStream();

  byte[] read() {
    return out.toByteArray();
  }

  void write(CharSequence...chars) {
    for (var cs:chars) {
      var n = cs.length();
      for (var i = 0; i < n; i++) {
        out.write((int)cs.charAt(i));
      }
    }
  }

  void write(ByteBuffer buf) {
    while (buf.hasRemaining()) {
      out.write((int)(buf.get() & 0x0ff));
    }
  }

  void onStart(CharSequence key, CharSequence code, CharSequence value) {
    // System.out.println("start: `"+key+"` `"+code+"` `"+value+"`");
    write(key," ",code," ",value,"\r\n");
  }
  void onHeader(CharSequence key, CharSequence value) {
    // System.out.println("header: `"+key+"` `"+value+"`");
    write(key,": ",value,"\r\n");
  }
  void onBody() {
    // System.out.println("body:");
    write("\r\n");
  }
  void onContent(ByteBuffer data) {
    // System.out.println("content: "+data+' '+data.remaining());
    write(data); // data.position(data.limit());  // fake consume
  }

}
