package org.kybprototyping.non_blocking_server.handler;

import org.kybprototyping.non_blocking_server.messaging.IncomingMessage;
import org.kybprototyping.non_blocking_server.messaging.OutgoingMessage;

/**
 * Represents the user API that will handle the incoming message and generate the outgoing message.
 */
public interface IncomingMessageHandler {

  /**
   * Generates the server message by incoming client message.
   * 
   * @param incomingMessage message sent by the client
   * @return server response to the client's message
   */
  OutgoingMessage handle(IncomingMessage incomingMessage);

}
