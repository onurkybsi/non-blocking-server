package org.kybprototyping.non_blocking_server;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.kybprototyping.non_blocking_server.util.TimeUtils;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
final class Writer {

  private static final Logger logger = LogManager.getLogger(Writer.class);

  private final ServerProperties properties;
  private final TimeUtils timeUtils;

  void write(SelectionKey selectedKey) throws IOException {
    SocketChannel connection = (SocketChannel) selectedKey.channel();
    if (!connection.isConnected()) {
      logger.warn("Connection closed before writing is completed: {}", connection);
      // That's needed. Maybe the connection is closed but the resource is still not released.
      closeConnection(selectedKey, connection);
      return;
    }

    var ctx = (ServerMessagingContext) selectedKey.attachment();
    if (!ctx.isOutgoingMessageComplete()) {
      logger.trace("Outgoing message is waiting: {}", connection);
      return;
    }

    connection.write(ctx.getOutgoingMessageBuffer());
    if (!ctx.getOutgoingMessageBuffer().hasRemaining()) {
      logger.debug("Outgoing message has been completely written: {}", connection);
      closeConnection(selectedKey, connection);
      return;
    }

    if (isTimedOut(ctx)) {
      logger.warn("Connection timeout occurred: {}", connection);
      closeConnection(selectedKey, connection);
    }
  }

  private void closeConnection(SelectionKey selectedKey, SocketChannel channel) throws IOException {
    selectedKey.cancel();
    /*
     * Note that this doesn't not only close the connection with the client. It also releases the
     * resource allocated for the connection which is essential for us to do!
     */
    channel.close();
  }


  private boolean isTimedOut(ServerMessagingContext ctx) {
    long now = timeUtils.epochMilli();
    return now - ctx.getStartTimestamp() >= properties.connectionTimeoutInMs();
  }

}
