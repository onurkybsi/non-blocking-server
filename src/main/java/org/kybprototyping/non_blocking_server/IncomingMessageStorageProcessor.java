package org.kybprototyping.non_blocking_server;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

final class IncomingMessageStorageProcessor implements MessagingProcessor {

  private static final Logger LOGGER = LogManager.getLogger(IncomingMessageStorageProcessor.class);

  @Override
  public void handle(ProcessorMessagingContext ctx) {
    String messageStorageBasePath = System.getenv("MESSAGE_STORAGE_PATH");
    if (messageStorageBasePath == null) {
      throw new RuntimeException("messageStorageBasePath couldn't be resolved!"); // NOSONAR
    }
    Path messageStoragePath = Path.of(messageStorageBasePath
        + "/%s-%s.txt".formatted(ctx.getSenderAddress(), Instant.now().getEpochSecond()));

    boolean isStoredSuccessfully = store(ctx.getIncomingMessage(), messageStoragePath);
    setOutgoingMessage(isStoredSuccessfully, ctx);
  }

  private static boolean store(byte[] content, Path path) {
    try (FileChannel channel =
        FileChannel.open(path, StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
      channel.write(ByteBuffer.wrap(content));
      return true;
    } catch (IOException e) {
      LOGGER.error("Message couldn't be stored: {}", path, e);
      return false;
    }
  }

  private static void setOutgoingMessage(boolean isStoredSuccessfully,
      ProcessorMessagingContext ctx) {
    if (isStoredSuccessfully) {
      ctx.setOutgoingMessage(ByteBuffer.wrap("SUCCESSFUL".getBytes()));
    } else {
      ctx.setOutgoingMessage(ByteBuffer.wrap("UNSUCCESSFUL".getBytes()));
    }
  }

}
