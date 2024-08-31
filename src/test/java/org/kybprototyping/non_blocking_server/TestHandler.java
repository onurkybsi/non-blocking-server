package org.kybprototyping.non_blocking_server;

import java.nio.charset.StandardCharsets;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.kybprototyping.non_blocking_server.messaging.IncomingMessage;
import org.kybprototyping.non_blocking_server.messaging.IncomingMessageHandler;
import org.kybprototyping.non_blocking_server.messaging.OutgoingMessage;

final class TestHandler implements IncomingMessageHandler {

  private static final Logger logger = LogManager.getLogger(TestHandler.class);

  static final TestHandler instance = new TestHandler();

  @Override
  public OutgoingMessage handle(IncomingMessage incomingMessage) {
    logger.info("Read: {}", new String(incomingMessage.content(), StandardCharsets.UTF_8));
    return new OutgoingMessage("OK".getBytes(StandardCharsets.UTF_8));
  }

}
