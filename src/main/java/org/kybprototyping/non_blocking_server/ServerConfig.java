package org.kybprototyping.non_blocking_server;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Represents the server configuration values.
 */
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
@Getter
final class ServerConfig {

  private final int port;
  private final Path messageStoragePath;

  static ServerConfig build(String[] args) {
    if (args.length < 2) {
      throw new IllegalArgumentException("args were not passed properly!");
    }
    return new ServerConfig(extractInt(args[0]), extractPath(args[1]));
  }

  private static Integer extractInt(String s) {
    try {
      return Integer.parseInt(s);
    } catch (NumberFormatException | NullPointerException e) {
      throw new IllegalArgumentException("port couldn't be extracted!");
    }
  }

  private static Path extractPath(String s) {
    try {
      // TODO: Check if the path exists
      return Path.of(s);
    } catch (InvalidPathException e) {
      throw new IllegalArgumentException("messageStoragePath couldn't be extracted!");
    }
  }

}
