package org.kybprototyping.non_blocking_server;

import static org.kybprototyping.non_blocking_server.configuration.ConfigurationExtractor.extractAsInteger;
import static org.kybprototyping.non_blocking_server.configuration.ServerConfigurationKeys.PORT;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;

/**
 * Non-blocking server that listens the configured port.
 * 
 * @author Onur Kayabasi (o.kayabasi@outlook.com)
 */
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public final class Server {

  private static final Logger logger = LogManager.getLogger(Server.class);

  private final AtomicBoolean isRunning = new AtomicBoolean(false);
  private final CountDownLatch bootstrapCompletion = new CountDownLatch(1);
  private final CountDownLatch shutdownCompletion = new CountDownLatch(1);

  private final Selector selector;
  private final ServerSocketChannel serverChannel;

  /**
   * Builds the {@link Server} to run.
   * 
   * @return built {@link Server}
   * @throws IOException if the building fails
   */
  static Server build() throws IOException {
    Selector selector = Selector.open();
    ServerSocketChannel serverChannel = ServerSocketChannel.open();
    return new Server(selector, serverChannel);
  }

  /**
   * Runs the server.
   * 
   * <p>
   * This method blocks the current thread until the server starts to listen.
   * </p>
   * 
   * @return {@code false} if the server is already running, otherwise {@code true}
   * @throws InterruptedException if the current thread is interrupted before listening
   */
  boolean run() throws InterruptedException {
    if (!isRunning.get()) {
      Executors.newSingleThreadExecutor(new ServerThreadFactory()).submit(() -> {
        int port = extractAsInteger(PORT);
        logger.info("Server port: {}", port);

        try {
          serverChannel.configureBlocking(false);
          serverChannel.socket().bind(new InetSocketAddress(port));
          serverChannel.register(selector, SelectionKey.OP_ACCEPT);
          accept(selector, port);

          shutDown();
          logger.info("Server stopped successfully!");
        } catch (Exception e) {
          logger.error("Something went wrong during bootstrap!", e);
        }
      });

      bootstrapCompletion.await();
      return true;
    }

    return false;
  }

  /**
   * Returns whether the server is running or not.
   * 
   * @return {@code true} if the server is running, otherwise {@code false}
   */
  boolean isRunning() {
    return isRunning.get();
  }

  /**
   * Stops the running server.
   * 
   * @return {@code false} if the server is already stopped, otherwise {@code true}
   * @throws InterruptedException if the current thread is interrupted before shutdown completed
   */
  boolean stop() throws InterruptedException {
    if (this.isRunning.get()) {
      logger.debug("Shutdown request received...");

      isRunning.set(false);
      selector.wakeup();
      shutdownCompletion.await();
      return true;
    } else {
      return false;
    }
  }

  private void accept(Selector selector, int port) {
    logger.info("Listening on: {}", port);

    var selectedKeyAction = new SelectedKeyAction(selector);
    isRunning.set(true);

    while (isRunning.get()) {
      try {
        bootstrapCompletion.countDown();
        selector.select(selectedKeyAction);
      } catch (Exception e) {
        logger.error("Something went wrong during key selection!", e);
      }
    }
  }

  private void shutDown() {
    try {
      this.serverChannel.close();
      this.selector.close();
      shutdownCompletion.countDown();
    } catch (Exception e) {
      logger.error("Shutdown failed!", e);
    }
  }

}
