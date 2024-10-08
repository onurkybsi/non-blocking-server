package org.kybprototyping.non_blocking_server;

import lombok.Builder;

/**
 * Server configuration values.
 */
@Builder
public record ServerProperties(Integer port, Integer minBufferSizeInBytes,
    Integer maxBufferSizeInBytes, Integer readTimeoutInMs, Integer messagingTimeoutInMs,
    Boolean isLongLivedConnectionsSupported) {
  public static class ServerPropertiesBuilder {
    /**
     * {@link ServerProperties} with default values.
     */
    ServerPropertiesBuilder() {
      port = 8080;
      minBufferSizeInBytes = 64;
      maxBufferSizeInBytes = 8000;
      readTimeoutInMs = 5000;
      messagingTimeoutInMs = 20000;
      isLongLivedConnectionsSupported = false;
    }
  }
}
