package org.kybprototyping.non_blocking_server;

import static org.kybprototyping.non_blocking_server.configuration.ConfigurationExtractor.extractAsInteger;
import static org.kybprototyping.non_blocking_server.configuration.ServerConfigurationKeys.PORT;
import java.io.Closeable;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

final class Server {

  private static final Logger logger = LogManager.getLogger(Server.class);

  private Server() {
    throw new UnsupportedOperationException("This class is not initiable!");
  }

  static void run() {
    int port = extractAsInteger(PORT);
    logger.info("Server port: {}", port);

    try (Selector selector = Selector.open();
        ServerSocketChannel serverChannel = ServerSocketChannel.open();) {
      addShutdownHookForCloseables(selector, serverChannel);

      serverChannel.configureBlocking(false);
      serverChannel.socket().bind(new InetSocketAddress(port));
      serverChannel.register(selector, SelectionKey.OP_ACCEPT);

      accept(selector, port);
    } catch (Exception e) {
      logger.error("Something went wrong during bootstrap!", e);
    }
  }

  private static void accept(Selector selector, int port) {
    logger.info("Listening on: {}", port);

    var selectedKeyAction = new SelectedKeyAction(selector);

    while (true) { // NOSONAR
      try {
        selector.select(selectedKeyAction);
      } catch (Exception e) {
        logger.error("Something went wrong during key selection!", e);
      }
    }
  }

  private static void addShutdownHookForCloseables(Closeable... closeables) {
    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
      for (Closeable closeable : closeables) {
        try {
          closeable.close();
        } catch (IOException e) {
          logger.error("{} couldn't be closed!", closeable.getClass().getSimpleName(), e);
        }
      }

      logger.info("All closeable closed successfully!");
    }));
  }

}
