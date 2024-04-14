package org.kybprototyping.non_blocking_server;

import java.net.SocketAddress;
import java.nio.ByteBuffer;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
@Getter
public class ProcessorMessagingContext {

  private final SocketAddress senderAddress;
  private final byte[] incomingMessage;
  private ByteBuffer outgoingMessage = null;

  public void setOutgoingMessage(ByteBuffer outgoingMessage) {
    this.outgoingMessage = outgoingMessage;
  }

}
