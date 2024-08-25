package org.kybprototyping.non_blocking_server;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

final class ServerTest {

  private static Server underTest;

  @BeforeAll
  static void setUp() throws Exception {
    underTest = Server.build(ServerProperties.builder().port(8080).build());
  }

  @AfterAll
  static void cleanUp() throws Exception {
    underTest.close();
  }

  @Test
  void should_Be_Running() throws Exception {
    // given
    Server server = Server.build(ServerProperties.builder().port(8081).build());
    server.start();

    // when
    var actual = server.isRunning();

    // then
    assertTrue(actual);
    server.close();
  }

  @Test
  void should_Be_Closed() throws Exception {
    // given
    Server server = Server.build(ServerProperties.builder().port(8081).build());
    server.start();

    // when
    server.close();

    // then
    assertFalse(server.isRunning());
    assertFalse(server.isOpen());
  }

}
