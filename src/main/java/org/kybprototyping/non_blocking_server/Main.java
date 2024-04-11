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
import org.apache.logging.log4j.core.lookup.MainMapLookup;

final class Main {

  private static final int TIMEOUT_SEC = 10;

  private static final Logger LOGGER = LogManager.getLogger(Main.class);
  private static final ExecutorService PROCESSOR_EXECUTOR =
      Executors.newVirtualThreadPerTaskExecutor();

  public static void main(String[] args) {
    MainMapLookup.setMainArguments(args);
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

  private static void accept(Selector selector, ServerConfig config) { // NOSONAR
    LOGGER.info("Listening on: {}", config.getPort());

    while (true) { // NOSONAR
      try {
        selector.select(selectedKey -> {
          try {
            if (selectedKey.isAcceptable()) {
              ServerSocketChannel server = (ServerSocketChannel) selectedKey.channel();
              SocketChannel connection = server.accept();
              connection.configureBlocking(false);
              connection.register(selector, SelectionKey.OP_READ,
                  IncomingMessage.of(ByteBuffer.allocate(1000)));
            } else if (selectedKey.isReadable()) {
              SocketChannel connection = (SocketChannel) selectedKey.channel();
              IncomingMessage message = (IncomingMessage) selectedKey.attachment();
              ByteBuffer buffer = message.getBuffer();

              if (!message.isComplete()) {
                // TODO: Handle buffer.remaining() == 0 case!
                int readBytes = connection.read(buffer);
                if (readBytes == -1) {
                  selectedKey.cancel();
                } else if (isTimedOut(message)) {
                  LOGGER.warn("Timeout occurred for {}", connection);
                  connection.close();
                  selectedKey.cancel();
                } else {
                  PROCESSOR_EXECUTOR
                      .submit(IncomingMessageProcessor.from(connection, message, config));
                }
              }
            } else {
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

  private static boolean isTimedOut(IncomingMessage message) {
    return Instant.now().getEpochSecond() - message.getTimestamp() >= TIMEOUT_SEC;
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
    }));
  }

}
