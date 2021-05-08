package tiny;

import java.nio.ByteBuffer;

class Http extends Listener {

  Http content(int _limit) {
    limit = _limit;
    return this;
  }

  @Override
  Http reset() {
    state = http_header_initial_state;
    limit = Integer.MAX_VALUE;
    key = new StringBuilder();
    code = new StringBuilder();
    value = new StringBuilder();
    return this;
  }

  int state;
  int limit;
  StringBuilder key, code, value;

  final static int http_header_initial_state = 0;
  final static int http_header_done_state = 5;

  final static short[] http_header_state = {
  //   *    \t    \n    \r   ' '   ','   ':'   PAD
    0x80,    1, 0xC1, 0xC1,    1, 0x80, 0x80, 0xC1, // state 0:  - method or version
    0x81,    2, 0xC1, 0xC1,    2,    1,    1, 0xC1, // state 1:  - path or status code
    0x82, 0x82,    4,    3, 0x82, 0x82, 0x82, 0xC1, // state 2:  - version or reason
    0xC1, 0xC1, 0x44, 0xC1, 0xC1, 0xC1, 0xC1, 0xC1, // state 3:  HTTP prologue newline
    0x84, 0xC1, 0xC0,    5, 0xC1, 0xC1,    6, 0xC1, // state 4:  Start of header field
    0xC1, 0xC1, 0xC0, 0xC1, 0xC1, 0xC1, 0xC1, 0xC1, // state 5:  Last CR before end of header
    0x87,    6, 0xC1, 0xC1,    6, 0x87, 0x87, 0xC1, // state 6:  Leading whitespace before header value
    0x87, 0x87, 0xC4,   10, 0x87, 0x88, 0x87, 0xC1, // state 7:  Header field value
    0x87, 0x88,    6,    9, 0x88, 0x88, 0x87, 0xC1, // state 8:  Split value field value
    0xC1, 0xC1,    6, 0xC1, 0xC1, 0xC1, 0xC1, 0xC1, // state 9:  CR after split value field
    0xC1, 0xC1, 0xC4, 0xC1, 0xC1, 0xC1, 0xC1, 0xC1, // state 10: CR after header value
  };

  boolean http_parse_header(ByteBuffer data) {
    while (data.hasRemaining()) {

      var ch = (char)( data.get() & 0x07f );
      var ct = switch (ch) {
        case '\t' -> 1;
        case '\n' -> 2;
        case '\r' -> 3;
        case  ' ' -> 4;
        case  ',' -> 5;
        case  ':' -> 6;
        default   -> 0;
      };

      var newstate = http_header_state[(state << 3) + ct] & 0x0FF;
      switch (newstate) {

        case 0x80 -> { // http_header_status_version_character
          key.append(ch);
        }
        case 0x81 -> { // http_header_status_code_character
          code.append(ch);
        }
        case 0x82 -> { // http_header_status_status_character
          value.append(ch);
        }
        case 0x44 -> { // http_header_status_store_prologue
          onStart.accept(key, code, value);
          key.setLength(0);
          code.setLength(0);
          value.setLength(0);
        }
        case 0x84 -> { // http_header_status_key_character
          key.append(ch);
        }
        case 0x87, 0x88 -> { // http_header_status_value_character
          value.append(ch);
        }
        case 0xC4 -> { // http_header_status_store_keyvalue
          onHeader.accept(key, value);
          key.setLength(0);
          value.setLength(0);
        }
        case 0xC0, 0xC1 -> { // http_header_status_done
          onBody.run();
          return true; // state = 0x05
        }

        default -> {} // http_header_status_continue;
      }
      state = (newstate & 0x0F);
    } // while(hasRemaining)
    return true;
  }

  // 0 <= mark <= position <= limit <= capacity
  // remaining = limit - position

  boolean http_parse_body(ByteBuffer data) {
    if (limit > 0) {
      var mark = data.position();
      if (limit < data.remaining()) {
        data.limit(mark+limit);
      }
      onContent.accept(data);
      var used = data.position() - mark;
      limit = used > 0 ? limit - used : 0;
    }
    return limit > 0; // content.hasRemaining
  }

  boolean http_parse(ByteBuffer data) {
    return (state != http_header_done_state)
         ? http_parse_header(data) : http_parse_body(data);
  }

}
