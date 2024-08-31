package org.kybprototyping.non_blocking_server.messaging;

import java.net.SocketAddress;

/**
 * Represents the incoming message from the client.
 */
public record IncomingMessage(SocketAddress clientAddress, byte[] content) {
}
