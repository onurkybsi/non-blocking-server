package org.kybprototyping.non_blocking_server;

final class Main {

  public static void main(String[] args) throws Exception {
    Server server = Server.build();
    server.start();
  }

}
