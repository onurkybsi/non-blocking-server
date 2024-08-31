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

final class TestClient {

  static String send(String outgoingMessage) throws IOException {
    try (Socket socket = new Socket("localhost", 8080)) {
      return send(socket, outgoingMessage);
    }
  }

  static List<String> send(List<String> outgoingMessages) throws IOException {
    List<String> responses = new ArrayList<>();

    for (String outgoingMessage : outgoingMessages) {
      try (Socket socket = new Socket("localhost", 8080)) {
        responses.add(send(socket, outgoingMessage));
      }
    }

    return responses;
  }

  static String send(Socket socket, String outgoingMessage) throws IOException {
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

}
