# Non-blocking Server Prototype

This project is a prototype of a web server that runs non-blocking with a single thread thanks to the _Java NIO_ library and uses the resource efficiently by processing each incoming request with _virtual threads_.

## Testing Command:

```bash
echo -ne 'Hello World!\x03' | nc localhost 8080
```