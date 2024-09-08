package org.kybprototyping.non_blocking_server;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.concurrent.ExecutorService;
import org.kybprototyping.non_blocking_server.handler.TimeoutHandler;
import org.kybprototyping.non_blocking_server.handler.TimeoutType;
import org.kybprototyping.non_blocking_server.util.TimeUtils;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
@Log4j2
final class Writer {

  private final ServerProperties properties;
  private final TimeUtils timeUtils;
  private final TimeoutHandler timeoutHandler;
  private final ExecutorService executor;

  void write(SelectionKey selectedKey) throws IOException {
    SocketChannel connection = (SocketChannel) selectedKey.channel();
    /*
     * 'connection.isOpen' is not needed since we're the only one that can do that :) Watch out the
     * difference between isOpen and isConnected.
     */
    if (!connection.isConnected()) {
      log.debug("Connection is already closed: {}", connection);
      selectedKey.channel();
      /*
       * Note that this doesn't not only close the connection with the client. It also releases the
       * resource allocated for the connection which is essential for us to do!
       */
      connection.close();
      // TODO: We should let the user know that the connection closed while it builds the response!
      return;
    }

    var ctx = (ServerMessagingContext) selectedKey.attachment();
    if (!ctx.isOutgoingMessageComplete()) {
      log.trace("Outgoing message is waiting: {}", connection);
      return;
    }

    connection.write(ctx.outgoingMessageBuffer());
    if (!ctx.outgoingMessageBuffer().hasRemaining()) {
      log.debug("Outgoing message has been completely written: {}", connection);
      shutdownConnection(connection, ctx, selectedKey);
      return;
    }

    if (!ctx.isTimeoutOccurred()) {
      setCompletedIfTimeout(ctx, selectedKey, connection);
    }
  }

  private void shutdownConnection(SocketChannel channel, ServerMessagingContext ctx,
      SelectionKey selectedKey) throws IOException {
    if (channel.socket().getSoLinger() != -1) {
      return;
    }

    long timeElapsed = timeUtils.epochMilli() - ctx.startTimestamp();
    long lingerDuration = Math.max(properties.connectionTimeoutInMs() - timeElapsed, 0);
    /**
     * If all bytes are received by the peer and the send buffer is empty, the socket will close
     * immediately, and the linger timeout will have no effect.
     */
    channel.socket().setSoLinger(true, (int) lingerDuration);
    channel.close();
    log.debug("Connection closed with linger duration {} ms", lingerDuration);
  }

  private boolean setCompletedIfTimeout(ServerMessagingContext ctx, SelectionKey selectedKey,
      SocketChannel connection) throws IOException {
    var timeoutType = timeoutType(ctx);
    if (timeoutType == null) {
      return false;
    }

    log.warn("Connection timeout: {}", connection);
    ctx.isIncomingMessageComplete(true);
    ctx.isTimeoutOccurred(true);
    selectedKey.interestOps(SelectionKey.OP_WRITE);
    executor.submit(new TimeoutHandlerExecutor(connection, ctx, timeoutType, timeoutHandler));
    return true;
  }

  private TimeoutType timeoutType(ServerMessagingContext ctx) {
    boolean isConnectionTimeoutOccurred =
        timeUtils.epochMilli() - ctx.startTimestamp() >= properties.connectionTimeoutInMs();
    return isConnectionTimeoutOccurred ? TimeoutType.CONNECTION : null;
  }

}
