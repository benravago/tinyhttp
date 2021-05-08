package tiny;

import java.nio.ByteBuffer;

// RFC2046 - Multipurpose Internet Mail Extensions (MIME) Part Two: Media Types
// RFC7578 - Returning Values from Forms: multipart/form-data

class Multipart extends Listener {

  Multipart boundary(CharSequence cs) {
    var str = "\r\n--"+unquote(cs);
    boundary = str.getBytes();
    boundary_offset = 2; // for first boundary marker after body CRLF marker
    return this;
  }

  @Override
  Multipart reset() {
    state = Http.http_header_done_state;
    key = new StringBuilder();
    value = new StringBuilder();
    return this;
  }

  int state;
  StringBuilder key, value;

  final static int http_header_status_character = 2;

  boolean http_parse_header(ByteBuffer data) {
    while (data.hasRemaining()) {

      var ch = (char)( data.get() & 0x07f );
      var index = switch (ch) {
        case '\t' -> 1;
        case '\n' -> 2;
        case '\r' -> 3;
        case  ' ' -> 4;
        case  ',' -> 5;
        case  ':' -> 6;
        default   -> 0;
      };

      var newstate = Http.http_header_state[(state << 3) + index] & 0x0FF;
      switch (newstate) {

        case 0x80, 0x81 -> {
          throw new IllegalStateException("new state: "+Integer.toHexString(newstate));
        }
        case 0x82 -> { // http_header_status_status_character
          value.append(ch);
        }
        case 0x44 -> { // http_header_status_store_prologue
          onStart.accept("content", "boundary", value);
          value.setLength(0);
        }
        case 0x84 -> { // http_header_status_key_character
          key.append(ch);
        }
        case 0x87, 0x88 -> { // http_header_status_value_character
          value.append(ch);
        }
        case 0xC4 -> { // http_header_status_store_keyvalue
          onHeader.accept(key,value);
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

  boolean http_parse_body(ByteBuffer data) {
    // assert boundary_offset > -1;
    if (boundary_offset > 0) {
      var p = match(data); // look for partial match
      if (p > 0) { // match found
        data.position(p); // skip boundary tail
        return http_parse_boundary();
      }
      else if (p < 0) { // partial match increment
        data.position(-p); // skip boundary chars
        return true; // try next buffer
      }
      else { // p == 0; no match
        var b = ByteBuffer.wrap(boundary,0,boundary_offset);
        onContent.accept(b); // use leading boundary from previous buffer as data
        boundary_offset = 0; // ignore prior boundary chars; resume data scan
      }
    }
    var p = find(data); // look for full match
    if (p == 0) { // no match
      onContent.accept(data); // use all data
      return true;
    }
    else { // p != 0; partial or full match
      var mark = p < 0 ? -p : p;
      var eod = data.limit();
      data.limit(mark - boundary_offset);
      onContent.accept(data);
      data.limit(eod);
      data.position(mark);
      return p < 0 ? true : http_parse_boundary();
    }
  }

  boolean http_parse_boundary() {
    state = http_header_status_character;
    boundary_offset = 0;
    return true;
  }

  boolean http_parse(ByteBuffer data) {
    return (state != Http.http_header_done_state)
         ? http_parse_header(data) : http_parse_body(data);
  }

  static CharSequence unquote(CharSequence str) {
    if (str.charAt(0) != '"') return str;
    var n = str.length() - 1;
    if (str.charAt(n) == '"') return str.subSequence(1,n);
    throw new IllegalArgumentException("malformed string: "+str);
  }

  byte[] boundary;     // "\r\n--boundary"
  int boundary_offset;

  /**
   *  'find' the full boundary pattern within within the buf.remaining() region.
   *  returns p > 0  : position after full match, boundary_offset adjusted
   *          p < 0  : position after partial match, boundary_offset adjusted
   *          p == 0 : no match, no change in boundary_offset
   */
  int find(ByteBuffer b) {
    var c = boundary[boundary_offset];
    var n = b.limit();
    var i = b.position();
    SCAN:
    while (i < n) {
      if (b.get(i++) == c) {
        var p = i;
        var k = boundary_offset + 1;
        while (p < n) {
          if (b.get(p++) != boundary[k++]) {
            continue SCAN;
          }
          if (k == boundary.length) {
            boundary_offset = k;
            return p; // full match, j = boundary_limit
          }
        }
        boundary_offset = k;
        return -p; // partial match, j = buf.limit()
      }
    }
    return 0; // no match
  }

  /**
   * 'match' a partial boundary pattern from the buf.position()
   *  returns p > 0  : position after full match, boundary_offset adjusted
   *          p < 0  : position after partial match, boundary_offset adjusted
   *          p == 0 : no match, no change in boundary_offset
   */
  int match(ByteBuffer b) {
    var k = boundary_offset;
    var p = b.position();
    var n = b.limit();
    while (p < n) {
      if (b.get(p++) != boundary[k++]) {
        return 0; // no match
      }
      if (k == boundary.length) {
        boundary_offset = k;
        return p; // match completed
      }
    }
    if (k > boundary_offset) {
      boundary_offset = k;
      return -p; // match incremented
    }
    return 0; // no match
  }

}
