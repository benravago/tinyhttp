/*-
 * Copyright 2012 Matthew Endsley
 * All rights reserved
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted providing that the following conditions
 * are met:
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE AUTHOR ``AS IS'' AND ANY EXPRESS OR
 * IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED.  IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY
 * DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS
 * OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
 * HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING
 * IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */

#include "http.h"

#include <ctype.h>
#include <string.h>
#include <stdio.h>

#include "header.h"
#include "chunk.h"
#include "multipart.h"

void append_body(struct http_roundtripper* rt, const char* data, int ndata)
{
    rt->funcs.body(rt->opaque, data, ndata);
}

void grow_scratch(struct http_roundtripper* rt, int size)
{
    if (rt->nscratch >= size) {
        return;
    }
    if (size < 64) {
        size = 64;
    }
    int nsize = (rt->nscratch * 3) / 2;
    if (nsize < size) {
        nsize = size;
    }
    rt->scratch = (char*)rt->funcs.realloc_scratch(rt->opaque, rt->scratch, nsize);
    rt->nscratch = nsize;
}

static int min(int a, int b)
{
    return a > b ? b : a;
}

enum http_content_type {
    data_raw,
    data_chunked,
    data_multipart,
    data_part,
};

void http_init(struct http_roundtripper* rt, struct http_funcs funcs, void* opaque)
{
    rt->funcs = funcs;
    rt->scratch = 0;
    rt->opaque = opaque;
    rt->ncode = 0;
    rt->parsestate = 0;
    rt->contentlength = -1;
    rt->state = http_roundtripper_header;
    rt->nscratch = 0;
    rt->nkey = 0;
    rt->nvalue = 0;
    rt->contenttype = data_raw;
    rt->boundary = 0;
    rt->nboundary = 0;
}

void http_free(struct http_roundtripper* rt)
{
    if (rt->scratch) {
        rt->funcs.realloc_scratch(rt->opaque, rt->scratch, 0);
        rt->scratch = 0;
    }
}

