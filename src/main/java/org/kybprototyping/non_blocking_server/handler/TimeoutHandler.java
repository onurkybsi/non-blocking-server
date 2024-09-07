package org.kybprototyping.non_blocking_server.handler;

import org.kybprototyping.non_blocking_server.messaging.IncomingMessage;
import org.kybprototyping.non_blocking_server.messaging.OutgoingMessage;

/**
 * Represents the user API that will handle the timeouts that might occur during the messaging.
 */
public interface TimeoutHandler {

  /**
   * Generates the server message in case a timeout occurence.
   * 
   * @param incomingMessage message sent by the client. Note that this might contain an incomplete
   *        content.
   * @param type type of the timeout occurred
   * @return server response to the client's message
   */
  OutgoingMessage handle(IncomingMessage incomingMessage, TimeoutType type);

}
