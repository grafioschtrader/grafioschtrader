package grafiosch.gtnet;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * The one clock of the GTNet wire protocol.
 *
 * <p>
 * {@code MessageEnvelope.timestamp} is declared a UTC instant, and the receiver enforces that: it compares the incoming
 * timestamp against {@code LocalDateTime.now(ZoneOffset.UTC)} and refuses anything further away than
 * {@code g.gnet.max.clock.skew.minutes}. A sender that stamps its own zone is therefore refused on any host that is not
 * running in UTC — two hours ahead in Central European summer time is already twenty-four times the default tolerance.
 * </p>
 *
 * <p>
 * The same value is what {@code gt_net_message.timestamp} stores, for a sent message as well as for a received one, so
 * everything that writes or compares that column goes through here. Times that are not part of the protocol keep the
 * local clock: when a task is due, whether a maintenance window is open, when a handshake happened.
 * </p>
 */
public abstract class GTNetTime {

  private GTNetTime() {
  }

  /**
   * The current instant in the zone the protocol is defined in.
   *
   * @return now, as UTC
   */
  public static LocalDateTime now() {
    return LocalDateTime.now(ZoneOffset.UTC);
  }
}
