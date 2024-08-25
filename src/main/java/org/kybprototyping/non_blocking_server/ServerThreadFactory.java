package org.kybprototyping.non_blocking_server;

import java.util.concurrent.ThreadFactory;

final class ServerThreadFactory implements ThreadFactory {

  @Override
  public Thread newThread(Runnable r) {
    Thread t = new Thread(r, "server");
    t.setDaemon(false);
    t.setPriority(Thread.MAX_PRIORITY);
    return t;
  }

}
