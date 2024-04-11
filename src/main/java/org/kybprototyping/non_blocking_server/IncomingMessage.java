package org.kybprototyping.non_blocking_server;

import java.nio.ByteBuffer;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

/**
 * Represents the client message sent.
 */
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
@Getter
final class IncomingMessage {

  private final ByteBuffer buffer;
  private final long timestamp;
  @Setter
  private boolean isComplete;

  static IncomingMessage of(ByteBuffer buffer) {
    return new IncomingMessage(buffer, Instant.now().getEpochSecond());
  }

}
