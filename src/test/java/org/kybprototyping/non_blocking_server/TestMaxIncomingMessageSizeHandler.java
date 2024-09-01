package org.kybprototyping.non_blocking_server;

import java.nio.charset.StandardCharsets;
import org.kybprototyping.non_blocking_server.messaging.IncomingMessage;
import org.kybprototyping.non_blocking_server.messaging.MaxIncomingMessageSizeHandler;
import org.kybprototyping.non_blocking_server.messaging.OutgoingMessage;

final class TestMaxIncomingMessageSizeHandler implements MaxIncomingMessageSizeHandler {

  static final TestMaxIncomingMessageSizeHandler instance = new TestMaxIncomingMessageSizeHandler();

  @Override
  public OutgoingMessage handle(IncomingMessage incomingMessage) {
    return new OutgoingMessage("INVALID_MESSAGE_SIZE".getBytes(StandardCharsets.UTF_8));
  }

}
