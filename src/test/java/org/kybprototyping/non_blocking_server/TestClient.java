package org.kybprototyping.non_blocking_server;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

final class TestClient {

  private static final int CONNECTION_MAX_TRY_COUNT = 5;

  private static final Logger logger = LogManager.getLogger(TestClient.class);

  static String send(String outgoingMessage) throws IOException {
    try (Socket socket = buildSocket()) {
      return send(socket, outgoingMessage);
    }
  }

  static List<String> send(List<String> outgoingMessages) throws IOException {
    List<String> responses = new ArrayList<>();

    for (String outgoingMessage : outgoingMessages) {
      try (Socket socket = buildSocket()) {
        responses.add(send(socket, outgoingMessage));
      }
    }

    return responses;
  }

  private static String send(Socket socket, String outgoingMessage) throws IOException {
    OutputStream out = socket.getOutputStream();
    byte[] sizeBytes = ByteBuffer.allocate(4).putInt(outgoingMessage.length()).array();
    out.write(sizeBytes);
    byte[] messageBytes = outgoingMessage.getBytes(StandardCharsets.UTF_8);
    out.write(messageBytes);
    out.flush();

    InputStream in = socket.getInputStream();
    var byteArrayOutputStream = new ByteArrayOutputStream();
    byte[] buffer = new byte[64];
    int bytesRead;
    while ((bytesRead = in.read(buffer)) != -1) {
      byteArrayOutputStream.write(buffer, 0, bytesRead);
    }

    return byteArrayOutputStream.toString(StandardCharsets.UTF_8.name());
  }

  private static Socket buildSocket() {
    int tryCount = 1;
    do {
      try {
        Thread.sleep((tryCount - 1) * 500);

        return new Socket("localhost", 8080);
      } catch (Exception e) {
        tryCount++;
        logger.warn("Exception occurred, retrying {}. times: {}", e.getMessage());
      }
    } while (tryCount <= CONNECTION_MAX_TRY_COUNT);
    throw new RuntimeException("Connection could not be established!");
  }

}
