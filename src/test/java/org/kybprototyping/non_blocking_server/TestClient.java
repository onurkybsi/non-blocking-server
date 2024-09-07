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
import lombok.extern.log4j.Log4j2;

@Log4j2
final class TestClient {

  private static final int CONNECTION_MAX_TRY_COUNT = 5;

  static String send(String outgoingMessage) throws IOException {
    try (Socket socket = buildSocket(8080)) {
      return send(socket, outgoingMessage);
    }
  }

  static String send(int port, String outgoingMessage) throws IOException {
    try (Socket socket = buildSocket(port)) {
      return send(socket, outgoingMessage);
    }
  }

  static List<String> send(List<String> outgoingMessages) throws IOException {
    List<String> responses = new ArrayList<>();

    for (String outgoingMessage : outgoingMessages) {
      try (Socket socket = buildSocket(8080)) {
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
    // Sends the FIN flag to the server which indicates that the writing of the client is done.
    socket.shutdownOutput();

    InputStream in = socket.getInputStream();
    var byteArrayOutputStream = new ByteArrayOutputStream();
    byte[] buffer = new byte[64];
    int bytesRead;
    while ((bytesRead = in.read(buffer)) != -1) {
      byteArrayOutputStream.write(buffer, 0, bytesRead);
    }

    return byteArrayOutputStream.toString(StandardCharsets.UTF_8.name());
  }

  private static Socket buildSocket(int port) {
    int tryCount = 1;
    do {
      try {
        Thread.sleep((tryCount - 1) * 500);

        return new Socket("localhost", port);
      } catch (Exception e) {
        tryCount++;
        log.warn("Exception occurred, retrying {}. times: {}", e.getMessage());
      }
    } while (tryCount <= CONNECTION_MAX_TRY_COUNT);
    throw new RuntimeException("Connection could not be established!");
  }

}
