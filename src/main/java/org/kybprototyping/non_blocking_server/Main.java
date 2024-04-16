package org.kybprototyping.non_blocking_server;

import java.io.Closeable;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.time.Instant;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

final class Main {

  private static final int TIMEOUT_SEC = 10;
  private static final int MESSAGE_END_INDICATOR = 3;

  private static final Logger LOGGER = LogManager.getLogger(Main.class);
  private static final ExecutorService PROCESSOR_EXECUTOR = Executors
      .newThreadPerTaskExecutor(Thread.ofVirtual().name("processor-executor-", 0).factory());

  public static void main(String[] args) {
    ServerConfig config = ServerConfig.build(args);
    LOGGER.info("Server port: {}", config.getPort());
    LOGGER.info("Server message storage path: {}", config.getMessageStoragePath());

    try (Selector selector = Selector.open();
        ServerSocketChannel serverChannel = ServerSocketChannel.open();) {
      addShutdownHookForCloseables(selector, serverChannel);

      serverChannel.configureBlocking(false);
      serverChannel.socket().bind(new InetSocketAddress(config.getPort()));
      serverChannel.register(selector, SelectionKey.OP_ACCEPT);

      accept(selector, config);
    } catch (Exception e) {
      LOGGER.error("Something went wrong during bootstrap!", e);
    }
  }

  private static void accept(Selector selector, ServerConfig config) {
    LOGGER.info("Listening on: {}", config.getPort());

    while (true) { // NOSONAR
      try {
        selector.select(selectedKey -> {
          try {
            if (selectedKey.isAcceptable()) {
              ServerSocketChannel server = (ServerSocketChannel) selectedKey.channel();
              SocketChannel connection = server.accept();
              connection.configureBlocking(false);
              connection.register(selector, SelectionKey.OP_READ | SelectionKey.OP_WRITE,
                  ServerMessagingContext.of(ByteBuffer.allocate(1000)));
            } else if (isReadable(selectedKey))
              read(selectedKey);
            else if (isWritable(selectedKey))
              write(selectedKey);
            else {
              LOGGER.warn("Unexpected key selected, it's being cancelled: {}", selectedKey);
              selectedKey.cancel();
            }
          } catch (Exception e) {
            LOGGER.error("Something went wrong during processing selectedKey: {}", selectedKey, e);
          }
        });
      } catch (Exception e) {
        LOGGER.error("Something went wrong during selecting the key!", e);
      }
    }
  }

  private static void read(SelectionKey selectedKey) throws IOException {
    SocketChannel connection = (SocketChannel) selectedKey.channel();
    ServerMessagingContext ctx = (ServerMessagingContext) selectedKey.attachment();
    if (ctx.isIncomingMessageComplete()) {
      return;
    }

    // TODO: Handle buffer.remaining() == 0 case!
    ByteBuffer buffer = ctx.getIncomingMessageBuffer();
    connection.read(buffer);
    if (!connection.isOpen()) {
      LOGGER.debug("Connection already closed {}", connection);
      selectedKey.cancel();
    }

    if (isIncomingMessageComplete(ctx)) {
      LOGGER.debug("Incoming message is complete, it's being processed: {}", connection);
      ctx.setIncomingMessageComplete();
      PROCESSOR_EXECUTOR.submit(new MessagingProcessorExecutor(connection, ctx));
      return;
    }

    if (isTimedOut(ctx)) {
      LOGGER.warn("Timeout occurred for {}", connection);
      connection.close();
      selectedKey.cancel();
    }
  }

  private static void write(SelectionKey selectedKey) throws IOException {
    SocketChannel connection = (SocketChannel) selectedKey.channel();
    ServerMessagingContext ctx = (ServerMessagingContext) selectedKey.attachment();
    if (!ctx.isOutgoingMessageComplete()) {
      return;
    }

    ByteBuffer buffer = ctx.getOutgoingMessageBuffer();
    if (buffer == null) {
      LOGGER.debug("No outgoing message set, connection {} is being closed...", connection);
      connection.close();
      selectedKey.cancel();
      return;
    }

    connection.write(buffer);
    if (!buffer.hasRemaining()) {
      LOGGER.debug("Outgoing has been written, connection {} is being closed...", connection);
      connection.close();
      selectedKey.cancel();
      return;
    }

    if (isTimedOut(ctx)) {
      LOGGER.warn("Timeout occurred for {}", connection);
      connection.close();
      selectedKey.cancel();
    }
  }

  private static boolean isReadable(SelectionKey selectedKey) {
    if (!selectedKey.isReadable()) {
      return false;
    }
    if (!SocketChannel.class.isAssignableFrom(selectedKey.channel().getClass())) {
      return false;
    }
    if (!ServerMessagingContext.class.equals(selectedKey.attachment().getClass())) { // NOSONAR
      return false;
    }
    return true;
  }

  private static boolean isWritable(SelectionKey selectedKey) {
    if (!selectedKey.isWritable()) {
      return false;
    }
    if (!SocketChannel.class.isAssignableFrom(selectedKey.channel().getClass())) {
      return false;
    }
    if (!ServerMessagingContext.class.equals(selectedKey.attachment().getClass())) { // NOSONAR
      return false;
    }
    return true;
  }

  private static boolean isTimedOut(ServerMessagingContext ctx) {
    return Instant.now().getEpochSecond() - ctx.getStartTimestamp() >= TIMEOUT_SEC;
  }

  private static boolean isIncomingMessageComplete(ServerMessagingContext message) {
    ByteBuffer buffer = message.getIncomingMessageBuffer();
    buffer.flip();
    return buffer.get(buffer.limit() - 1) == MESSAGE_END_INDICATOR;
  }

  private static void addShutdownHookForCloseables(Closeable... closeables) {
    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
      for (Closeable closeable : closeables) {
        try {
          closeable.close();
        } catch (IOException e) {
          LOGGER.error("{} couldn't be closed!", closeable.getClass().getSimpleName(), e);
        }
      }

      LOGGER.info("All closeable closed successfully!");
    }));
  }

}
