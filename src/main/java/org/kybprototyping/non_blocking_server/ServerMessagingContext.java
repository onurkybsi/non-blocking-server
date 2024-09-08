package org.kybprototyping.non_blocking_server;

import java.nio.ByteBuffer;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * Represents the messaging context between the client and the server.
 */
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
@Getter
@Setter(AccessLevel.PACKAGE)
@Accessors(fluent = true, chain = true, makeFinal = true)
final class ServerMessagingContext {

  private final long startTimestamp;
  private ByteBuffer incomingMessageBuffer;
  private boolean isIncomingMessageComplete;
  private volatile boolean isTimeoutOccurred;
  private volatile ByteBuffer outgoingMessageBuffer;
  private volatile boolean isOutgoingMessageComplete;
  private volatile long endTimestamp;

  static ServerMessagingContext of(long startTimestamp, ByteBuffer incomingMessageBuffer) {
    var ctx = new ServerMessagingContext(startTimestamp);
    ctx.incomingMessageBuffer(incomingMessageBuffer);
    return ctx;
  }

}
