package org.kybprototyping.non_blocking_server;

import java.net.SocketAddress;
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
  private final SocketAddress remoteAddress;
  private ByteBuffer incomingMessageBuffer;
  private boolean isIncomingMessageComplete;
  private volatile boolean isTimeoutOccurred;
  private volatile ByteBuffer outgoingMessageBuffer;
  private volatile boolean isOutgoingMessageComplete;
  private volatile long endTimestamp;

  static ServerMessagingContext of(long startTimestamp, SocketAddress remoteAddress,
      ByteBuffer incomingMessageBuffer) {
    var ctx = new ServerMessagingContext(startTimestamp, remoteAddress);
    ctx.incomingMessageBuffer(incomingMessageBuffer);
    return ctx;
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + (int) (startTimestamp ^ (startTimestamp >>> 32));
    result = prime * result + ((remoteAddress == null) ? 0 : remoteAddress.hashCode());
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj)
      return true;
    if (obj == null)
      return false;
    if (getClass() != obj.getClass())
      return false;
    ServerMessagingContext other = (ServerMessagingContext) obj;
    if (startTimestamp != other.startTimestamp)
      return false;
    if (remoteAddress == null) {
      if (other.remoteAddress != null)
        return false;
    } else if (!remoteAddress.equals(other.remoteAddress))
      return false;
    return true;
  }

}
