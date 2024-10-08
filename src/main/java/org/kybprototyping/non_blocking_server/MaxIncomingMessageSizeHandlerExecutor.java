package org.kybprototyping.non_blocking_server;

import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import org.kybprototyping.non_blocking_server.handler.MaxIncomingMessageSizeHandler;
import org.kybprototyping.non_blocking_server.messaging.IncomingMessage;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
final class MaxIncomingMessageSizeHandlerExecutor implements Runnable {

  private final SocketChannel connection;
  private final ServerMessagingContext ctx;
  private final MaxIncomingMessageSizeHandler maxIncomingMessageSizeHandler;

  @Override
  public void run() {
    try {
      var incomingMessage =
          new IncomingMessage(connection.getRemoteAddress(), ctx.incomingMessageBuffer().array());
      var outgoingMessage = maxIncomingMessageSizeHandler.handle(incomingMessage).content();
      ctx.outgoingMessageBuffer(ByteBuffer.wrap(outgoingMessage));
      ctx.isOutgoingMessageComplete(true);
    } catch (Exception e) {
      /**
       * TODO: Call user exception handler? Or user should have catched the exception just close the
       * connection?
       */
    }
  }

}
