# Non-blocking Server Prototype

This project is a prototype of a web server that runs non-blocking with a single thread thanks to the Java NIO library and uses the resource efficiently by processing each incoming request with virtual threads.

## Testing Command:

```bash
echo -ne 'Hello World!\x03' | nc localhost 8080
```