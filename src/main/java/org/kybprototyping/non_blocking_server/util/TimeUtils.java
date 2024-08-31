package org.kybprototyping.non_blocking_server.util;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import lombok.Builder;

/**
 * Utility class for time related classes such as {@link LocalDateTime}, {@link OffsetDateTime} and
 * so on.
 */
@Builder
public final class TimeUtils {

  @Builder.Default
  private final Clock clock = Clock.systemDefaultZone();

  /**
   * Returns the current time as epoch milliseconds.
   * 
   * @return current epoch milliseconds
   */
  public long epochMilli() {
    return clock.instant().toEpochMilli();
  }

}
