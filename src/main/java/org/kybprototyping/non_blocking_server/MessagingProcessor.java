package org.kybprototyping.non_blocking_server;

interface MessagingProcessor {

  void handle(ProcessorMessagingContext ctx);

}
