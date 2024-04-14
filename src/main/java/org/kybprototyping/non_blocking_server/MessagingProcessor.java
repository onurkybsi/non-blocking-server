package org.kybprototyping.non_blocking_server;

interface MessagingProcessor {

  void process(ProcessorMessagingContext ctx);

}
