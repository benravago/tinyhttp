
cc = clang
src = chunk.c chunk.h header.c header.h http.c http.h multipart.c multipart.h

tests: whole chunked multipart

parse: main.c $(src)
	$(cc) -I. -o $@ $(filter %.c,$^)

chunked: ../test/data/chunked parse
	./parse $<

multipart: ../test/data/multipart parse
	./parse $<

whole: ../test/data/whole parse
	./parse $<

clean:
	rm parse

