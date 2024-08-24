package org.kybprototyping.non_blocking_server.configuration;

public final class ServerConfigurationKeys {

  public static final String PORT = "port";
  public static final String TIMEOUT_SEC = "timeoutsec";

  private ServerConfigurationKeys() {
    throw new UnsupportedOperationException("This class is not initiable!");
  }
}