int http_data(struct http_roundtripper* rt, const char* data, int size, int* read)
{
    const int initial_size = size;
    while (size) {
        switch (rt->state) {

          case http_roundtripper_header: {

            switch (http_parse_header_char(&rt->parsestate, *data)) {

              case http_header_status_done: {
                rt->funcs.content(rt->opaque);
                if (rt->parsestate != 0) {
                    rt->state = http_roundtripper_error;
                } else if (rt->contenttype == data_chunked) {
                    rt->contentlength = 0;
                    rt->state = http_roundtripper_chunk_header;
                } else if (rt->contenttype == data_multipart) {
                    rt->contenttype = data_part;
                    rt->parsestate = part_header_status_start;
                    rt->state = http_roundtripper_header;
                } else if (rt->contenttype == data_part) {
                    rt->state = http_roundtripper_part_data;
                } else if (rt->contentlength == 0) {
                    rt->state = http_roundtripper_close;
                } else if (rt->contentlength > 0) {
                    rt->state = http_roundtripper_raw_data;
                } else if (rt->contentlength == -1) {
                    rt->state = http_roundtripper_unknown_data;
                } else {
                    rt->state = http_roundtripper_error;
                }
                break;
              }
              case http_header_status_token1_character: {
                grow_scratch(rt, rt->nkey + 1);
                rt->scratch[rt->nkey] = *data;
                ++rt->nkey;
                break;
              }
              case http_header_status_token2_character: {
                grow_scratch(rt, rt->nkey + rt->ncode + 1);
                rt->scratch[rt->nkey+rt->ncode] = *data;
                ++rt->ncode;
                break;
              }
              case http_header_status_token3_character: {
                grow_scratch(rt, rt->nkey + rt->ncode + rt->nvalue + 1);
                rt->scratch[rt->nkey+rt->ncode+rt->nvalue] = *data;
                ++rt->nvalue;
                break;
              }
              case http_header_status_key_character: {
                grow_scratch(rt, rt->nkey + 1);
                rt->scratch[rt->nkey] = tolower(*data);
                ++rt->nkey;
                break;
              }
              case http_header_status_value_character: {
                grow_scratch(rt, rt->nkey + rt->nvalue + 1);
                rt->scratch[rt->nkey+rt->nvalue] = *data;
                ++rt->nvalue;
                break;
              }
              case http_header_status_store_keyvalue: {
                if (rt->nkey == 17 && 0 == strncmp(rt->scratch, "transfer-encoding", rt->nkey)) {
                    if (rt->nvalue == 7 && 0 == strncmp(rt->scratch + rt->nkey, "chunked", rt->nvalue)) {
                        rt->contenttype = data_chunked;
                    }
                } else if (rt->nkey == 14 && 0 == strncmp(rt->scratch, "content-length", rt->nkey)) {
                    int ii, end;
                    rt->contentlength = 0;
                    for (ii = rt->nkey, end = rt->nkey + rt->nvalue; ii != end; ++ii) {
                        rt->contentlength = rt->contentlength * 10 + rt->scratch[ii] - '0';
                    }
                } else if (rt->nkey == 12 && 0 == strncmp(rt->scratch, "content-type", rt->nkey)) {
                    if (rt->nvalue > 20 && 0 == strncmp(rt->scratch + rt->nkey, "multipart/form-data;", 20)) {
                        http_multipart_form_data(rt);
                        rt->contenttype = data_multipart;
                    }
                }
                rt->funcs.header(rt->opaque,
                    rt->scratch, rt->nkey,
                    rt->scratch + rt->nkey, rt->nvalue
                );
                rt->nkey = 0;
                rt->nvalue = 0;
                break;
              }
              case http_header_status_store_prologue: {
                rt->funcs.prologue(rt->opaque,
                    rt->scratch, rt->nkey,
                    rt->scratch + rt->nkey, rt->ncode,
                    rt->scratch + rt->nkey + rt->ncode, rt->nvalue
                );
                rt->nkey = 0;
                rt->ncode = 0;
                rt->nvalue = 0;
                break;
              }

            } // switch(http_parse...)

            --size;
            ++data;
            break;
          }

          case http_roundtripper_chunk_header: {
            if (!http_parse_chunked(&rt->parsestate, &rt->contentlength, *data)) {
                if (rt->contentlength == -1) {
                    rt->state = http_roundtripper_error;
                } else if (rt->contentlength == 0) {
                    rt->state = http_roundtripper_close;
                } else {
                    rt->state = http_roundtripper_chunk_data;
                }
            }
            --size;
            ++data;
            break;
          }

          case http_roundtripper_chunk_data: {
            const int chunksize = min(size, rt->contentlength);
            append_body(rt, data, chunksize);
            rt->contentlength -= chunksize;
            size -= chunksize;
            data += chunksize;

            if (rt->contentlength == 0) {
                rt->contentlength = 1;
                rt->state = http_roundtripper_chunk_header;
            }
            break;
          }

          case http_roundtripper_part_data: {
            const int partsize = http_parse_part(rt, data, size);
            rt->contentlength -= partsize;
            if (rt->contentlength == 0) {
                rt->state = http_roundtripper_close;
            }
            size -= partsize;
            data += partsize;
            break;
          }

          case http_roundtripper_raw_data: {
            const int chunksize = min(size, rt->contentlength);
            append_body(rt, data, chunksize);
            rt->contentlength -= chunksize;
            size -= chunksize;
            data += chunksize;

            if (rt->contentlength == 0) {
                rt->state = http_roundtripper_close;
            }
            break;
          }

          case http_roundtripper_unknown_data: {
            if (size == 0) {
                rt->state = http_roundtripper_close;
            } else {
                append_body(rt, data, size);
                size -= size;
                data += size;
            }
            break;
          }

          case http_roundtripper_close:
          case http_roundtripper_error: {
            break;
          }

        } // switch(rt->state)

        if (rt->state == http_roundtripper_error || rt->state == http_roundtripper_close) {
            if (rt->scratch) {
                rt->funcs.realloc_scratch(rt->opaque, rt->scratch, 0);
                rt->scratch = 0;
            }
            *read = initial_size - size;
            return 0;
        }
    } // while(size)

    *read = initial_size - size;
    return 1;
}

int http_iserror(struct http_roundtripper* rt)
{
    return rt->state == http_roundtripper_error;
}
