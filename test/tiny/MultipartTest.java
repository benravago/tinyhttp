package tiny;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MultipartTest extends TestCase {

  @Test
  void test() {
    var file = "./test/data/multipart";
    var tag = "abcdef";

    var parser = new Multipart().boundary(tag);
    parser.reset()
    .onStart((a,b,c) -> write("\r\n","--",tag,c,"\r\n"))
    .onHeader(this::onHeader)
    .onBody(this::onBody)
    .onContent((b) -> write(b));

    var bytes = load(file);
    var crlf = find(bytes,new byte[]{0x0d,0x0a,0x0d,0x0a});
    var data = subArray(bytes,crlf+4);
    var remaining = feed(data,parser::http_parse);

    var out = read();
    bytes = subArray(out,2); // remove extra CRLF from front
    assertArrayEquals(data,bytes);

    assertEquals(0,remaining);
  }

}
