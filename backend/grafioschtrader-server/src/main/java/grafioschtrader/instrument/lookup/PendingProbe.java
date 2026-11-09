package grafioschtrader.instrument.lookup;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import grafioschtrader.connector.instrument.IFeedConnector;
import grafioschtrader.dto.ConnectorAlternativeDto;
import grafioschtrader.types.ConnectorProbeStatus;

/**
 * A probe started on a virtual thread; a probe that misses the deadline is reported, not dropped.
 *
 * @param connector    the probed connector
 * @param urlExtension the URL extension that is tried
 * @param future       the running probe
 */
record PendingProbe(IFeedConnector connector, String urlExtension, Future<ConnectorAlternativeDto> future) {

  /**
   * Waits for the probe until the common deadline of the lookup.
   *
   * @param deadlineNanos {@link System#nanoTime()} value at which the lookup gives up
   * @return the outcome of the probe, or a failure for a probe that timed out or failed
   */
  ConnectorAlternativeDto await(long deadlineNanos) {
    try {
      return future.get(Math.max(0, deadlineNanos - System.nanoTime()), TimeUnit.NANOSECONDS);
    } catch (TimeoutException e) {
      future.cancel(true);
      return ConnectorProbe.failed(connector, urlExtension, ConnectorProbeStatus.TIMEOUT,
          "Deadline of the lookup exceeded");
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      future.cancel(true);
      return ConnectorProbe.failed(connector, urlExtension, ConnectorProbeStatus.REQUEST_FAILED, "Interrupted");
    } catch (ExecutionException e) {
      return ConnectorProbe.failed(connector, urlExtension, ConnectorProbeStatus.REQUEST_FAILED,
          String.valueOf(e.getCause()));
    }
  }
}
