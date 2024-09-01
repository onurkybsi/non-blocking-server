package org.kybprototyping.non_blocking_server.messaging;

/**
 * Represents the user API that will handle the incoming message with more than the maximum allowed
 * size.
 */
public interface MaxIncomingMessageSizeHandler {

  /**
   * Generates the server message by incoming client message.
   * 
   * @param incomingMessage message sent by the client with more than the maximum allowed size
   * @return server response to the client's message
   */
  OutgoingMessage handle(IncomingMessage incomingMessage);

}
