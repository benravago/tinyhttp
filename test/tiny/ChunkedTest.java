package tiny;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ChunkedTest extends TestCase {

  @Test
  void test() {
    var file = "./test/data/chunked";

    var parser = new Chunked();
    parser.reset()
      .onStart((a,b,c) -> write("\r\n",c,"\r\n"))
      .onHeader(this::onHeader)
      .onBody(this::onBody)
      .onContent((b) -> write(b));

    var bytes = load(file);
    var crlf = find(bytes,new byte[]{0x0d,0x0a,0x0d,0x0a});
    var data = subArray(bytes,crlf+4);
    var remaining = feed(data,parser::http_parse);

    write("\r\n"); // append extra CRLF to back
    var out = read();
    bytes = subArray(out,2); // remove extra CRLF from front
    assertArrayEquals(data,bytes);

    assertEquals( remaining, bytes.length-2 );
  }

}
