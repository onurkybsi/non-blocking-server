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

  /*
   * TODO: Use a custom wrapper to prevent the client server to write to buffer when it checks the
   * end of reading!
   */
  private final ByteBuffer incomingMessageBuffer;
  private final Long startTimestamp;

  private volatile boolean isIncomingMessageComplete;
  private volatile ByteBuffer outgoingMessageBuffer;
  private Long endTimestamp;
  private volatile boolean isOutgoingMessageComplete;

  static ServerMessagingContext of(ByteBuffer incomingMessageBuffer) {
    return new ServerMessagingContext(incomingMessageBuffer, Instant.now().toEpochMilli());
  }

  void setIncomingMessageComplete() {
    this.isIncomingMessageComplete = true;
  }

  void setOutgoingMessageComplete() {
    this.isOutgoingMessageComplete = true;
  }

  void setOutgoingMessageBuffer(byte[] outgoingMessage) {
    this.outgoingMessageBuffer = ByteBuffer.wrap(outgoingMessage);
  }

  void setEndTimestamp(Long endTimestamp) {
    this.endTimestamp = endTimestamp;
  }

}
