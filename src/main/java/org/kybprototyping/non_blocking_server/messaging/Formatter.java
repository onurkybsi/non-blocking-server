package org.kybprototyping.non_blocking_server.messaging;

import java.nio.ByteBuffer;

/**
 * Formatter that must be implemented by the client according to the message format that the server
 * accepts.
 * 
 * @author Onur Kayabasi(o.kayabasi@outlook.com)
 */
public interface Formatter {

  /**
   * Returns whether the incoming message is fully read or not.
   * 
   * <p>
   * This method is invoked every time some data is read over the network. This method should return
   * {@code true} if the reading should be terminated by the server based on the message format that
   * the client implements. When that happens, server stops to attempt to read and sends the
   * complete message to process.
   * 
   * @param incomingMessageBuffer incoming message buffer
   * @return {@code true} is the incoming message is complete, otherwise {@code false}
   */
  boolean isIncomingMessageComplete(ByteBuffer incomingMessageBuffer);

}
