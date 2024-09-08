package org.kybprototyping.non_blocking_server;

import static java.util.concurrent.Executors.newSingleThreadExecutor;
import static java.util.concurrent.Executors.newThreadPerTaskExecutor;
import java.io.IOException;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.time.Clock;
import org.kybprototyping.non_blocking_server.handler.IncomingMessageHandler;
import org.kybprototyping.non_blocking_server.handler.MaxIncomingMessageSizeHandler;
import org.kybprototyping.non_blocking_server.handler.TimeoutHandler;
import org.kybprototyping.non_blocking_server.messaging.Formatter;
import org.kybprototyping.non_blocking_server.util.TimeUtils;
import lombok.AccessLevel;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * Builder for {@link Server}.
 * 
 * <p>
 * Note that, this class is not thread safe!
 * </p>
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
  @NonNull
  private final TimeoutHandler timeoutHandler;

  private ServerProperties properties = ServerProperties.builder().build();
  private Clock clock = Clock.systemDefaultZone();

  /**
   * Builds the {@link Server} to run.
   * 
   * @return built {@link Server}
   * @throws IllegalArgumentException if given properties is not valid
   * @throws IOException if the building fails
   */
  public Server build() throws IOException {
    assertPropertiesValid();

    var selector = Selector.open();
    var serverChannel = ServerSocketChannel.open();
    var serverThreadExecutor = newSingleThreadExecutor(ServerThreadFactory.newInstance());
    var timeUtils = TimeUtils.builder().clock(clock).build();
    var userThreadsExecutor =
        newThreadPerTaskExecutor(Thread.ofVirtual().name("executor-", 0).factory());
    var reader = new Reader(properties, formatter, timeUtils, incomingMessageHandler,
        maxIncomingMessageSizeHandler, timeoutHandler, userThreadsExecutor);
    var writer = new Writer(properties, timeUtils, timeoutHandler, userThreadsExecutor);
    return new Server(selector, serverChannel, serverThreadExecutor, properties, reader, writer);
  }

  private void assertPropertiesValid() {
    if (properties.readTimeoutInMs() >= properties.connectionTimeoutInMs()) {
      throw new IllegalArgumentException(
          "properties.readTimeoutInMs cannot be greater than or equal to properties.connectionTimeoutInMs");
    }
  }

}
