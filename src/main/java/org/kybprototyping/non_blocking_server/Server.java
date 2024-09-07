package org.kybprototyping.non_blocking_server;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import org.kybprototyping.non_blocking_server.handler.IncomingMessageHandler;
import org.kybprototyping.non_blocking_server.handler.MaxIncomingMessageSizeHandler;
import org.kybprototyping.non_blocking_server.messaging.Formatter;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

/**
 * Non-blocking server that listens the configured port.
 * 
 * @author Onur Kayabasi (o.kayabasi@outlook.com)
 */
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
@Log4j2
public final class Server implements AutoCloseable {

  private final Selector selector;
  private final ServerSocketChannel serverChannel;
  private final ExecutorService executorService;
  private final ServerProperties properties;
  private final Reader reader;
  private final Writer writer;

  private final AtomicBoolean isRunning = new AtomicBoolean(false);
  private final AtomicBoolean hasShutDown = new AtomicBoolean(false);

  private CountDownLatch startCompletion;
  private CountDownLatch stopCompletion;

  /**
   * Builder for {@link Server}
   * 
   * @param formatter user {@link Formatter} implementation
   * @param incomingMessageHandler user {@link IncomingMessageHandler} implementation
   * @param maxIncomingMessageSizeHandler user {@link MaxIncomingMessageSizeHandler} implementation
   * @return builder for {@link Server}
   */
  public static ServerBuilder builder(Formatter formatter,
      IncomingMessageHandler incomingMessageHandler,
      MaxIncomingMessageSizeHandler maxIncomingMessageSizeHandler) {
    return new ServerBuilder(formatter, incomingMessageHandler, maxIncomingMessageSizeHandler);
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
      log.info("Server properties: {}", this.properties);

      this.serverChannel.socket().bind(new InetSocketAddress(this.properties.port()));
      this.serverChannel.configureBlocking(false);
      this.serverChannel.register(this.selector, SelectionKey.OP_ACCEPT);
      this.executorService.submit(this::accept);

      this.startCompletion.await();
      return true;
    } catch (IOException e) {
      log.error("Bootstrap failed!", e);
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
      log.debug("Shutdown is successful!");
    } catch (IOException e) {
      log.error("Shutdown failed!", e);
      this.hasShutDown.set(false);
      throw e;
    }
  }

  private void accept() {
    log.info("Listening on: {}", this.properties.port());

    var selectedKeyAction =
        new SelectedKeyAction(this.properties, this.selector, this.reader, this.writer);

    this.isRunning.set(true);
    this.startCompletion.countDown();
    while (this.isRunning.get()) {
      try {
        this.selector.select(selectedKeyAction);
      } catch (Exception e) {
        log.error("Something went wrong during key selection!", e);
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
    log.debug("Server is being stopped...");

    this.isRunning.set(false);
    this.selector.wakeup();
    this.stopCompletion.await();

    log.info("Server stopped successfully!");
    return true;
  }

}
