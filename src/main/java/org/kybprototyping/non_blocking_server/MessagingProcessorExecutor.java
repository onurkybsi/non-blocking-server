package org.kybprototyping.non_blocking_server;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
final class MessagingProcessorExecutor implements Runnable {

  private static final Logger LOGGER = LogManager.getLogger(MessagingProcessorExecutor.class);
  private static final List<MessagingProcessor> HANDLERS =
      Arrays.asList(new IncomingMessageStorageProcessor());

  private final SocketChannel connection;
  private final ServerMessagingContext ctx;

  @Override
  public void run() {
    LOGGER.debug("Executor started: {}", connection);
    var processorCtx = extractProcessorMessagingContext();
    if (processorCtx == null)
      return;

    for (var handler : HANDLERS) {
      try {
        handler.handle(processorCtx);
      } catch (Exception e) {
        LOGGER.error("Handler {} execution unsuccessful!", handler.getClass().getName(), e);
        setInternalServerError();
        break;
      }
    }
    LOGGER.debug("All handlers were executed!");

    if (!ctx.isOutgoingMessageComplete()) {
      ctx.setOutgoingMessageBuffer(processorCtx.getOutgoingMessage());
      ctx.setEndTimestamp(Instant.now().getEpochSecond());
      ctx.setOutgoingMessageComplete();
    }
  }

  private ProcessorMessagingContext extractProcessorMessagingContext() {
    try {
      return new ProcessorMessagingContext(connection.getRemoteAddress(),
          ctx.getIncomingMessageBuffer().array());
    } catch (IOException e) {
      LOGGER.error("Incoming message context couldn't be extracted!", e);
      setInternalServerError();
      return null;
    }
  }

  private void setInternalServerError() {
    ctx.setOutgoingMessageBuffer(ByteBuffer.wrap("INTERNAL_SERVER_ERROR".getBytes()));
    ctx.setEndTimestamp(Instant.now().getEpochSecond());
    ctx.setOutgoingMessageComplete();
  }

}
