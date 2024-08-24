package org.kybprototyping.non_blocking_server;

import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.IOException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

final class ServerTest {

  private static Server underTest;

  @BeforeAll
  static void setUp() throws IOException {
    underTest = Server.build();
  }

  @AfterAll
  static void cleanUp() throws InterruptedException {
    underTest.stop();
  }

  @Test
  void should_Be_Running() throws Exception {
    // given
    Server server = Server.build();
    server.run();

    // when
    var actual = server.isRunning();

    // then
    assertTrue(actual);
    server.stop();
  }

}
