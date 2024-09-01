package org.kybprototyping.non_blocking_server;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.kybprototyping.non_blocking_server.messaging.Formatter;
import org.kybprototyping.non_blocking_server.messaging.IncomingMessageHandler;
import org.kybprototyping.non_blocking_server.messaging.MaxIncomingMessageSizeHandler;
import org.kybprototyping.non_blocking_server.util.TimeUtils;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
final class Reader {

  private static final int GROWTH_FACTOR = 2;

  private static final Logger logger = LogManager.getLogger(Reader.class);
  private static final ExecutorService executor =
      Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name("executor-", 0).factory());

  private final ServerProperties properties;
  private final Formatter formatter;
  private final TimeUtils timeUtils;
  private final IncomingMessageHandler incomingMessageHandler;
  private final MaxIncomingMessageSizeHandler maxIncomingMessageSizeHandler;

  void read(SelectionKey selectedKey) throws IOException {
    SocketChannel connection = (SocketChannel) selectedKey.channel();
    var ctx = (ServerMessagingContext) selectedKey.attachment();

    int bytesRead = connection.read(ctx.getIncomingMessageBuffer());
    /*
     * That doesn't mean that the client closed the connection! That might mean that, the client has
     * closed its output stream so it is waiting for the server to write! So we should not close the
     * connection without checking here!
     */
    if (bytesRead == -1) {
      if (connection.isConnected()) {
        // So the client closed its output stream and it waits for the server to write.
        setCompleted(connection, ctx, selectedKey);
      } else {
        // The client has closed the entire connection!
        logger.warn("Connection closed before reading is completed: {}", connection);
        closeConnection(selectedKey, connection);
      }

      return;
    }

    if (formatter.isIncomingMessageComplete(ctx.getIncomingMessageBuffer())) {
      setCompleted(connection, ctx, selectedKey);
      return;
    }

    if (isTimedOut(ctx)) {
      logger.warn("Connection timeout: {}", connection);
      // TODO: Call user timeout handler!
      closeConnection(selectedKey, connection);
    }

    if (!setCompletedIfMaxIncomingMessageSize(ctx, selectedKey, connection)) {
      growBufferIfFull(ctx);
    }
  }

  private void setCompleted(SocketChannel connection, ServerMessagingContext ctx,
      SelectionKey selectedKey) {
    logger.debug("Incoming message is complete: {}", connection);
    ctx.setIncomingMessageComplete();
    selectedKey.interestOps(SelectionKey.OP_WRITE);
    executor.submit(new IncomingMessageHandlerExecutor(connection, ctx, incomingMessageHandler));
  }

  private void closeConnection(SelectionKey selectedKey, SocketChannel channel) throws IOException {
    selectedKey.cancel();
    channel.close();
  }

  private boolean isTimedOut(ServerMessagingContext ctx) {
    long now = timeUtils.epochMilli();
    boolean isReadTimeoutOccurred = now - ctx.getStartTimestamp() >= properties.readTimeoutInMs();
    boolean isConnectionTimeoutOccurred =
        now - ctx.getStartTimestamp() >= properties.connectionTimeoutInMs();
    return isReadTimeoutOccurred || isConnectionTimeoutOccurred;
  }

  private boolean setCompletedIfMaxIncomingMessageSize(ServerMessagingContext ctx,
      SelectionKey selectedKey, SocketChannel connection) throws IOException {
    if (ctx.getIncomingMessageBuffer().capacity() < properties.maxBufferSizeInBytes()) {
      return false;
    }

    logger.debug("Incoming message reached max size: {}", connection);
    ctx.setIncomingMessageComplete();
    selectedKey.interestOps(SelectionKey.OP_WRITE);
    executor.submit(
        new MaxIncomingMessageSizeHandlerExecutor(connection, ctx, maxIncomingMessageSizeHandler));
    return true;
  }

  // Is this an efficient way?
  private void growBufferIfFull(ServerMessagingContext ctx) {
    if (ctx.getIncomingMessageBuffer().hasRemaining()) {
      return;
    }

    int newCapacity = Math.min(ctx.getIncomingMessageBuffer().capacity() * GROWTH_FACTOR,
        properties.maxBufferSizeInBytes());
    ByteBuffer grownBuffer = ByteBuffer.allocate(newCapacity);

    ctx.getIncomingMessageBuffer().flip(); // Switch to read mode
    grownBuffer.put(ctx.getIncomingMessageBuffer()); // Copy data to the grown buffer

    ctx.setIncomingMessageBuffer(grownBuffer);
  }

}
