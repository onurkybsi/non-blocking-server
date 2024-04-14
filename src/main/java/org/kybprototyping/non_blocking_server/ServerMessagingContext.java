package org.kybprototyping.non_blocking_server;

import java.nio.ByteBuffer;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Represents the messaging context between the client and the server.
 */
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
@Getter
final class ServerMessagingContext {

  private final ByteBuffer incomingMessageBuffer;
  private final Long startTimestamp;
  private boolean isIncomingMessageComplete;
  private ByteBuffer outgoingMessageBuffer;
  private Long endTimestamp;
  private boolean isOutgoingMessageComplete;

  static ServerMessagingContext of(ByteBuffer incomingMessageBuffer) {
    return new ServerMessagingContext(incomingMessageBuffer, Instant.now().getEpochSecond());
  }

  void setIncomingMessageComplete() {
    this.isIncomingMessageComplete = true;
  }

  void setOutgoingMessageComplete() {
    this.isOutgoingMessageComplete = true;
  }

  void setOutgoingMessageBuffer(ByteBuffer outgoingMessageBuffer) {
    this.outgoingMessageBuffer = outgoingMessageBuffer;
  }

  void setEndTimestamp(Long endTimestamp) {
    this.endTimestamp = endTimestamp;
  }

}
