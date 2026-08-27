package grafiosch.gtnet;

import grafiosch.entities.GTNet;

/**
 * Enqueues the exchange configuration synchronisation after a data exchange has been agreed.
 *
 * <p>
 * The library negotiates the exchange but knows nothing about what is exchanged, so it cannot build the task itself.
 * The seam is an optional bean rather than an overridable handler method: a second handler bean for the same message
 * code would be refused by {@code GTNetMessageHandlerRegistry}, so an application cannot subclass
 * {@code DataRequestHandler} to fill the hook. An application that has something to synchronise publishes one
 * implementation; the library stack publishes none, and the handlers simply do not enqueue anything.
 * </p>
 *
 * <p>
 * Without it a freshly accepted pair has no supplier-detail rows until the next daily run.
 * </p>
 */
public interface IExchangeSyncTrigger {

  /**
   * Schedules a synchronisation with the peer whose data exchange has just been agreed.
   *
   * <p>
   * The implementation must schedule rather than execute: the calling handler is inside the transaction that persists
   * the exchange, and a synchronisation running before that commit would not see it.
   * </p>
   *
   * @param remoteGTNet the peer the exchange was agreed with, may be null when the handler could not resolve it
   */
  void scheduleExchangeSync(GTNet remoteGTNet);
}
