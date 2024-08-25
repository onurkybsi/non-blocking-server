package org.kybprototyping.non_blocking_server;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

final class ServerTest {

  @Test
  void should_Be_Running() throws Exception {
    // given
    Server server = Server.build();
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
    Server server = Server.build();
    server.start();

    // when
    server.close();

    // then
    assertFalse(server.isRunning());
    assertFalse(server.isOpen());
  }

}
