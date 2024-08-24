package org.kybprototyping.non_blocking_server.configuration;

import java.util.HashMap;
import java.util.Map;

final class ServerConfigurationDefaults {

  static final HashMap<String, String> defaults = new HashMap<>(
      Map.of(ServerConfigurationKeys.PORT, "8080", ServerConfigurationKeys.TIMEOUT_SEC, "10"));

  private ServerConfigurationDefaults() {
    throw new UnsupportedOperationException("This class is not initiable!");
  }

}
