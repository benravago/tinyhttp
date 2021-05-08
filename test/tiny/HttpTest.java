package tiny;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HttpTest extends TestCase {

  void eval(String file) {
    var parser = new Http();
    parser.reset()
      .onStart(this::onStart)
      .onHeader(this::onHeader)
      .onBody(this::onBody)
      .onContent(this::onContent);
    var bytes = load(file);
    var remaining = feed(bytes,parser::http_parse);
    assertArrayEquals(bytes,read());
    assertEquals(0,remaining);
  }

  @Test
  void test_request() {
    eval("./test/data/request");
  }
  @Test
  void test_response() {
    eval("./test/data/response");
  }
  @Test
  void test_whole() {
    eval("./test/data/whole");
  }
  @Test
  void test_chunked() {
    eval("./test/data/chunked");
  }
  @Test
  void test_multipart() {
    eval("./test/data/multipart");
  }

}
