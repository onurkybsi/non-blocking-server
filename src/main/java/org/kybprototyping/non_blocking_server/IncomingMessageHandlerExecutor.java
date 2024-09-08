package org.kybprototyping.non_blocking_server;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import org.kybprototyping.non_blocking_server.handler.IncomingMessageHandler;
import org.kybprototyping.non_blocking_server.messaging.IncomingMessage;
import org.kybprototyping.non_blocking_server.util.TimeUtils;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
final class IncomingMessageHandlerExecutor implements Runnable {

  private final SocketChannel connection;
  private final ServerMessagingContext ctx;
  private final IncomingMessageHandler handler;
  private final TimeUtils timeUtils;

  @Override
  public void run() {
    try {
      var outgoingMessage = handler.handle(incomingMessage());
      ctx.outgoingMessageBuffer(ByteBuffer.wrap(outgoingMessage.content()));
      ctx.isOutgoingMessageComplete(true);
      ctx.endTimestamp(timeUtils.epochMilli());
      // TODO: Wait until the outgoing messsage is successfully written? How to let the user know? A
      // callback through CountDownLatch?
    } catch (Exception e) {
      /**
       * TODO: Call user exception handler? Or user should have catched the exception just close the
       * connection?
       */
    }
  }

  private IncomingMessage incomingMessage() throws IOException {
    return new IncomingMessage(connection.getRemoteAddress(), ctx.incomingMessageBuffer().array());
  }

}
