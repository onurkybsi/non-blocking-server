package org.kybprototyping.non_blocking_server;

import lombok.Builder;

/**
 * Server configuration values.
 */
@Builder
public record ServerProperties(Integer port, Integer maxBufferSizeInBytes, Integer timeoutInMs) {
  public static class ServerPropertiesBuilder {
    /**
     * {@link ServerProperties} with default values.
     */
    ServerPropertiesBuilder() {
      port = 8080;
      maxBufferSizeInBytes = 8000;
      timeoutInMs = 10;
    }
  }
}
