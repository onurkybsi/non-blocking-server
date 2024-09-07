package org.kybprototyping.non_blocking_server.test_implementations;

import java.nio.charset.StandardCharsets;
import org.kybprototyping.non_blocking_server.handler.IncomingMessageHandler;
import org.kybprototyping.non_blocking_server.messaging.IncomingMessage;
import org.kybprototyping.non_blocking_server.messaging.OutgoingMessage;
import lombok.extern.log4j.Log4j2;

@Log4j2
public final class TestHandler implements IncomingMessageHandler {

  public static final TestHandler instance = new TestHandler();

  @Override
  public OutgoingMessage handle(IncomingMessage incomingMessage) {
    log.info("Read: {}", new String(incomingMessage.content(), StandardCharsets.UTF_8));
    return new OutgoingMessage("OK".getBytes(StandardCharsets.UTF_8));
  }

}
