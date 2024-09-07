package org.kybprototyping.non_blocking_server;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import org.kybprototyping.non_blocking_server.util.TimeUtils;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
@Log4j2
final class Writer {

  private final ServerProperties properties;
  private final TimeUtils timeUtils;

  void write(SelectionKey selectedKey) throws IOException {
    SocketChannel connection = (SocketChannel) selectedKey.channel();
    /*
     * 'connection.isOpen' is not needed since we're the only one that can do that :) Watch out the
     * difference between isOpen and isConnected.
     */
    if (!connection.isConnected()) {
      log.warn("Connection closed before writing is completed: {}", connection);
      // That's needed. Maybe the connection is closed but the resource is still not released.
      closeConnection(selectedKey, connection);
      // TODO: We should let the user know that the connection closed while it builds the response!
      return;
    }

    var ctx = (ServerMessagingContext) selectedKey.attachment();
    if (!ctx.isOutgoingMessageComplete()) {
      log.trace("Outgoing message is waiting: {}", connection);
      return;
    }

    connection.write(ctx.getOutgoingMessageBuffer());
    if (!ctx.getOutgoingMessageBuffer().hasRemaining()) {
      log.debug("Outgoing message has been completely written: {}", connection);
      closeConnection(selectedKey, connection);
      return;
    }

    if (isTimedOut(ctx)) {
      log.warn("Connection timeout occurred: {}", connection);
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
