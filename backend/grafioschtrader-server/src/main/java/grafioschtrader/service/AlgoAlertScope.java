package grafioschtrader.service;

import grafioschtrader.entities.AlgoStrategy;
import grafioschtrader.entities.Security;

/**
 * One strategy paired with one instrument it is to be evaluated against, together with the context the pair was reached
 * through.
 *
 * <p>
 * A pair rather than a strategy, because a strategy on an AlgoTop or on an asset class bucket applies to every
 * instrument below it. Crossing state, deduplication and the notification text are all per pair, so the pair is what
 * the evaluation is driven by.
 * </p>
 *
 * @param idTenant    tenant that owns the configuration and receives the notification
 * @param strategy    the configured alert
 * @param security    the instrument it is evaluated against
 * @param contextName what the notification names as the source: the AlgoTop for a hierarchy alert, the instrument
 *                    itself for a standalone one
 * @param active      whether the strategy and every node it hangs below are switched on. An inactive pair is not
 *                    evaluated; its crossing baselines are discarded instead, so that switching it on again cannot
 *                    report a move that happened while it was off.
 */
public record AlgoAlertScope(Integer idTenant, AlgoStrategy strategy, Security security, String contextName,
    boolean active) {
}
