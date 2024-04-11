package org.kybprototyping.non_blocking_server;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.SocketChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import lombok.RequiredArgsConstructor;

/**
 * Represents the processor of the server's incoming messages.
 */
@RequiredArgsConstructor
final class IncomingMessageProcessor implements Runnable {

  private static final int MESSAGE_END_INDICATOR = 3;
  private static final byte[] SUCCESSFUL_PROCESSING_RESPONSE = "MESSAGE_PROCESSED".getBytes();

  private static final Logger LOGGER = LogManager.getLogger(IncomingMessageProcessor.class);

  private final SocketChannel connection;
  private final IncomingMessage message;
  private final ServerConfig config;

  static IncomingMessageProcessor from(SocketChannel connection, IncomingMessage message,
      ServerConfig config) {
    return new IncomingMessageProcessor(connection, message, config);
  }

  @Override
  public void run() {
    ByteBuffer buffer = message.getBuffer();
    buffer.flip();

    if (buffer.get(buffer.limit() - 1) != MESSAGE_END_INDICATOR) {
      return;
    }
    message.setComplete(true);

    store(buffer, connection, config);
    writeResponse(connection);
  }

  private static void store(ByteBuffer buffer, SocketChannel connection, ServerConfig config) {
    try {
      var remoteAddress = connection.getRemoteAddress();
      var timestamp = Instant.now().getEpochSecond();
      String basePath = config.getMessageStoragePath().toString();
      Path messageStoragePath =
          Path.of(basePath + "/%s-%s.txt".formatted(remoteAddress, timestamp));

      store(buffer, messageStoragePath);
    } catch (Exception e) {
      LOGGER.error("Message couldn't be stored!", e);
    }
  }

  private static void store(ByteBuffer buffer, Path path) {
    try (FileChannel channel =
        FileChannel.open(path, StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
      buffer.limit(buffer.limit() - 1);
      channel.write(buffer);
    } catch (IOException e) {
      LOGGER.error("Message couldn't be stored: {}", path, e);
    }
  }

  private static void writeResponse(SocketChannel connection) {
    try (connection) {
      int written = 0;

      do {
        written = connection.write(ByteBuffer.wrap(SUCCESSFUL_PROCESSING_RESPONSE));
      } while (written != SUCCESSFUL_PROCESSING_RESPONSE.length);
    } catch (IOException e) {
      LOGGER.error("Response couldn't be written!", e);
    }
  }

}
