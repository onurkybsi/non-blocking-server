package org.kybprototyping.non_blocking_server;

import static org.kybprototyping.non_blocking_server.configuration.ConfigurationExtractor.extractAsInteger;
import static org.kybprototyping.non_blocking_server.configuration.ServerConfigurationKeys.PORT;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
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
public final class Server implements AutoCloseable {

  private static final Logger logger = LogManager.getLogger(Server.class);

  private final Selector selector;
  private final ServerSocketChannel serverChannel;
  private final ExecutorService executorService;

  private final AtomicBoolean isRunning = new AtomicBoolean(false);
  private final AtomicBoolean hasShutDown = new AtomicBoolean(false);

  private CountDownLatch startCompletion;
  private CountDownLatch stopCompletion;

  /**
   * Builds the {@link Server} to run.
   * 
   * @return built {@link Server}
   * @throws IOException if the building fails
   */
  public static Server build() throws IOException {
    Selector selector = Selector.open();
    ServerSocketChannel serverChannel = ServerSocketChannel.open();
    ExecutorService executorService = Executors.newSingleThreadExecutor(new ServerThreadFactory());
    return new Server(selector, serverChannel, executorService);
  }

  /**
   * Starts the server.
   * 
   * <p>
   * This method blocks the current thread until the server starts to listen.
   * </p>
   * 
   * @return {@code true} if the server is successfully started, otherwise {@code false}
   * @throws IllegalStateException if the server has shut down(closed)
   * @throws InterruptedException if the current thread is interrupted before listening
   */
  public synchronized boolean start() throws InterruptedException {
    if (this.hasShutDown.get()) {
      throw new IllegalStateException("Server has shut down!");
    }

    if (!this.isRunning.get()) {
      this.startCompletion = new CountDownLatch(1);
      this.stopCompletion = new CountDownLatch(1);

      executorService.submit(() -> {
        int port = extractAsInteger(PORT);
        logger.info("Server port: {}", port);

        try {
          this.serverChannel.configureBlocking(false);
          this.serverChannel.socket().bind(new InetSocketAddress(port));
          this.serverChannel.register(selector, SelectionKey.OP_ACCEPT);

          accept(selector, port);
          this.stopCompletion.countDown();
        } catch (IOException e) {
          logger.error("Something went wrong during bootstrap!", e);
          this.startCompletion.countDown();
        }
      });

      this.startCompletion.await();
      return this.isRunning();
    }

    return true;
  }

  /**
   * Returns whether the server is running or not.
   * 
   * @return {@code true} if the server is running, otherwise {@code false}
   */
  public boolean isRunning() {
    return isRunning.get();
  }

  /**
   * Stops the running server if it's running.
   * 
   * <p>
   * Note that this method only stops the server to listen. It doesn't close all the resources open.
   * So, if you would like to restart, you can call the {@code start} method afterwards, however, if
   * you would like to shutdown the server and release all the resources, you need to call
   * {@code close}.
   * </p>
   * 
   * @return {@code false} if the server is already stopped, otherwise {@code true}
   * @throws InterruptedException if the current thread is interrupted before stopping
   */
  public synchronized boolean stop() throws InterruptedException {
    if (this.isRunning.get()) {
      try {
        logger.debug("Stop request received...");

        this.isRunning.set(false);
        this.selector.wakeup();
        this.stopCompletion.await();

        logger.info("Server stopped successfully!");
        return true;
      } catch (InterruptedException e) {
        logger.error("Stop interrupted!", e);
        throw e;
      }
    } else {
      return false;
    }
  }

  /**
   * Tells whether or not this server is open.
   * 
   * @return {@code true} if, and only if, this server is open
   */
  public boolean isOpen() {
    return !this.hasShutDown.get();
  }

  @Override
  public void close() throws Exception {
    if (!this.hasShutDown.compareAndSet(false, true)) {
      return;
    }

    try {
      this.stop();

      this.serverChannel.close();
      this.selector.close();
      this.executorService.close();

      logger.debug("Shutdown is successful!");
    } catch (Exception e) {
      logger.error("Shutdown failed!", e);
      this.hasShutDown.set(false);
      throw e;
    }
  }

  private void accept(Selector selector, int port) {
    logger.info("Listening on: {}", port);

    var selectedKeyAction = new SelectedKeyAction(selector);

    this.isRunning.set(true);
    this.startCompletion.countDown();
    while (this.isRunning.get()) {
      try {
        this.selector.select(selectedKeyAction);
      } catch (Exception e) {
        logger.error("Something went wrong during key selection!", e);
      }
    }
  }

}
