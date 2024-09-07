package org.kybprototyping.non_blocking_server.test_implementations;

import java.nio.charset.StandardCharsets;
import org.kybprototyping.non_blocking_server.handler.TimeoutHandler;
import org.kybprototyping.non_blocking_server.handler.TimeoutType;
import org.kybprototyping.non_blocking_server.messaging.IncomingMessage;
import org.kybprototyping.non_blocking_server.messaging.OutgoingMessage;

public final class TestTimeoutHandler implements TimeoutHandler {

  public static final TestTimeoutHandler instance = new TestTimeoutHandler();

  @Override
  public OutgoingMessage handle(IncomingMessage incomingMessage, TimeoutType type) {
    return new OutgoingMessage("TIMEOUT".getBytes(StandardCharsets.UTF_8));
  }

}
