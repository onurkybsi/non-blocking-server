package org.kybprototyping.non_blocking_server;

import static org.kybprototyping.non_blocking_server.configuration.ConfigurationExtractor.extractAsInteger;
import static org.kybprototyping.non_blocking_server.configuration.ServerConfigurationKeys.MAX_BUFFER_SIZE_BYTES;
import static org.kybprototyping.non_blocking_server.configuration.ServerConfigurationKeys.TIMEOUT_SEC;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.time.Instant;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
final class SelectedKeyAction implements Consumer<SelectionKey> {

  private static final int MAX_BUFFER_SIZE = extractAsInteger(MAX_BUFFER_SIZE_BYTES);
  private static final int MESSAGE_END_INDICATOR = 3;

  private static final Logger logger = LogManager.getLogger(SelectedKeyAction.class);
  private static final ExecutorService PROCESSOR_EXECUTOR = Executors
      .newThreadPerTaskExecutor(Thread.ofVirtual().name("processor-executor-", 0).factory());

  private final Selector selector;

  @Override
  public void accept(SelectionKey selectedKey) {
    try {
      if (selectedKey.isAcceptable()) {
        acceptConnection(selectedKey);
      } else if (isReadable(selectedKey)) {
        read(selectedKey);
      } else if (isWritable(selectedKey)) {
        write(selectedKey);
      } else {
        logger.warn("Unexpected key selected, it's being cancelled: {}", selectedKey);
        selectedKey.cancel();
      }
    } catch (Exception e) {
      logger.error("Something went wrong during processing selectedKey: {}", selectedKey, e);
    }
  }

  private void acceptConnection(SelectionKey selectedKey) throws IOException {
    ServerSocketChannel server = (ServerSocketChannel) selectedKey.channel();
    SocketChannel connection = server.accept();
    connection.configureBlocking(false);
    connection.register(selector, SelectionKey.OP_READ | SelectionKey.OP_WRITE,
        ServerMessagingContext.of(ByteBuffer.allocate(MAX_BUFFER_SIZE)));
  }

  private static void read(SelectionKey selectedKey) throws IOException {
    SocketChannel connection = (SocketChannel) selectedKey.channel();
    if (!connection.isOpen()) {
      logger.warn("Connection is already closed: {}", connection);
      selectedKey.cancel();
      return;
    }
    ServerMessagingContext ctx = (ServerMessagingContext) selectedKey.attachment();
    if (ctx.isIncomingMessageComplete()) {
      return;
    }

    ByteBuffer buffer = ctx.getIncomingMessageBuffer();
    connection.read(buffer);
    if (isIncomingMessageComplete(ctx)) {
      logger.debug("Incoming message is complete, it's being processed: {}", connection);
      ctx.setIncomingMessageComplete();
      PROCESSOR_EXECUTOR.submit(new MessagingProcessorExecutor(connection, ctx));
      return;
    }
    if (isTimedOut(ctx)) {
      logger.warn("Connection timeout occurred: {}", connection);
      connection.close();
      selectedKey.cancel();
    }
  }

  private static void write(SelectionKey selectedKey) throws IOException {
    SocketChannel connection = (SocketChannel) selectedKey.channel();
    if (!connection.isOpen()) {
      logger.warn("Connection is already closed: {}", connection);
      selectedKey.cancel();
      return;
    }
    ServerMessagingContext ctx = (ServerMessagingContext) selectedKey.attachment();
    if (!ctx.isOutgoingMessageComplete()) {
      return;
    }
    ByteBuffer buffer = ctx.getOutgoingMessageBuffer();
    if (buffer == null) {
      logger.warn("No outgoing message set, connection {} is being closed...", connection);
      connection.close();
      selectedKey.cancel();
      return;
    }
    if (!buffer.hasRemaining() && isTimedOut(ctx)) {
      logger.debug("Complete connection is being closed due to timeout: {}", connection);
      connection.close();
      selectedKey.cancel();
      return;
    }

    connection.write(buffer);
    if (!buffer.hasRemaining()) {
      logger.debug("Outgoing message has been completely written: {}", connection);
      return;
    }
    if (isTimedOut(ctx)) {
      logger.warn("Connection timeout occurred: {}", connection);
      connection.close();
      selectedKey.cancel();
    }
  }

  private static boolean isReadable(SelectionKey selectedKey) {
    if (!selectedKey.isReadable()) {
      return false;
    }
    if (!SocketChannel.class.isAssignableFrom(selectedKey.channel().getClass())) {
      return false;
    }
    if (!ServerMessagingContext.class.equals(selectedKey.attachment().getClass())) { // NOSONAR
      return false;
    }
    return true;
  }

  private static boolean isWritable(SelectionKey selectedKey) {
    if (!selectedKey.isWritable()) {
      return false;
    }
    if (!SocketChannel.class.isAssignableFrom(selectedKey.channel().getClass())) {
      return false;
    }
    if (!ServerMessagingContext.class.equals(selectedKey.attachment().getClass())) { // NOSONAR
      return false;
    }
    return true;
  }

  private static boolean isTimedOut(ServerMessagingContext ctx) {
    return Instant.now().getEpochSecond()
        - ctx.getStartTimestamp() >= extractAsInteger(TIMEOUT_SEC);
  }

  private static boolean isIncomingMessageComplete(ServerMessagingContext message) {
    ByteBuffer buffer = message.getIncomingMessageBuffer();
    buffer.flip();
    return buffer.limit() > 0 && buffer.get(buffer.limit() - 1) == MESSAGE_END_INDICATOR;
  }


}
