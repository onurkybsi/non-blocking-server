package org.kybprototyping.non_blocking_server;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.StandardSocketOptions;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.time.Clock;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.kybprototyping.non_blocking_server.messaging.Formatter;
import org.kybprototyping.non_blocking_server.messaging.IncomingMessageHandler;
import org.kybprototyping.non_blocking_server.util.TimeUtils;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;

/**
 * Non-blocking server that listens the configured port.
 * 
 * @author Onur Kayabasi (o.kayabasi@outlook.com)
 */
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public final class Server implements AutoCloseable {

  private static final Logger logger = LogManager.getLogger(Server.class);

  private final ServerProperties properties;
  private final Formatter formatter;
  private final TimeUtils timeUtils;
  private final IncomingMessageHandler incomingMessageHandler;
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
   * @param properties server configuration values
   * @param formatter user {@link Formatter} implementation
   * @param clock server clock
   * @param incomingMessageHandler user {@link IncomingMessageHandler} implementation
   * @return built {@link Server}
   * @throws IOException if the building fails
   */
  public static Server build(ServerProperties properties, Formatter formatter, Clock clock,
      IncomingMessageHandler incomingMessageHandler) throws IOException {
    Selector selector = Selector.open();
    ServerSocketChannel serverChannel = ServerSocketChannel.open();
    ExecutorService executorService = Executors.newSingleThreadExecutor(new ServerThreadFactory());
    return new Server(properties, formatter, TimeUtils.builder().clock(clock).build(),
        incomingMessageHandler, selector, serverChannel, executorService);
  }

  /**
   * Starts the server.
   * 
   * <p>
   * This method blocks the current thread until the server starts to listen.
   * </p>
   * 
   * @return {@code false} if the server is already started, otherwise {@code true}
   * @throws IllegalStateException if the server has already shut down(closed)
   * @throws IOException if socket to listen could not get bound
   * @throws InterruptedException if the current thread is interrupted before listening
   */
  public synchronized boolean start() throws IOException, InterruptedException {
    if (this.hasShutDown.get()) {
      throw new IllegalStateException("Server has shut down!");
    }
    if (this.isRunning.get()) {
      return false;
    }

    this.startCompletion = new CountDownLatch(1);
    this.stopCompletion = new CountDownLatch(1);

    try {
      logger.info("Server properties: {}", this.properties);

      this.serverChannel.configureBlocking(false);
      this.serverChannel.socket().bind(new InetSocketAddress(this.properties.port()));
      // TODO: How to set this?
      this.serverChannel.setOption(StandardSocketOptions.SO_RCVBUF, Integer.MAX_VALUE);
      this.serverChannel.register(this.selector, SelectionKey.OP_ACCEPT);
      this.executorService.submit(this::accept);

      this.startCompletion.await();
      return true;
    } catch (IOException e) {
      logger.error("Bootstrap failed!", e);
      throw e;
    }
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
    return this.stopServer();
  }

  /**
   * Tells whether or not this server is open.
   * 
   * @return {@code true} if, and only if, this server is open
   */
  public boolean isOpen() {
    return !this.hasShutDown.get();
  }

  /**
   * @throws InterruptedException if the current thread is interrupted before closing
   * @throws IOException if an I/O error occurs
   */
  @Override
  public synchronized void close() throws InterruptedException, IOException {
    if (this.hasShutDown.get()) {
      return;
    }

    try {
      this.stopServer();

      this.serverChannel.close();
      this.selector.close();
      this.executorService.close();

      this.hasShutDown.set(true);
      logger.debug("Shutdown is successful!");
    } catch (IOException e) {
      logger.error("Shutdown failed!", e);
      this.hasShutDown.set(false);
      throw e;
    }
  }

  private void accept() {
    logger.info("Listening on: {}", this.properties.port());

    var selectedKeyAction = new SelectedKeyAction(this.properties, this.selector,
        new Reader(this.properties, this.formatter, this.timeUtils, this.incomingMessageHandler),
        new Writer(this.properties, this.timeUtils));

    this.isRunning.set(true);
    this.startCompletion.countDown();
    while (this.isRunning.get()) {
      try {
        this.selector.select(selectedKeyAction);
      } catch (Exception e) {
        logger.error("Something went wrong during key selection!", e);
      }
    }
    this.stopCompletion.countDown();
  }

  private boolean stopServer() throws InterruptedException {
    if (this.hasShutDown.get()) {
      throw new IllegalStateException("Server has shut down!");
    }
    if (!this.isRunning.get()) {
      return false;
    }
    logger.debug("Server is being stopped...");

    this.isRunning.set(false);
    this.selector.wakeup();
    this.stopCompletion.await();

    logger.info("Server stopped successfully!");
    return true;
  }

}
