package org.kybprototyping.non_blocking_server;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.function.Consumer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
final class SelectedKeyAction implements Consumer<SelectionKey> {

  private static final Logger logger = LogManager.getLogger(SelectedKeyAction.class);

  private final ServerProperties properties;
  private final Selector selector;
  private final Reader reader;
  private final Writer writer;

  @Override
  public void accept(SelectionKey selectedKey) {
    try {
      if (selectedKey.isAcceptable()) {
        acceptConnection(selectedKey);
      } else if (selectedKey.isReadable()) {
        reader.read(selectedKey);
      } else if (selectedKey.isWritable()) {
        writer.write(selectedKey);
      } else {
        logger.warn("Unexpected key selected, it's being cancelled: {}", selectedKey);
        selectedKey.cancel();
      }
    } catch (Exception e) {
      logger.error("Something went wrong during processing selectedKey: {}", selectedKey, e);
    }
  }

  private void acceptConnection(SelectionKey selectedKey) throws IOException {
    ServerSocketChannel serverChannel = (ServerSocketChannel) selectedKey.channel();

    SocketChannel connection = serverChannel.accept();
    if (connection != null) {
      connection.configureBlocking(false);
      connection.register(selector, SelectionKey.OP_READ, serverMessagingContext());
      logger.debug("Connection accepted: {}", connection);
    }
  }

  private ServerMessagingContext serverMessagingContext() {
    return ServerMessagingContext.of(ByteBuffer.allocate(properties.minBufferSizeInBytes()));
  }

}
