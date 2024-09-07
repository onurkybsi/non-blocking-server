package org.kybprototyping.non_blocking_server;

import java.nio.channels.SocketChannel;
import org.kybprototyping.non_blocking_server.handler.TimeoutHandler;
import org.kybprototyping.non_blocking_server.handler.TimeoutType;
import org.kybprototyping.non_blocking_server.messaging.IncomingMessage;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
final class TimeoutHandlerExecutor implements Runnable {

  private final SocketChannel connection;
  private final ServerMessagingContext ctx;
  private final TimeoutType timeoutType;
  private final TimeoutHandler timeoutHandler;

  @Override
  public void run() {
    try {
      var incomingMessage = new IncomingMessage(connection.getRemoteAddress(),
          ctx.getIncomingMessageBuffer().array());
      var outgoingMessage = timeoutHandler.handle(incomingMessage, timeoutType).content();
      ctx.setOutgoingMessageBuffer(outgoingMessage);
      ctx.setOutgoingMessageComplete();
    } catch (Exception e) {
      /**
       * TODO: Call user exception handler? Or user should have catched the exception just close the
       * connection?
       */
    }
  }

}
