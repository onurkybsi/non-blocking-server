package org.kybprototyping.non_blocking_server;

final class Main {

  public static void main(String[] args) throws Exception {
    Server server = Server.build(ServerProperties.builder().port(8081).build());
    server.start();
  }

}
