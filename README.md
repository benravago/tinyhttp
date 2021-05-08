# tinyhttp
A minimal HTTP response (and request) parser.

This is java port of [Matthew Endsley](https://mendsley.github.io/2012/12/19/tinyhttp.html)'s [tinyhttp](https://github.com/mendsley/tinyhttp) state-machine HTTP parser which I've also modified to be able to handle http request messages as well as multipart/form-data content.

The modified c source is in the <code>./base</code> directory.  The <code>./base/main.c</code> program runs some test data through the parser.

The ported java code is in the <code>./src</code> directory and the corresponding junit5 tests in <code>./test/tiny</code> use the same test data. 
