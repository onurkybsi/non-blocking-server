package org.kybprototyping.non_blocking_server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

final class ServerTest {

  private static final Logger logger = LogManager.getLogger(ServerTest.class);

  private static Server underTest;

  @BeforeAll
  static void setUp() throws Exception {
    underTest = Server.build(ServerProperties.builder().port(8080).minBufferSizeInBytes(64).build(),
        TestFormatter.instance, Clock.systemDefaultZone(), TestHandler.instance,
        TestMaxIncomingMessageSizeHandler.instance);
    underTest.start();
  }

  @AfterAll
  static void cleanUp() throws Exception {
    underTest.close();
  }

  @Test
  void should_Be_Running() throws Exception {
    // given
    Server server =
        Server.build(ServerProperties.builder().port(8081).build(), TestFormatter.instance,
            Clock.systemUTC(), TestHandler.instance, TestMaxIncomingMessageSizeHandler.instance);
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
    Server server =
        Server.build(ServerProperties.builder().port(8081).build(), TestFormatter.instance,
            Clock.systemUTC(), TestHandler.instance, TestMaxIncomingMessageSizeHandler.instance);
    server.start();

    // when
    server.close();

    // then
    assertFalse(server.isRunning());
    assertFalse(server.isOpen());
  }

  @ParameterizedTest
  @ValueSource(ints = {1, 10, 100, 1000, 10_000})
  void should_Process_Client_Messages(int numberOfMessages) throws Exception {
    // given
    List<String> outgoingMessages = new ArrayList<>(numberOfMessages);
    IntStream.range(0, numberOfMessages)
        .forEach(i -> outgoingMessages.add("Hello from %s!".formatted(i)));

    // when
    var actual = TestClient.send(outgoingMessages);

    // then
    assertTrue(actual.stream().allMatch(m -> "OK".equals(m)));
  }

  @Test
  void should_Process_Concurrent_Connections() throws Exception {
    // given
    ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    // when
    var futures = new ArrayList<Future<String>>();
    int numberOfMessages = 1_000;
    for (int i = 0; i < numberOfMessages; i++) {
      final int ix = i;
      final String outgoingMessage = "Hello from %s!".formatted(ix);
      futures.add(executor.submit(() -> {
        try {
          Thread.sleep(ix * 15);
          return TestClient.send(outgoingMessage);
        } catch (InterruptedException e) {
          return e.getMessage();
        }
      }));
    }
    executor.shutdown();
    executor.awaitTermination(1, TimeUnit.MINUTES);

    // then
    var outgoingMessages = futures.stream().map(f -> {
      try {
        return f.get();
      } catch (Exception e) {
        logger.warn(e.getMessage());
        return e.getMessage();
      }
    }).toList();
    assertTrue(outgoingMessages.stream().allMatch(m -> "OK".equals(m)));
  }

  @Test
  void should_Grow_Incoming_Message_Buffer_Size_When_Incoming_Message_Is_Bigger_Than_Existing_Buffer()
      throws Exception {
    // given
    int port = 8081;
    int minBufferSizeInBytes = 1;
    var serverProperties =
        ServerProperties.builder().port(port).minBufferSizeInBytes(minBufferSizeInBytes).build();
    Server server = Server.build(serverProperties, TestFormatter.instance, Clock.systemUTC(),
        TestHandler.instance, TestMaxIncomingMessageSizeHandler.instance);
    server.start();
    String message = "12";

    // when
    var actual = TestClient.send(port, message);

    // then
    assertEquals("OK", actual);
  }

  @Test
  void should_Return_User_Max_Incoming_Message_Size_Message_When_Max_Incoming_Message_Size_Has_Reached()
      throws Exception {
    // given
    int port = 8081;
    int minBufferSizeInBytes = 1;
    int maxBufferSizeInBytes = 5;
    var serverProperties = ServerProperties.builder().port(port)
        .minBufferSizeInBytes(minBufferSizeInBytes).maxBufferSizeInBytes(maxBufferSizeInBytes)
        .readTimeoutInMs(100_000).connectionTimeoutInMs(100_000).build();
    Server server = Server.build(serverProperties, TestFormatter.instance, Clock.systemUTC(),
        TestHandler.instance, TestMaxIncomingMessageSizeHandler.instance);
    server.start();
    String message = "12";

    // when
    var actual = TestClient.send(port, message);

    // then
    assertEquals("INVALID_MESSAGE_SIZE", actual);
    server.close();
  }

}
