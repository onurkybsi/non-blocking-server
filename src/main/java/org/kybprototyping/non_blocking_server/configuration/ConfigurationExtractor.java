package org.kybprototyping.non_blocking_server.configuration;

import java.util.HashMap;
import java.util.Properties;
import java.util.regex.Pattern;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Class that extracts server configuration values in the following order:
 * 
 * <ul>
 * <li>1. Environment variables</li>
 * <li>2. {@code server.properties}</li>
 * <li>3. Server defaults</li>
 * </ul>
 * 
 * Note that this class is not thread-safe!
 */
public final class ConfigurationExtractor {

  private static final Logger logger = LogManager.getLogger(ConfigurationExtractor.class);
  private static final Properties serverProperties = new Properties();
  static {
    try {
      var inStream =
          ConfigurationExtractor.class.getClassLoader().getResourceAsStream("server.properties");
      serverProperties.load(inStream);
    } catch (Exception e) {
      logger.warn("server.properties could not be read!", e);
    }
  }
  private static final Pattern REGEX_NUMERIC = Pattern.compile("-?\\d+(\\.\\d+)?");

  private static final HashMap<String, String> cache = new HashMap<>();

  private ConfigurationExtractor() {
    throw new UnsupportedOperationException("This class is not initiable!");
  }

  /**
   * Returns the configuration by given key.
   * 
   * @param key configuration key
   * @return configuration value
   */
  public static String extract(String key) {
    if (cache.containsKey(key)) {
      return cache.get(key);
    }

    String fromEnvs = System.getenv(key);
    if (fromEnvs != null) {
      cache.put(key, fromEnvs);
      return fromEnvs;
    }

    String fromServerProperties = serverProperties.getProperty(key);
    if (fromServerProperties != null) {
      cache.put(key, fromEnvs);
      return fromServerProperties;
    }

    return ServerConfigurationDefaults.defaults.get(key);
  }

  /**
   * Returns the configuration by given key.
   * 
   * <p>
   * Tries to convert to {@code int}, if it cannot, it returns the server default value.
   * </p>
   * 
   * @param key configuration key
   * @return configuration value as {@code int}
   */
  public static int extractAsInteger(String key) {
    String value = extract(key);
    if (REGEX_NUMERIC.matcher(value).matches()) {
      return Integer.valueOf(value);
    }
    return Integer.valueOf(ServerConfigurationDefaults.defaults.get(key));
  }

}
