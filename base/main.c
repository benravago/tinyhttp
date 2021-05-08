#include <sys/types.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <errno.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>

#include "http.h"

char* crlf(char *d, int n) {
    for (int i = 0; i < n; i++) {
        if (d[i] == '~') { d[i] = '\r'; }
    }
    return d;
}

struct stat* load(char *fn) {

    struct stat *sb = malloc(sizeof(struct stat));
    if (stat(fn,sb) == 0) {
        char* cb = malloc(sb->st_size);
        if (cb) {
            FILE *fp = fopen(fn,"r");
            if (fp) {
                if (fread(cb,sb->st_size,1,fp) == 1) {
                    fclose(fp);
                    printf("read: `%s` %ld\n",fn,sb->st_size);
                    void **p = (void**)(&(sb->st_nlink));
                    *p = crlf(cb,sb->st_size);
                    return sb;
                }
            }
        }
    }
    perror("load");
    exit(errno);
}

char* canonical(char *fn) {
    char *wd = getcwd(0,0);
    char *cn = malloc(strlen(wd) + 1 + strlen(fn) + 1);
    strcat(cn,wd);
    strcat(cn,"/");
    strcat(cn,fn);
    free(wd);
    return cn;
}


void* func_realloc(void* opaque, void* ptr, int size) {
    return realloc(ptr,size);
}

void func_body(void* opaque, const char* data, int size) {
    printf("data: `%.*s` %d\n", size,data, size );
}

void func_header(void* opaque, const char* key, int nkey, const char* value, int nvalue) {
    printf("hdr:  `%.*s` %d `%.*s` %d\n", nkey,key, nkey, nvalue,value, nvalue );
}

void func_prologue(void* opaque, const char* key, int nkey, const char* code, int ncode, const char* value, int nvalue) {
    printf("info: `%.*s` %d `%.*s` %d `%.*s` %d\n", nkey,key, nkey, ncode,code, ncode, nvalue,value, nvalue );
}

void func_content(void* opaque) {
    printf("body:\n");
}

struct http_funcs funcs = {
    func_realloc,
    func_body,
    func_header,
    func_prologue,
    func_content
};

struct context {
    int rc;
};


int main(int argc, char*argv[]) {
    if (argc < 2) exit(1);

    char *cn = canonical(argv[1]);
    printf("in:   `%s`\n",cn);

    struct stat *sb = load(argv[1]);
    void **p = (void**)(&(sb->st_nlink));
    void *buf = *p;

    printf("\n");

    struct context ctx;
    struct http_roundtripper rt;
    http_init(&rt,funcs,&ctx);

    int read = 0;
    int needmore = http_data(&rt,buf,sb->st_size,&read);

    http_free(&rt);

    printf("\ndone: %d %d\n", needmore, read);
}
