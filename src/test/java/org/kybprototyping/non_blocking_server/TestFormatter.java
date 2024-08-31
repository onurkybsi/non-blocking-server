package org.kybprototyping.non_blocking_server;

import java.nio.ByteBuffer;
import org.kybprototyping.non_blocking_server.messaging.Formatter;

final class TestFormatter implements Formatter {

  static TestFormatter instance = new TestFormatter();

  @Override
  public boolean isIncomingMessageComplete(ByteBuffer incomingMessageBuffer) {
    if (incomingMessageBuffer.position() < 4) {
      return false;
    }

    int sizeOfMessage = incomingMessageBuffer.getInt(0);
    return incomingMessageBuffer.position() == sizeOfMessage + 4;
  }

}
