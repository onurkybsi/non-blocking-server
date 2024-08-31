package org.kybprototyping.non_blocking_server.messaging;

/**
 * Represents the outgoing message to the client.
 */
public record OutgoingMessage(byte[] content) {
}
