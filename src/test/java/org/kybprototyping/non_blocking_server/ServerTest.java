package org.kybprototyping.non_blocking_server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.kybprototyping.non_blocking_server.test_implementations.TestFormatter;
import org.kybprototyping.non_blocking_server.test_implementations.TestHandler;
import org.kybprototyping.non_blocking_server.test_implementations.TestMaxIncomingMessageSizeHandler;
import org.kybprototyping.non_blocking_server.test_implementations.TestTimeoutHandler;
import lombok.extern.log4j.Log4j2;

@Log4j2
final class ServerTest {

  private static Server underTest;

  @BeforeAll
  static void setUp() throws Exception {
    underTest = testServer(ServerProperties.builder().build());
    underTest.start();
  }

  @AfterAll
  static void cleanUp() throws Exception {
    underTest.close();
  }

  @Test
  void should_Be_Running() throws Exception {
    // given
    var properties = ServerProperties.builder().port(8081).build();
    Server server = testServer(properties);
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
    var properties = ServerProperties.builder().port(8081).build();
    Server server = testServer(properties);
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
        log.warn(e.getMessage());
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
    var properties =
        ServerProperties.builder().port(port).minBufferSizeInBytes(minBufferSizeInBytes).build();
    Server server = testServer(properties);
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
    var properties =
        ServerProperties.builder().port(port).minBufferSizeInBytes(minBufferSizeInBytes)
            .maxBufferSizeInBytes(maxBufferSizeInBytes).build();
    Server server = testServer(properties);
    server.start();
    String message = "12";

    // when
    var actual = TestClient.send(port, message);

    // then
    assertEquals("INVALID_MESSAGE_SIZE", actual);
    server.close();
  }

  @Test
  void should_Return_Timeout_Handlers_Response_When_Read_Timeout_Occurred()
      throws IOException, InterruptedException {
    // given
    int port = 8081;
    int readTimeoutInMs = 0;
    int messagingTimeoutInMs = 2_000;
    var properties = ServerProperties.builder().port(port).readTimeoutInMs(readTimeoutInMs)
        .messagingTimeoutInMs(messagingTimeoutInMs).build();
    Server server = testServer(properties);
    server.start();
    String message = "Hello!";

    // when
    String actual = null;
    try (Socket socket = TestClient.buildSocket(8081)) {
      OutputStream out = socket.getOutputStream();
      byte[] sizeBytes = ByteBuffer.allocate(4).putInt(message.length()).array();
      out.write(sizeBytes);
      out.flush();
      // Causes the timeout...
      Thread.sleep(250);
      byte[] messageBytes = message.getBytes(StandardCharsets.UTF_8);
      out.write(messageBytes);
      socket.shutdownOutput();

      InputStream in = socket.getInputStream();
      var byteArrayOutputStream = new ByteArrayOutputStream();
      byte[] buffer = new byte[64];
      int bytesRead;
      while ((bytesRead = in.read(buffer)) != -1) {
        byteArrayOutputStream.write(buffer, 0, bytesRead);
      }

      actual = byteArrayOutputStream.toString(StandardCharsets.UTF_8.name());
    }

    // then
    assertEquals("TIMEOUT", actual);
    server.close();
  }

