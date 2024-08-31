package org.kybprototyping.non_blocking_server;

import java.io.IOException;
import java.nio.channels.SocketChannel;
import org.kybprototyping.non_blocking_server.messaging.IncomingMessage;
import org.kybprototyping.non_blocking_server.messaging.IncomingMessageHandler;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
final class IncomingMessageHandlerExecutor implements Runnable {

  private final SocketChannel connection;
  private final ServerMessagingContext ctx;
  private final IncomingMessageHandler handler;

  @Override
  public void run() {
    try {
      var outgoingMessage = handler.handle(incomingMessage());
      ctx.setOutgoingMessageBuffer(outgoingMessage.content());
      ctx.setOutgoingMessageComplete();
    } catch (Exception e) {
      // TODO: Call user exception handler!
    }
  }

  private IncomingMessage incomingMessage() throws IOException {
    return new IncomingMessage(connection.getRemoteAddress(),
        ctx.getIncomingMessageBuffer().array());
  }

}
