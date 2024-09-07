package org.kybprototyping.non_blocking_server.test_implementations;

import java.nio.charset.StandardCharsets;
import org.kybprototyping.non_blocking_server.handler.MaxIncomingMessageSizeHandler;
import org.kybprototyping.non_blocking_server.messaging.IncomingMessage;
import org.kybprototyping.non_blocking_server.messaging.OutgoingMessage;

public final class TestMaxIncomingMessageSizeHandler implements MaxIncomingMessageSizeHandler {

  public static final TestMaxIncomingMessageSizeHandler instance =
      new TestMaxIncomingMessageSizeHandler();

  @Override
  public OutgoingMessage handle(IncomingMessage incomingMessage) {
    return new OutgoingMessage("INVALID_MESSAGE_SIZE".getBytes(StandardCharsets.UTF_8));
  }

}
