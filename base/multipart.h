#ifndef HTTP_MULTIPART_H
#define HTTP_MULTIPART_H

#if defined(__cplusplus)
extern "C" {
#endif

#define part_header_status_start (2)

void http_multipart_form_data(struct http_roundtripper* rt);

int http_parse_part(struct http_roundtripper* rt, const char *data, int size);

#if defined(__cplusplus)
}
#endif

#endif
