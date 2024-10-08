package org.kybprototyping.non_blocking_server;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.concurrent.ExecutorService;
import org.kybprototyping.non_blocking_server.handler.IncomingMessageHandler;
import org.kybprototyping.non_blocking_server.handler.MaxIncomingMessageSizeHandler;
import org.kybprototyping.non_blocking_server.handler.TimeoutHandler;
import org.kybprototyping.non_blocking_server.handler.TimeoutType;
import org.kybprototyping.non_blocking_server.messaging.Formatter;
import org.kybprototyping.non_blocking_server.util.TimeUtils;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
@Log4j2
final class Reader {

  private static final int GROWTH_FACTOR = 2;

  private final ServerProperties properties;
  private final Formatter formatter;
  private final TimeUtils timeUtils;
  private final IncomingMessageHandler incomingMessageHandler;
  private final MaxIncomingMessageSizeHandler maxIncomingMessageSizeHandler;
  private final TimeoutHandler timeoutHandler;
  private final ExecutorService executor;

  void read(SelectionKey selectedKey) throws IOException {
    SocketChannel connection = (SocketChannel) selectedKey.channel();
    if (!connection.isConnected()) {
      log.debug("Connection is already closed: {}", connection);
      closeConnectionEntirely(selectedKey, connection);
      return;
    }

    var ctx = context(selectedKey, connection);

    int bytesRead = connection.read(ctx.incomingMessageBuffer());
    /*
     * That doesn't mean that the client closed the connection! That might mean that, the client has
     * closed its output stream so it is waiting for the server to write! So we should not close the
     * connection without checking here!
     */
    if (bytesRead == -1) {
      if (connection.isConnected()) {
        log.debug("Client closed its output stream: {}", ctx);

        ctx.isClientClosedOutputStream(true);
        ctx.isIncomingMessageComplete(isIncomingMessageComplete(ctx));
        if (!properties.isLongLivedConnectionsSupported()) {
          connection.socket().shutdownInput();
        }
        selectedKey.interestOps(SelectionKey.OP_WRITE);

        executor.submit(incomingMessageHandlerExecutor(connection, ctx));
      } else {
        log.warn("Connection closed before reading is completed: {}", ctx);
        closeConnectionEntirely(selectedKey, connection);
      }
      return;
    }

    if (isIncomingMessageComplete(ctx)) {
      ctx.isIncomingMessageComplete(true);
      if (!properties.isLongLivedConnectionsSupported()) {
        connection.socket().shutdownInput();
      }
      selectedKey.interestOps(SelectionKey.OP_WRITE);

      executor.submit(incomingMessageHandlerExecutor(connection, ctx));
      return;
    }

    if (setCompletedIfTimeout(ctx, selectedKey, connection)) {
      return;
    }

    if (!setCompletedIfMaxIncomingMessageSize(ctx, selectedKey, connection)) {
      growBufferIfFull(ctx);
    }
  }

  private ServerMessagingContext context(SelectionKey selectedKey, SocketChannel connection)
      throws IOException {
    if (selectedKey.attachment() != null) {
      return (ServerMessagingContext) selectedKey.attachment();
    } else {
      var ctx = ServerMessagingContext.of(timeUtils.epochMilli(), connection.getRemoteAddress(),
          ByteBuffer.allocate(properties.minBufferSizeInBytes()));
      selectedKey.attach(ctx);
      return ctx;
    }
  }

  private void closeConnectionEntirely(SelectionKey selectedKey, SocketChannel connection)
      throws IOException {
    selectedKey.channel();
    connection.close();
  }

  private boolean isIncomingMessageComplete(ServerMessagingContext ctx) {
    return formatter.isIncomingMessageComplete(ctx.incomingMessageBuffer());
  }

  private IncomingMessageHandlerExecutor incomingMessageHandlerExecutor(SocketChannel connection,
      ServerMessagingContext ctx) {
    return new IncomingMessageHandlerExecutor(connection, ctx, incomingMessageHandler, timeUtils);
  }

  private boolean setCompletedIfTimeout(ServerMessagingContext ctx, SelectionKey selectedKey,
      SocketChannel connection) throws IOException {
    var timeoutType = timeoutType(ctx);
    if (timeoutType == null) {
      return false;
    }
    log.warn("Read timeout: {}", ctx);

    ctx.isTimeoutOccurred(true);
    if (!properties.isLongLivedConnectionsSupported()) {
      connection.socket().shutdownInput();
    }
    selectedKey.interestOps(SelectionKey.OP_WRITE);

    executor.submit(new TimeoutHandlerExecutor(connection, ctx, timeoutType, timeoutHandler));
    return true;
  }

  private TimeoutType timeoutType(ServerMessagingContext ctx) {
    long now = timeUtils.epochMilli();
    return now - ctx.startTimestamp() >= properties.readTimeoutInMs() ? TimeoutType.READ : null;
  }

  private boolean setCompletedIfMaxIncomingMessageSize(ServerMessagingContext ctx,
      SelectionKey selectedKey, SocketChannel connection) throws IOException {
    if (ctx.incomingMessageBuffer().capacity() < properties.maxBufferSizeInBytes()) {
      return false;
    }
    log.debug("Incoming message reached max size: {}", ctx);

    ctx.isIncomingMessageComplete(true);
    if (!properties.isLongLivedConnectionsSupported()) {
      connection.socket().shutdownInput();
    }
    selectedKey.interestOps(SelectionKey.OP_WRITE);

    executor.submit(
        new MaxIncomingMessageSizeHandlerExecutor(connection, ctx, maxIncomingMessageSizeHandler));
    return true;
  }

  // Is this an efficient way?
  private void growBufferIfFull(ServerMessagingContext ctx) {
    if (ctx.incomingMessageBuffer().hasRemaining()) {
      return;
    }

    int newCapacity = Math.min(ctx.incomingMessageBuffer().capacity() * GROWTH_FACTOR,
        properties.maxBufferSizeInBytes());
    ByteBuffer grownBuffer = ByteBuffer.allocate(newCapacity);

    ctx.incomingMessageBuffer().flip(); // Switch to read mode
    grownBuffer.put(ctx.incomingMessageBuffer()); // Copy data to the grown buffer

    ctx.incomingMessageBuffer(grownBuffer);
  }

}