  @Test
  void should_Close_Connection_When_Client_Did_Not_Read_Response_Before_Connection_Timeout()
      throws IOException, InterruptedException {
    // given
    int port = 8081;
    int readTimeoutInMs = 500;
    int messagingTimeoutInMs = 1_000;
    var properties = ServerProperties.builder().port(port).readTimeoutInMs(readTimeoutInMs)
        .messagingTimeoutInMs(messagingTimeoutInMs).build();
    Server server = testServer(properties);
    server.start();
    String message = "Hello!";

    // when
    assertThrows(IOException.class, () -> {
      try (Socket socket = TestClient.buildSocket(8081)) {
        OutputStream out = socket.getOutputStream();
        byte[] sizeBytes = ByteBuffer.allocate(4).putInt(message.length()).array();
        out.write(sizeBytes);
        Thread.sleep(messagingTimeoutInMs);
        byte[] messageBytes = message.getBytes(StandardCharsets.UTF_8);
        out.write(messageBytes);
        socket.shutdownOutput();

        // Causes the timeout...
        InputStream in = socket.getInputStream();
        var byteArrayOutputStream = new ByteArrayOutputStream();
        byte[] buffer = new byte[64];
        int bytesRead;
        while ((bytesRead = in.read(buffer)) != -1) {
          byteArrayOutputStream.write(buffer, 0, bytesRead);
        }
      }
    });

    // then
    server.close();
  }

  @Test
  void should_Support_Long_Lived_Connections() throws IOException, InterruptedException {
    // given
    int port = 8081;
    int readTimeoutInMs = 500;
    int messagingTimeoutInMs = 1_000;
    var properties = ServerProperties.builder().port(port).readTimeoutInMs(readTimeoutInMs)
        .messagingTimeoutInMs(messagingTimeoutInMs).isLongLivedConnectionsSupported(true).build();
    Server server = testServer(properties);
    server.start();
    String message = "Hello!";

    // when & then
    // Three messages with the same connection.
    try (Socket socket = TestClient.buildSocket(8081)) {
      assertEquals("OK", TestClient.send(socket, message, false, 2));
      Thread.sleep(messagingTimeoutInMs);
      assertEquals("OK", TestClient.send(socket, message, false, 2));
      Thread.sleep(messagingTimeoutInMs);
      assertEquals("OK", TestClient.send(socket, message, false, 2));
    }
    server.close();
  }

  @Test
  @Disabled
  void should_Support_Parallel_Processing_With_Long_Lived_Connections()
      throws IOException, InterruptedException {
    // given
    int port = 8081;
    int readTimeoutInMs = 500;
    int messagingTimeoutInMs = 1_000;
    var properties = ServerProperties.builder().port(port).readTimeoutInMs(readTimeoutInMs)
        .messagingTimeoutInMs(messagingTimeoutInMs).isLongLivedConnectionsSupported(true).build();
    Server server = testServer(properties);
    server.start();
    ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    HashMap<Integer, Socket> connections = new HashMap<>();
    int numberOfConnections = 5;
    for (int i = 1; i <= numberOfConnections; i++) {
      Socket socket = TestClient.buildSocket(8081);
      connections.put(i, socket);
    }

    // when
    var futures = new ArrayList<Future<String>>();
    int numberOfMessages = 1_000;
    for (int i = 1; i <= numberOfConnections; i++) {
      for (int j = 0; j < numberOfMessages; j++) {
        final int connectionNum = i;
        final int messageNum = j;
        final String outgoingMessage =
            "Connection %s & Message %s".formatted(connectionNum, messageNum);
        futures.add(executor.submit(
            () -> TestClient.send(connections.get(connectionNum), outgoingMessage, false, 2)));
      }
    }
    executor.shutdown();
    executor.awaitTermination(1, TimeUnit.MINUTES);

    // then
    var outgoingMessages = futures.stream().map(f -> {
      try {
        return f.get();
      } catch (Exception e) {
        log.warn(e.getMessage());
        return e.getMessage();
      }
    }).toList();
    assertTrue(outgoingMessages.stream().allMatch(m -> "OK".equals(m)));
    connections.entrySet().forEach(e -> {
      try {
        e.getValue().close();
      } catch (IOException e1) {
        e1.printStackTrace();
      }
    });
    server.close();
  }

  private static Server testServer(ServerProperties properties) throws IOException {
    return Server
        .builder(TestFormatter.instance, TestHandler.instance,
            TestMaxIncomingMessageSizeHandler.instance, TestTimeoutHandler.instance)
        .properties(properties).build();
  }

}
