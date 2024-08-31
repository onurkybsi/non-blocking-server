package org.kybprototyping.non_blocking_server;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.kybprototyping.non_blocking_server.messaging.Formatter;
import org.kybprototyping.non_blocking_server.messaging.IncomingMessageHandler;
import org.kybprototyping.non_blocking_server.util.TimeUtils;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
final class Reader {

  private static final Logger logger = LogManager.getLogger(Reader.class);
  private static final ExecutorService executor =
      Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name("executor-", 0).factory());

  private final ServerProperties properties;
  private final Formatter formatter;
  private final TimeUtils timeUtils;
  private final IncomingMessageHandler incomingMessageHandler;

  void read(SelectionKey selectedKey) throws IOException {
    SocketChannel connection = (SocketChannel) selectedKey.channel();
    var ctx = (ServerMessagingContext) selectedKey.attachment();

    int bytesRead = connection.read(ctx.getIncomingMessageBuffer());
    /*
     * There is no need to check the connection status(connection.isConnected()). -1 should indicate
     * that the connection has closed by the client since if the incoming message is already
     * completed, we should have detected that during last read.
     */
    if (bytesRead == -1) {
      logger.warn("Connection closed before reading is completed: {}", connection);
      selectedKey.cancel();
      connection.close();
      return;
    }

    if (formatter.isIncomingMessageComplete(ctx.getIncomingMessageBuffer())) {
      logger.debug("Incoming message is complete: {}", connection);
      ctx.setIncomingMessageComplete();
      selectedKey.interestOps(SelectionKey.OP_WRITE);
      executor.submit(new IncomingMessageHandlerExecutor(connection, ctx, incomingMessageHandler));
      return;
    }

    if (isTimedOut(ctx)) {
      logger.warn("Connection timeout: {}", connection);
      selectedKey.cancel();
      connection.close();
    }
  }

  private boolean isTimedOut(ServerMessagingContext ctx) {
    long now = timeUtils.epochMilli();
    boolean isReadTimeoutOccurred = now - ctx.getStartTimestamp() >= properties.readTimeoutInMs();
    boolean isConnectionTimeoutOccurred =
        now - ctx.getStartTimestamp() >= properties.connectionTimeoutInMs();
    return isReadTimeoutOccurred || isConnectionTimeoutOccurred;
  }

}
