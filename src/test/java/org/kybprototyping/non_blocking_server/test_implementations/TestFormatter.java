package org.kybprototyping.non_blocking_server.test_implementations;

import java.nio.ByteBuffer;
import org.kybprototyping.non_blocking_server.messaging.Formatter;

public final class TestFormatter implements Formatter {

  public static TestFormatter instance = new TestFormatter();

  @Override
  public boolean isIncomingMessageComplete(ByteBuffer incomingMessageBuffer) {
    if (incomingMessageBuffer.position() < 4) {
      return false;
    }

    int sizeOfMessage = incomingMessageBuffer.getInt(0);
    return incomingMessageBuffer.position() == sizeOfMessage + 4;
  }

}
