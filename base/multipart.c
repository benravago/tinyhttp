#include <stdio.h>
#include <string.h>

#include "http.h"
#include "multipart.h"

static int find(char *data, int size, char *key, int nkey) {

    char *e = data + size;  // end of data
    char p = *key;          // key[0]
    char *s1 = key + 1;     // key[1:n]
    char *t = key + nkey;   // end of pattern

    for (char *d = data; d < e; d++) {
        next_d:
        if (*d == p) {
            d += 1;
            for (char *s = s1; s < t; s++, d++) {
                if (d < e) {
                    if (*d != *s) {
                        goto next_d; // incomplete; resume outer scan
                    } else {
                        // continue inner scan
                    }
                } else {
                    return key - s; // partial pattern found; return negative of matched size
                }
            }
            return (d - data) - nkey; // whole pattern found
        }
    }
    return size; // pattern not found
}

static void http_part_done(struct http_roundtripper* rt) {
    grow_scratch(rt, rt->nboundary + 1);
    strcpy(rt->scratch, rt->boundary + 2);
    rt->nvalue = rt->nboundary - 2;
    rt->parsestate = part_header_status_start;
    rt->state = http_roundtripper_header;
}

int http_parse_part(struct http_roundtripper* rt, const char *data, int size) {

    if (rt->ncode) { // if partial pattern match at end of last data scan,
                     // try to match remaining pattern bytes
        int remaining = rt->nboundary - rt->ncode;
        int r = find((char*)data, remaining, rt->boundary + rt->ncode, remaining);

        if (r == 0) { // remaining fragment matched
            http_part_done(rt);
            rt->ncode = 0;
            return remaining;
        }
        else { // remainder not matched
               // send used boundary chars  to funcs.body(), then continue
            append_body(rt, rt->boundary, rt->ncode);
            rt->ncode = 0;
        }
    }

    int partsize = find((char*)data, size, rt->boundary, rt->nboundary);

    if (partsize < 0) { // partial match at end of data[]
        rt->ncode = -(partsize);
        append_body(rt, data, size - rt->ncode);
        partsize = size;
    } else if (partsize > 0) { // match after start
        append_body(rt, data, partsize);
    } else { // match at start
        http_part_done(rt);
        partsize = rt->nboundary;
    }

    return partsize;
}

void http_multipart_form_data(struct http_roundtripper* rt) {

    char *p = rt->scratch + rt->nkey + 20;
    char *b = strstr(p, " boundary=");
    if (!b) return;

    b += 10;
    char *c = b;
    char *d = rt->scratch + rt->nkey + rt->nvalue;
    while (*c != 0 && *c > ' ' && *c != ';' && *c != ',' && c < d) c++;

    rt->nboundary = 4 + (c - b);
    rt->boundary = rt->funcs.realloc_scratch(rt->opaque, rt->boundary, rt->nboundary + 1);

    rt->boundary[0] = '\r';
    rt->boundary[1] = '\n';
    rt->boundary[2] = '-';
    rt->boundary[3] = '-';

    strncpy(rt->boundary + 4, b, rt->nboundary - 4);

    rt->boundary[rt->nboundary] = 0;
}

