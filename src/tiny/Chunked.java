package tiny;

import java.nio.ByteBuffer;

// RFC7230 - Hypertext Transfer Protocol (HTTP/1.1): Message Syntax and Routing
//  4. Transfer Codings
//  4.1. Chunked Transfer Coding

class Chunked extends Listener {

  @Override
  Chunked reset() {
    state = http_chunk_initial_state;
    limit = 0;
    size.setLength(0);
    return this;
  }

  int state;
  int limit;
  StringBuilder size = new StringBuilder();

  static final int http_chunk_initial_state = 0;
  static final int http_chunk_done_state = 2;
  static final int http_chunk_resume_state = 3;

  static final short[] http_chunk_state = {
  //   *    LF    CR   HEX
    0xC1, 0xC1, 0xC1,    1, // s0: initial hex char
    0xC1, 0xC1,    2, 0x81, // s1: additional hex chars, followed by CR
    0xC1, 0x83, 0xC1, 0xC1, // s2: trailing LF
    0xC1, 0xC1,    4, 0xC1, // s3: CR after chunk block
    0xC1, 0xC0, 0xC1, 0xC1, // s4: LF after chunk block
  };

  boolean http_parse_chunked(ByteBuffer data) {
    while (data.hasRemaining()) {
      var ch = (char)( data.get() & 0x07f );

      var code = switch (ch) {
        case '\n' -> 1; // LF
        case '\r' -> 2; // CR
        case '0',  '1',  '2',  '3', '4',  '5',  '6',  '7', '8',  '9',
             'A',  'B',  'C',  'D',  'E',  'F',
             'a',  'b',  'c',  'd',  'e',  'f' -> 3;
        default -> 0;
      };

      var newstate = http_chunk_state[ (state << 2) + code] & 0x0FF;
      switch (newstate) {
        case 0xC0, 0xC1 -> {
          // no-op
        }
        case 0x01, 0x81 -> { // size char
          size.append(ch);
        }
        case 0x83 -> { // chunk-size done
          limit = Integer.parseInt(size, 0, size.length(), 16);
          onStart.accept("chunk","size",size);
          size.setLength(0);
          return limit != 0; // state = 0x02
        }
        default -> {} // continue
      }
      state = (newstate & 0x0F);
    } // while(remaining)
    return true; // needs more
  }

  boolean http_parse_body(ByteBuffer data) {
    if (limit > 0) {
      var mark = data.position();
      if (limit >= data.remaining()) {
        onContent.accept(data); // all data is content
      } else {
        var eod = data.limit(); // save end-of-data
        data.limit(mark+limit); // show end-of-content
        onContent.accept(data);
        data.limit(eod); // restore end-of-data
      }
      var used = data.position() - mark;
      // assert used > 0
      limit = used > 0 ? limit - used : 0;
    }
    if (limit < 1) {
      // assert limit == 0
      state = http_chunk_resume_state;
    }
    return true;
  }

  boolean http_parse(ByteBuffer data) {
    return (state != http_chunk_done_state)
         ? http_parse_chunked(data) : http_parse_body(data);
  }

}
