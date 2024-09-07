package org.kybprototyping.non_blocking_server;

import java.io.IOException;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.time.Clock;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.kybprototyping.non_blocking_server.handler.IncomingMessageHandler;
import org.kybprototyping.non_blocking_server.handler.MaxIncomingMessageSizeHandler;
import org.kybprototyping.non_blocking_server.messaging.Formatter;
import org.kybprototyping.non_blocking_server.util.TimeUtils;
import lombok.AccessLevel;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * Builder for {@link Server}
 * 
 * @author Onur Kayabasi (o.kayabasi@outlook.com)
 */
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
@Setter
@Accessors(fluent = true, chain = true, makeFinal = true)
public final class ServerBuilder {

  @NonNull
  private final Formatter formatter;
  @NonNull
  private final IncomingMessageHandler incomingMessageHandler;
  @NonNull
  private final MaxIncomingMessageSizeHandler maxIncomingMessageSizeHandler;

  private ServerProperties properties = ServerProperties.builder().build();
  private Clock clock = Clock.systemDefaultZone();

  /**
   * Builds the {@link Server} to run.
   * 
   * @return built {@link Server}
   * @throws IOException if the building fails
   */
  public Server build() throws IOException {
    Selector selector = Selector.open();
    ServerSocketChannel serverChannel = ServerSocketChannel.open();
    ExecutorService executorService = Executors.newSingleThreadExecutor(new ServerThreadFactory());
    TimeUtils timeUtils = TimeUtils.builder().clock(clock).build();
    var reader = new Reader(properties, formatter, timeUtils, incomingMessageHandler,
        maxIncomingMessageSizeHandler);
    var writer = new Writer(properties, timeUtils);
    return new Server(selector, serverChannel, executorService, properties, reader, writer);
  }

}
